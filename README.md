# CUNZHI01 手机端（Android APK）监看与控制工具 · 编写说明

本文档对应 **「寸止监看」（Android APK）** 的完整实现规范，在兼容电脑端 CUNZHI01 MQTT 遥测协议的基础上，增加了手机端双模式接入、全属性可写下发、电机与电刺激安全测试台以及小米（HyperOS / MIUI）后台保活优化。

---

## 1. 运行环境与基础配置

| 项目 | 约定 |
| --- | --- |
| 应用名称 | 寸止监看（工程名 `CunzhiMonitor`） |
| Application ID | `com.aistudio.cunzhimonitor.kxqvtr` |
| 技术栈 | Kotlin + Jetpack Compose（Material 3）+ Coroutines / Flow + DataStore Preferences |
| 最低 / 目标版本 | `minSdk 24`（Android 7.0） / `targetSdk 36` |
| 通信实现 | 纯 Kotlin MQTT 3.1.1 编解码器（内置 TCP Broker + 外部 Broker 客户端）与 RFC 6762 mDNS 响应器，无外部 C/JNI 依赖 |
| 数据持久化 | 连接配置、下行格式、保活选项使用 DataStore 持久化；设备实时遥测与 180 点历史曲线存于内存，重启后由设备重新上报恢复 |

---

## 2. 两种运行模式与局域网发现

手机与 CUNZHI01 设备处于同一局域网（或手机开启热点供设备接入）即可使用，应用提供两种模式二选一：

### 模式 A：内置 Broker（默认）
- 手机端直接监听局域网 TCP `1883`（端口可自定义），接收 CUNZHI01 主动连接与遥测上报，同时支持向已订阅 `/drecv/<mac>` 的设备直接推送 QoS 1 下行控制指令。
- 开启 mDNS 广播后，应用自动获取当前 Wi-Fi / 热点 IPv4（也支持手动指定 IP），持有 `WifiManager.MulticastLock` 并监听 UDP `5353`，对 `easysmart.local` 的 A 记录查询返回手机 IPv4。
- 同一局域网内应仅保留一个宣告 `easysmart.local` 的 Broker，避免设备发现冲突；如切换 Broker，需让 CUNZHI01 重连 Wi-Fi 以重新解析。

### 模式 B：连接已有 Broker
- 当局域网或电脑端已有独立 MQTT Broker（例如电脑端工具、Mosquitto、EMQX）运行时使用。
- 填写目标地址（支持 `mqtt://host:port`、`host:port` 或纯主机名/IP，默认端口 `1883`）及可选用户名/密码。
- 连接成功后自动订阅 `/dpub/+`（QoS 1），断线自动指数退避重连，**每次重连后强制重新订阅 `/dpub/+`**，防止订阅丢失；下发指令直接发布至该 Broker 的 `/drecv/<mac>`。

---

## 3. MQTT 通信协议规格

### 3.1 主题与传输约定

| 项目 | 约定 |
| --- | --- |
| 传输层 | MQTT 3.1.1 over TCP，默认端口 `1883` |
| 首选发现地址 | `mqtt://easysmart.local`（mDNS IPv4 A 记录）；备用为网关 IP:1883 |
| 上行主题 | `/dpub/<mac>`（12 位十六进制 MAC，应用内部统一转小写作为设备主键），批量订阅 `/dpub/+` |
| 下行主题 | `/drecv/<mac>`（单设备控制与查询），设备另订阅 `/all` |
| 消息格式 | JSON，UTF-8，QoS 1，`retain=false`（允许重复交付，幂等覆盖最新字段） |

> 周期上报 JSON 中不包含 MAC 字段，设备身份**严格从主题 `/dpub/<mac>` 提取**，不从 JSON 推断。

### 3.2 三种上行消息（设备 → 手机）

1. **完整状态（`method: "report"`）**：约每 10 秒发送一次，包含 `ver` 及 24 项设备属性。只合并属性，**不写入压力历史曲线**，避免重复采样。
   ```json
   {"method":"report","ver":"v1.1.41","device_type":"CUNZHI01","sleep_time":7200,"battery":85,"report_delay_ms":5000}
   ```
2. **周期遥测（`method: "update"` 平铺格式）**：按 `report_delay_ms`（默认 5000 ms）发送 `pressure`、`pressure1`、`battery`、`game_cz_count`。**仅此类消息追加压力历史采样点**（每台设备保留最近 180 个点）。
   ```json
   {"method":"update","pressure":85.7,"pressure1":12.5,"battery":85,"game_cz_count":0}
   ```
