# CUNZHI MQTT 监看工具 · 编写说明

电脑端用 Node.js 运行 MQTT broker，浏览器只打开本机监看页。设备列表按 MQTT 主题里的 MAC 区分。页面显示两路压力、电量、计数、趋势、最近上报时间和设备配置。

## 安装

需要 Node.js 20 或更新版本。在 PowerShell 进入本目录后安装锁定版本的依赖。

```powershell
npm ci
npm test
```

## 使用已有 broker

这台电脑已有程序占用 1883 时，用此模式可以直接接收数据。

```powershell
$env:MQTT_URL = 'mqtt://127.0.0.1:1883'
npm start
```

浏览器打开 `http://127.0.0.1:8787`。此模式只订阅已有 broker 的 `/dpub/+`，不会争用 1883。

## 使用内置 broker

先结束旧 broker 和旧的 `easysmart.local` 宣告程序，再用本工具接管。CUNZHI01 和电脑应在同一局域网。将示例 IP 换成电脑的实际 Wi‑Fi IPv4。

```powershell
Remove-Item Env:MQTT_URL -ErrorAction SilentlyContinue
$env:MDNS_IP = '192.168.10.56'
npm start
```

本工具在这个 IP 上监听 MQTT TCP 1883，并通过 UDP 5353 回应 `easysmart.local` 的 IPv4 查询。必要时让 CUNZHI01 重新连接 Wi‑Fi，以便它重新发现 broker。如果电脑和设备无法互通，检查 Windows 专用网络防火墙的 TCP 1883 与 UDP 5353 入站规则，以及路由器是否开启了客户端隔离。监看页始终监听本机回环地址。内置 broker 面向受信任的局域网，接受设备的遥测上报。

| 环境变量 | 用途 |
| --- | --- |
| `MQTT_URL` | 订阅既有 broker 的地址。留空时启动内置 broker。 |
| `MDNS_IP` | 电脑的局域网 IPv4。设定后回应 `easysmart.local`。内置模式也优先用这个地址监听。 |
| `MQTT_HOST` | 可选的内置 broker 监听地址。未设置 `MDNS_IP` 时默认监听 `0.0.0.0`。 |
| `MQTT_PORT` | 内置 broker 的端口，默认 1883。固件通过 mDNS 发现时使用 1883。 |
| `WEB_PORT` | 本机页面端口，默认 8787。 |

按 Ctrl+C 停止。设备数据和图表样本保存在内存中，重启工具后由设备重新上报。

## 代码结构与协议

| 文件 | 职责 |
| --- | --- |
| `server.js` | 启动 Aedes 或连接已有 MQTT，处理 mDNS，服务静态页面和只读 `GET /api/state`。 |
| `telemetry.js` | 校验主题、JSON 和数据类型，合并属性，维护最近 180 个压力采样点。 |
| `web/` | 设备选择、实时数值、趋势图和只读属性表。页面每约 100 ms 拉取一次状态。 |
| `telemetry.test.js` | 验证完整状态与增量合并、单项回复、超时判断及无效输入。 |

- 完整状态 `method: "report"` 包含 `ver` 和 24 个设备属性。固件的心跳任务约每 10 秒发送一次。
- 周期数据 `method: "update"` 使用平铺字段 `pressure`、`pressure1`、`battery`、`game_cz_count`。默认周期 5000 ms，实际取决于设备当前配置。
- 单项查询的回复也叫 `update`，但字段在 `key` 和 `value` 中。解析后只更新相应属性。工具本身只读，内置 broker 只允许向设备上行遥测主题发布。
- 压力显示为原始读数。近期上报超时以 15 秒和设备上报周期三倍中的较大值判断。

