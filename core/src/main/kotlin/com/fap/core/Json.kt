package com.fap.core

/**
 * The smallest JSON reader that can handle the site's ajax responses
 * (`/search_v2/?ajax=1…`, `/ajax/tags/suggest/…`).
 *
 * The core module is zero-dependency on purpose, so instead of pulling in a
 * JSON library for two endpoints this parses into plain Kotlin values:
 * `Map<String, Any?>`, `List<Any?>`, `String`, `Double`, `Boolean`, `null`.
 * Anything malformed yields `null` rather than throwing.
 */
public object JsonParser {

    public fun parse(text: String): Any? {
        val parser = Parser(text)
        return try {
            parser.skipWhitespace()
            val value = parser.parseValue()
            parser.skipWhitespace()
            if (parser.atEnd()) value else null
        } catch (e: Exception) {
            null
        }
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("unexpected end of json")
            return when (text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                out[key] = parseValue()
                skipWhitespace()
                when (next()) {
                    '}' -> return out
                    ',' -> continue
                    else -> throw IllegalArgumentException("expected , or } in object")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return out
            }
            while (true) {
                out.add(parseValue())
                skipWhitespace()
                when (next()) {
                    ']' -> return out
                    ',' -> continue
                    else -> throw IllegalArgumentException("expected , or ] in array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (pos >= text.length) break
                        when (val escaped = text[pos++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw IllegalArgumentException("bad unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                out.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalArgumentException("bad escape: $escaped")
                        }
                    }
                    else -> out.append(c)
                }
            }
            throw IllegalArgumentException("unterminated string")
        }

        private fun parseNumber(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            if (start == pos) throw IllegalArgumentException("expected a value at $pos")
            return text.substring(start, pos).toDouble()
        }

        private fun parseBoolean(): Boolean {
            return if (text.startsWith("true", pos)) {
                pos += 4
                true
            } else if (text.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("bad literal at $pos")
            }
        }

        private fun parseNull(): Any? {
            if (!text.startsWith("null", pos)) throw IllegalArgumentException("bad literal at $pos")
            pos += 4
            return null
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else ' '

        private fun next(): Char {
            if (pos >= text.length) throw IllegalArgumentException("unexpected end of json")
            return text[pos++]
        }

        private fun expect(c: Char) {
            skipWhitespace()
            if (next() != c) throw IllegalArgumentException("expected $c at $pos")
        }
    }
}

/** Typed accessors over [JsonParser] output, tolerant of the site's mixed types. */
internal fun Any?.asJsonObject(): Map<*, *>? = this as? Map<*, *>

internal fun Map<*, *>.jsonString(key: String): String? = when (val value = this[key]) {
    is String -> value
    is Number -> value.toString()
    else -> null
}

internal fun Map<*, *>.jsonInt(key: String): Int? = when (val value = this[key]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
    else -> null
}

internal fun Map<*, *>.jsonBoolean(key: String): Boolean? = this[key] as? Boolean

internal fun Map<*, *>.jsonList(key: String): List<*>? = this[key] as? List<*>