3. **单项查询回复（`method: "update"` 含 `key`/`value`）**：响应 `get` 查询，仅更新对应 `key` 字段，不写入历史曲线；若有 `msg_id` 会原样回传。
   ```json
   {"method":"update","key":"report_delay_ms","value":5000,"msg_id":1}
   ```

### 3.3 下行查询与写入消息（手机 → 设备，发往 `/drecv/<mac>`）

- **单项查询（`get`）**：
  ```json
  {"method":"get","key":"report_delay_ms","msg_id":1}
  ```
- **属性写入（`set`）**（支持在「控制 / 连接」页切换两种固件兼容格式）：
  - 标准 `key/value` 格式（默认）：
    ```json
    {"method":"set","key":"power","value":50,"msg_id":2}
    ```
  - 平铺字段格式（兼容部分直接按字段名解析的固件）：
    ```json
    {"method":"set","power":50,"msg_id":3}
    ```
- 每次写入成功后默认延迟约 `220 ms` 自动发一次对应字段的 `get` 进行回读确认。

### 3.4 状态合并、空值与在线判定规则

- **原始压力读数**：`pressure` / `pressure1` 范围 `0–800`（无 kPa 标定）。`0` 是有效安静读数，缺失字段保持 `null`（UI 显示 `—`），历史采样点中缺失通道记录为 `null`（趋势图自动断线，不伪造为 0）。
- **在线超时判定**：`timeoutMs = max(15000, report_delay_ms * 3)`。在线状态严格以最后一条合法上行消息的本地接收时间判定，而非 Broker 连接状态。
- **异常输入过滤**：非 `/dpub/<12位hex>` 主题、非法 JSON、未知 `method` 一律忽略并记入「忽略/异常」日志；未知字段在日志保留，展示模型只接纳协议表内属性。
- **双时间戳延迟追踪**：同时记录 Broker 收到时间与引擎合入时间，便于排查局域网与端侧延迟。

---

## 4. 完整属性字段表（25 项）

| 字段 | 类型 | 权限 | 约束 / 范围 | 含义与默认值 |
| --- | --- | --- | --- | --- |
| `ver` | String | 只读 | — | 固件版本（如 `v1.1.41`），完整上报附带 |
| `device_type` | String | 只读 | — | 设备型号，固定 `CUNZHI01` |
| `battery` | Int | 只读 | `0–100%` | 电池百分比，运行时更新 |
| `pressure` | Float | 只读 | `0–800` | 第一路压力原始值，默认 `0` |
| `pressure1` | Float | 只读 | `0–800` | 第二路压力原始值，默认 `0` |
| `sleep_time` | Int | **可写** | `≥ 0` s | 无有效入站消息后进入休眠的时长，默认 `7200` s |
| `report_delay_ms` | Int | **可写** | `100–10000` ms | 周期遥测上报间隔，默认 `5000` ms（联动在线超时阈值） |
| `power` | Int | **可写** | `≥ 0`（测试台 `0–100`） | 电机输出强度，默认 `0` |
| `safe` | Int | **可写** | **`0` 或 `1`** | **电刺激电压上限档位：`safe=1` 上限 `36V`，`safe=0` 上限 `75V`**（可从 NVS 恢复；未知时默认按 `36V` 安全限压） |
| `voltage` | Int | **可写** | **`0–36V` 或 `0–75V`** | **电刺激电压（V），上限实时受 `safe` 约束** |
| `shock` | Int | **可写** | **`0` 或 `1`** | **电刺激触发开关，仅支持 `0`（关）或 `1`（开）** |
| `delay` | Int | **可写** | **`20–1000` ms** | **电刺激延时/时间参数（毫秒）** |
| `game_mode` | Int | **可写** | `≥ 0` | 设备内置模式编号，默认 `0` |
| `game_duration` | Int | **可写** | `≥ 0` s | 模式总时长（秒），默认 `0` |
| `game_fly_dur` | Int | **可写** | `≥ 0` s | 模式阶段时长（秒），默认 `60` s |
| `game_e_vol` | Int | **可写** | `0–36` / `0–75` | 模式内电刺激强度（受当前 `safe` 电压上限约束） |
| `game_e_dur` | Int | **可写** | `≥ 0` | 模式内电刺激时长 |
| `game_p1_thresh` | Float | **可写** | `0–800` | 第一路压力阈值，与原始压力同量纲，默认 `0` |
| `game_p2_thresh` | Float | **可写** | `0–800` | 第二路压力阈值，与原始压力同量纲，默认 `0` |
| `game_m_dur` | Int | **可写** | `≥ 0` ms | 模式内电机时长（毫秒），默认 `0` |
| `game_m_power` | Int | **可写** | `≥ 0` | 模式内电机强度，默认 `0` |
| `game_m_step` | Int | **可写** | `≥ 0` | 电机强度步进，默认 `0` |
| `game_cooldown` | Int | **可写** | `≥ 0` ms | 模式冷却时长（毫秒），默认 `0` |
| `game_kegel_t` | Int | **可写** | `≥ 0` ms | 模式内计时参数（毫秒），默认 `0` |
| `game_cz_count` | Int | **可写** | `≥ 0` | 计数，默认 `0`，随周期遥测上报 |