固件对照见 [CUNZHI01 上报任务](../repos/hardware/components/base_device/CUNZHI01.c)、[通用属性处理](../repos/hardware/components/base_device/base_device.c)与 [MQTT 连接实现](../repos/hardware/components/smqtt/smqtt.c)。内置 broker 使用 [Aedes](https://github.com/moscajs/aedes)。

## APK 服务端对接规格

### 职责和网络拓扑

1. CUNZHI01 是 MQTT 客户端。它连到局域网里的 broker，主动发送遥测。
2. APK 的服务端如果负责收集设备数据，可以作为另一个 MQTT 客户端，连接同一个 broker，订阅 `/dpub/+`。也可以由该服务端自己的进程内 broker 直接接收设备消息。二选一即可。
3. 当前电脑监看工具演示了这两种接法。它自带的 HTTP 接口只绑定 `127.0.0.1`，同一电脑上的服务可以访问。局域网里的手机不能直接访问这个回环接口。APK 和自建服务端之间的 API、鉴权和推送由 APK 项目定义。

### 连接与发现

| 项目 | 约定 |
| --- | --- |
| 设备侧连接 | MQTT TCP，默认端口 `1883` |
| 首选地址 | `mqtt://easysmart.local`。在承载 broker 的主机上，用 mDNS 回应局域网 IPv4 A 记录。 |
| 备用地址 | CUNZHI01 当前 Wi‑Fi 的默认网关 IP，端口仍为 1883。 |
| 上行 | `/dpub/<mac>`，真实设备例子为 `/dpub/90649b4ed93c`。订阅所有设备用 `/dpub/+`。 |
| 下行 | 设备订阅 `/drecv/<mac>` 和 `/all`。本监看软件只消费上行。 |
| 上行传输 | JSON，UTF‑8，QoS 1，`retain=false`。QoS 1 允许重复交付，接入端应接受重复快照。 |

用主题里的 12 位十六进制 MAC 作为键，并统一为小写。周期消息中没有 MAC 字段，不要从 JSON 推断设备身份。多台设备可共用一个 broker。一个局域网只给 `easysmart.local` 指定一个 broker 地址，避免发现结果冲突。由于设备上报不保留，订阅成功后要等下一条消息才能初始化数据。

### 三种上行格式

**完整状态**携带 `method: "report"`、固件版本 `ver` 和下表的全部 24 项。设备进入就绪状态后，源码使用约 10 秒的心跳周期。这里是缩略示例。

```json
{"method":"report","ver":"v1.1.41","device_type":"CUNZHI01","sleep_time":7200,"battery":85,"report_delay_ms":5000}
```

**周期遥测**携带四个平铺字段。压力每 100 ms 采样一次，发布由 `report_delay_ms` 控制。因此默认 5000 ms 时，订阅端通常每 5 秒才见到一帧压力。

```json
{"method":"update","pressure":85.7,"pressure1":12.5,"battery":85,"game_cz_count":0}
```

**单项查询回复**也用 `update`，但携带 `key` 和 `value`。如果调用方提供了非负整数 `msg_id`，固件会将它回传。以下请求由可选的上位机查询功能发往单设备下行主题，回复仍在原上行主题。本监看程序无需发送该请求也能工作。

```json
{"method":"get","key":"report_delay_ms","msg_id":1}
```

```json
{"method":"update","key":"report_delay_ms","value":5000,"msg_id":1}
```

### 完整字段表

“可写”是固件属性声明。APK 的第一版监看服务可以把整张表作为只读状态展示。

| 字段 | 类型 | 含义和源码默认值 |
| --- | --- | --- |
| `ver` | 字符串 | 固件版本，完整上报附带。截图实例为 `v1.1.41`。 |
| `device_type` | 字符串 | 只读，`CUNZHI01`。 |
| `sleep_time` | 整数 | 可写，休眠前允许无有效入站消息的时间，默认 7200 秒。 |
| `battery` | 整数 | 只读，电池百分比，运行时更新。 |
| `power` | 整数 | 可写，电机状态字段，默认 0。 |
| `voltage` | 整数 | 可写，电刺激相关状态字段。 |
| `shock` | 整数 | 可写，电刺激相关状态字段。 |
| `delay` | 整数 | 可写，电刺激相关时间字段。 |
| `safe` | 整数 | 可写，电刺激相关状态字段，从 NVS 恢复的值可与初始值不同。 |
| `pressure` | 浮点数 | 只读，第一路压力原始值，默认 0。 |
| `pressure1` | 浮点数 | 只读，第二路压力原始值，默认 0。 |
| `report_delay_ms` | 整数 | 可写，周期遥测间隔，默认 5000 ms，属性声明范围 100–10000 ms。 |
| `game_mode` | 整数 | 可写，设备内置模式，默认 0。 |
| `game_duration` | 整数 | 可写，模式总时长，默认 0，单位秒。 |
| `game_fly_dur` | 整数 | 可写，模式阶段时长，默认 60 秒。 |
| `game_e_vol` | 整数 | 可写，模式内电刺激相关字段。 |
| `game_e_dur` | 整数 | 可写，模式内电刺激相关时间字段。 |
| `game_p1_thresh` | 浮点数 | 可写，第一路阈值，默认 0，与原始压力同量纲。 |
| `game_p2_thresh` | 浮点数 | 可写，第二路阈值，默认 0，与原始压力同量纲。 |
| `game_m_dur` | 整数 | 可写，模式内电机时长，默认 0，单位毫秒。 |
| `game_m_power` | 整数 | 可写，模式内电机强度字段，默认 0。 |
| `game_m_step` | 整数 | 可写，模式内电机强度步进，默认 0。 |
| `game_cooldown` | 整数 | 可写，模式冷却时长，默认 0，单位毫秒。 |
| `game_kegel_t` | 整数 | 可写，模式内计时参数，默认 0，单位毫秒。 |
| `game_cz_count` | 整数 | 可写，计数，默认 0，会随周期遥测发送。 |

`pressure` 与 `pressure1` 的源码计算范围为 0–800。原始数值没有物理 kPa 标定。安静时的 0 是有效读数，缺失字段应保持未知或沿用上一次值。

### 服务端状态与 API 最小约定

每台设备维护一份按 MAC 索引的最新字段集合。对 `report`、平铺 `update`、单项回复逐项合并，未知字段可在日志里保留，展示模型只接受认可的属性。接收端给每条有效消息打上本地时间。固件上报中没有采样时间戳和应用层流水号。历史曲线只用周期压力帧，避免把每 10 秒的完整上报重复当成压力采样。

本工具的只读接口 `GET /api/state` 可以作为应用数据层参考。下面是简化的返回示例，`lastSeen` 和 `at` 都是 Unix 毫秒。任一路在某次采样里缺失时，该采样的对应通道是 `null`。

```json
{
  "mode": "external",
  "mqttReady": true,
  "brokerPort": null,
  "discoveryIp": null,
  "devices": [
    {
      "mac": "90649b4ed93c",
      "fields": {"device_type":"CUNZHI01","ver":"v1.1.41","pressure":85.7,"pressure1":12.5,"battery":85},
      "lastSeen": 1760000000000,
      "online": true,
      "history": [{"at":1760000000000,"first":85.7,"second":12.5}]
    }
  ]
}
```

`mqttReady` 表示服务端已开始监听或已订阅 broker。设备在线状态按最后一条有效消息判定，不能只根据 broker 在线来判定设备在线。订阅端每次 MQTT 重连后要重新订阅。本工具在内存中保留每台设备最近 180 个点，APK 服务端若需要长时间历史，可自行存储并定义保留期限。

### 联调验收清单

1. 先用独立测试 broker，向 `/dpub/001122334455` 投递上面的完整状态，再投递周期遥测。确认版本保留、两路压力同时刷新、缺失值不变成 0。
2. 再投递单项回复，确认只更新指定属性。无效 JSON、未知方法和不符合主题格式的消息应忽略。
3. 真机上线时检查实际 MAC 和 `device_type`，等待完整状态与周期遥测都到达。分别改变两路压力，确认两条曲线没有接反。
4. 断开设备或 broker 后确认状态能超时。恢复连接后确认新的数据继续进入，避免订阅丢失。
5. 需要排查延迟时分开记录 broker 收到消息和 APK 收到数据的时间。网页轮询更快只能降低显示等待，无法提高传感器实际采样或 MQTT 发布频率。
