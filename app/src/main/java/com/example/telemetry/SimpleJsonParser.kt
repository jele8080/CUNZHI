package com.example.telemetry

/**
 * Lightweight, strict RFC 8259 JSON parser in pure Kotlin.
 * Preserves exact integer (Long) vs floating-point (Double) vs String vs Boolean vs null distinction
 * without relying on Android framework stubs.
 */
object SimpleJsonParser {

    fun parseObject(input: String): Map<String, Any?>? {
        val p = Parser(input)
        p.skipWs()
        if (p.peek() != '{') return null
        val obj = p.readObject() ?: return null
        p.skipWs()
        return if (p.isAtEnd()) obj else null
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= s.length
        fun peek(): Char? = if (pos < s.length) s[pos] else null

        fun skipWs() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\t' || s[pos] == '\r' || s[pos] == '\n')) {
                pos++
            }
        }

        fun readObject(): Map<String, Any?>? {
            if (peek() != '{') return null
            pos++ // '{'
            skipWs()
            val result = LinkedHashMap<String, Any?>()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWs()
                val key = readString() ?: return null
                skipWs()
                if (peek() != ':') return null
                pos++
                skipWs()
                val value = readValue()
                if (value === INVALID) return null
                result[key] = value
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> return null
                }
            }
        }

        private fun readArray(): List<Any?>? {
            if (peek() != '[') return null
            pos++
            skipWs()
            val list = ArrayList<Any?>()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWs()
                val v = readValue()
                if (v === INVALID) return null
                list.add(v)
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> return null
                }
            }
        }

        private fun readValue(): Any? {
            return when (val ch = peek()) {
                null -> INVALID
                '"' -> readString() ?: INVALID
                '{' -> readObject() ?: INVALID
                '[' -> readArray() ?: INVALID
                't' -> if (consumeLiteral("true")) true else INVALID
                'f' -> if (consumeLiteral("false")) false else INVALID
                'n' -> if (consumeLiteral("null")) null else INVALID
                '-', in '0'..'9' -> readNumber()
                else -> INVALID
            }
        }

        private fun consumeLiteral(lit: String): Boolean {
            if (s.regionMatches(pos, lit, 0, lit.length)) {
                pos += lit.length
                return true
            }
            return false
        }

        private fun readString(): String? {
            if (peek() != '"') return null
            pos++
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= s.length) return null
                        when (val esc = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) return null
                                val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> return null
                        }
                    }
                    c.code < 0x20 -> return null
                    else -> sb.append(c)
                }
            }
            return null
        }

        private fun readNumber(): Any {
            val start = pos
            var isFloat = false
            if (peek() == '-') pos++
            if (isAtEnd()) return INVALID
            if (s[pos] == '0') {
                pos++
            } else if (s[pos] in '1'..'9') {
                while (pos < s.length && s[pos] in '0'..'9') pos++
            } else {
                return INVALID
            }
            if (pos < s.length && s[pos] == '.') {
                isFloat = true
                pos++
                if (pos >= s.length || s[pos] !in '0'..'9') return INVALID
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                if (pos >= s.length || s[pos] !in '0'..'9') return INVALID
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            val token = s.substring(start, pos)
            return if (isFloat) {
                token.toDoubleOrNull()?.takeIf { it.isFinite() } ?: INVALID
            } else {
                token.toLongOrNull() ?: token.toDoubleOrNull()?.takeIf { it.isFinite() } ?: INVALID
            }
        }
    }

    private val INVALID = Any()
}