---

## 5. 页面功能与硬件测试安全设计

应用包含 4 个主标签页（宽屏/平板自动切换侧边 `NavigationRail`）：

1. **「监看」页**：
   - 多设备按 MAC 切换、在线/超时状态标签、距上次上报秒数与端到端延迟显示。
   - 双通道实时压力读数卡片（支持阈值参照线）、电量、计数、`ver` 信息。
   - 180 点双通道 Canvas 趋势曲线（支持 `0–800` 固定量程 / 自动缩放切换、按住拖动查看任意采样点时间与两路数值）。
   - 分类实时协议日志（遥测 / 下发 / 系统 / mDNS / 忽略与异常）。
2. **「控制」页**：
   - **顶部全局急停按钮**：随时一键取消进行中的定时脉冲任务，并连续下发 `power=0`、`shock=0`、`voltage=0`。
   - **电机测试模块（`power`）**：`0–100` 滑块、数值输入、快捷档位（`0/20/40/60/80/100`）、直接持续下发/停止，以及定时脉冲测试（`0.5s / 1s / 2s / 3s` 倒计时进度条，到时自动发 `power=0`）。
   - **电刺激测试模块（`safe` / `voltage` / `delay` / `shock`）**：
     - 默认处于**安全锁定态**，需手动开启右侧安全锁才展开写入控件；「立即停止电刺激（`shock=0, voltage=0V`）」按钮始终置顶可用。
     - `safe` 分段切换（`safe=1 · 36V` / `safe=0 · 75V`），切回 `safe=1` 时自动钳位高于 `36V` 的待下发电压。
     - `voltage` 滑块与快捷档位随 `safe` 实时切换上限（`0–36V` 或 `0–75V`）。
     - `delay` 严格约束为 `20–1000 ms`（滑块 + 输入框 + `20/100/200/500/1000 ms` 快捷档）。
     - `shock` 仅提供 `0（关）` / `1（开）` 分段切换。
     - 组合点动测试：按序下发 `voltage` → `delay` → `shock=1`，并支持 `300ms / 500ms / 1s` 后自动归零 `shock=0`。
3. **「属性」页**：
   - 分组展示全部 25 个协议字段（基本与心跳 / 压力与阈值 / 电机与电刺激 / 模式与计时），支持关键词搜索。
   - 每个字段支持单项 `get` 刷新；全部 20 个可写字段均支持弹窗编辑并内置对应参数范围校验与快捷预设。
4. **「连接」页**：
   - 模式切换（内置 Broker / 已有 Broker）、端口、mDNS 开关与本机 IPv4 探测、下行 `set` 格式选择、防熄屏与服务启停控制。
   - 内置 **小米后台保活优化面板**。

---

## 6. 小米手机（HyperOS / MIUI）后台服务专项优化

针对小米系统激进的省电策略（PowerKeeper 冻结网络、息屏断 Wi-Fi、清理后台进程），应用采用合规无敏感权限方案：

1. **前台服务（`MqttMonitorService`）**：
   - 声明 `foregroundServiceType="dataSync"`、`stopWithTask="false"`，使用 `START_STICKY` 策略，服务被系统回收后自动从 DataStore 恢复上次连接配置重启。
   - 常驻通知实时刷新当前模式、端口/地址、在线设备数与连接客户端数；适配 Android 14+ `onTimeout` 回调。
2. **双锁防休眠与低延迟 Wi-Fi**：
   - 可开关持有 `PowerManager.PARTIAL_WAKE_LOCK`（防止息屏后 CPU 休眠导致内置 Broker 停止响应心跳）与 `WifiManager.WifiLock`（Android 10+ 优先 `WIFI_MODE_FULL_LOW_LATENCY`，防止息屏进入高延迟省电模式）。
3. **网络切换自动自愈**：
   - 注册 `ConnectivityManager.NetworkCallback`，检测到 Wi-Fi 重连、IP 变化或网络恢复时，自动重建 mDNS 监听与 Broker / 客户端连接。
4. **小米设置一键直达向导（实时体检）**：
   - 自动检测电池优化白名单状态、通知权限与当前锁状态，提供零敏感权限的直达入口：
     - 小米「省电策略 → 无限制」（直达 `PowerKeeper`，自动降级至应用详情页）
     - 小米「自启动管理」
     - 通知渠道权限设置（确保常驻通知不被折叠隐藏）
     - 最近任务页下拉「加锁」操作提示。

---

## 7. 权限清单（遵循最小权限原则）

- `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`：局域网 TCP 通信与本机 IP 检测（无需定位权限）。
- `CHANGE_WIFI_MULTICAST_STATE`：UDP `5353` mDNS 组播接收与响应。
- `WAKE_LOCK`：息屏保持 MQTT 监听与 Wi-Fi 低延迟连接。
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`：后台持续运行监看与 Broker 服务。
- `POST_NOTIFICATIONS`（Android 13+ 运行时申请）：展示前台保活通知。

---

## 8. 代码模块划分

| 模块路径 | 职责 |
| --- | --- |
| `app/src/main/java/com/example/model/TelemetryModels.kt` | 25 项协议 Schema、电压/延时安全常量、设备快照与配置模型 |
| `app/src/main/java/com/example/mqtt/MqttPacket.kt` | 纯 Kotlin MQTT 3.1.1 报文编解码与主题通配符匹配 |
| `app/src/main/java/com/example/mqtt/EmbeddedMqttBroker.kt` | 内置 TCP MQTT Broker（QoS 1 `PUBACK`、上行分发、下行 `/drecv/<mac>` 推送） |
| `app/src/main/java/com/example/mqtt/ExternalMqttClient.kt` | 外部 Broker 客户端（自动重连、重连自动重新订阅 `/dpub/+`、线程安全发布） |
| `app/src/main/java/com/example/mqtt/MdnsResponder.kt` | RFC 6762 mDNS 编解码与 `easysmart.local` IPv4 A 记录响应 |
| `app/src/main/java/com/example/mqtt/NetworkUtils.kt` | 局域网 Wi-Fi / 热点 IPv4 检测 |
| `app/src/main/java/com/example/telemetry/SimpleJsonParser.kt` | 纯 Kotlin RFC 8259 JSON 解析器（便于本地 JVM 单测） |
| `app/src/main/java/com/example/telemetry/TelemetryEngine.kt` | 上行校验、属性合并、180 点历史维护、在线超时计算与日志记录 |
| `app/src/main/java/com/example/telemetry/DownlinkCommandBuilder.kt` | `/drecv/<mac>` 下行 `get` / `set`（`key/value` 与平铺格式）构造器 |
| `app/src/main/java/com/example/data/ConfigStore.kt` | DataStore 配置持久化 |
| `app/src/main/java/com/example/data/MonitorRepository.kt` | 网络自愈协调、上下行读写与急停统一调度 |
| `app/src/main/java/com/example/service/MqttMonitorService.kt` | `dataSync` 前台服务与 WakeLock / WifiLock 管理 |
| `app/src/main/java/com/example/service/XiaomiBackgroundHelper.kt` | 小米后台保活体检与系统设置一键跳转 |
| `app/src/main/java/com/example/ui/MonitorViewModel.kt` | 状态聚合、参数安全钳位与定时脉冲任务编排 |
| `app/src/main/java/com/example/ui/screens/*` | 监看、控制、属性、连接 4 个 Jetpack Compose 页面 |
| `app/src/test/java/com/example/TelemetryAndMqttTest.kt` | JVM 单元测试（合并规则、超时、180 点上限、MQTT/mDNS 编解码、Broker 环回上下行、`safe/voltage/delay` 约束） |

---

## 9. 联调验收清单

1. **上行合并与空值校验**：向 `/dpub/001122334455` 先发 `report` 再发 `update`，确认 `ver` 保留、两路压力刷新、`0` 正常显示、缺失字段保持 `—` 而非 `0`。
2. **单项回复与容错**：发送 `{"method":"update","key":"report_delay_ms","value":8000,"msg_id":1}`，确认仅更新对应字段且超时阈值同步变为 `24s`；非法 JSON / 未知 `method` / 错误主题被忽略并记入日志。
3. **可写控制与安全边界**：
   - 在「控制」页测试电机 `power` 持续输出、定时脉冲自动归零及一键急停。
   - 测试电刺激 `safe=1`（电压最高 `36V`）与 `safe=0`（最高 `75V`）联动，确认切回 `safe=1` 时自动钳位；确认 `shock` 仅允许 `0/1`、`delay` 限制在 `20–1000 ms`。
4. **断线与后台恢复**：在小米真机配置「省电无限制 + 自启动 + 后台加锁」后锁屏 10 分钟或切换一次 Wi-Fi，确认前台服务存活、重连后数据与下发控制持续正常。
