package com.fap.core.html

/**
 * The tiny CSS subset used by [Element.select].
 *
 * Supported:
 *   div              tag name
 *   .name            class token
 *   #id              id
 *   *                any element
 *   [attr]           attribute present
 *   [attr=value]     exact
 *   [attr*=value]    contains
 *   [attr^=value]    starts with
 *   [attr$=value]    ends with
 *   div .name        descendant
 *   div > .name      direct child
 *   a, b             group
 */
internal class CssSelector(private val steps: List<Step>) {

    internal class Step(
        val tag: String?,
        val classes: List<String>,
        val id: String?,
        val attrs: List<AttrMatcher>,
        val combinator: Char // ' ' (descendant) or '>' (child) - relationship to the previous step
    ) {
        fun matches(element: Element): Boolean {
            if (tag != null && tag != "*" && element.tag != tag) return false
            if (id != null && element.id() != id) return false
            val elementClasses = element.classes()
            for (c in classes) if (c !in elementClasses) return false
            for (a in attrs) if (!a.matches(element)) return false
            return true
        }
    }

    internal class AttrMatcher(val name: String, val op: Char?, val value: String?) {
        fun matches(element: Element): Boolean {
            val actual = element.attr(name) ?: return false
            if (op == null || value == null) return true
            return when (op) {
                '=' -> actual.equals(value, ignoreCase = true)
                '*' -> actual.contains(value, ignoreCase = true)
                '^' -> actual.startsWith(value, ignoreCase = true)
                '$' -> actual.endsWith(value, ignoreCase = true)
                '~' -> actual.split(Regex("\\s+")).any { it.equals(value, ignoreCase = true) }
                else -> true
            }
        }
    }

    fun findIn(root: Element): List<Element> {
        if (steps.isEmpty()) return emptyList()
        var current: List<Element> = root.descendants().filter { steps[0].matches(it) }
        for (k in 1 until steps.size) {
            val step = steps[k]
            val next = LinkedHashSet<Element>()
            for (anchor in current) {
                val pool = if (step.combinator == '>') anchor.elements() else anchor.descendants()
                for (candidate in pool) {
                    if (step.matches(candidate)) next.add(candidate)
                }
            }
            current = next.toList()
        }
        return current
    }

    companion object {
        fun parse(selector: String): List<CssSelector> =
            selector.split(',').mapNotNull { parseGroup(it) }

        private fun parseGroup(group: String): CssSelector? {
            val steps = ArrayList<Step>()
            var i = 0
            val s = group.trim()
            if (s.isEmpty()) return null

            while (i < s.length) {
                while (i < s.length && s[i].isWhitespace()) i++
                if (i >= s.length) break

                // combinator between steps
                if (s[i] == '>') {
                    if (steps.isEmpty()) return null
                    steps[steps.size - 1] = withCombinator(steps.last(), '>')
                    i++
                    continue
                }

                var tag: String? = null
                val classes = ArrayList<String>()
                var id: String? = null
                val attrs = ArrayList<AttrMatcher>()

                loop@ while (i < s.length && !s[i].isWhitespace() && s[i] != '>') {
                    when (s[i]) {
                        '.' -> {
                            val start = ++i
                            while (i < s.length && isNameChar(s[i])) i++
                            if (i > start) classes.add(s.substring(start, i))
                        }
                        '#' -> {
                            val start = ++i
                            while (i < s.length && isNameChar(s[i])) i++
                            if (i > start) id = s.substring(start, i)
                        }
                        '[' -> {
                            val end = s.indexOf(']', i)
                            if (end < 0) {
                                i = s.length
                                break@loop
                            }
                            parseAttr(s.substring(i + 1, end))?.let { attrs.add(it) }
                            i = end + 1
                        }
                        else -> {
                            val start = i
                            while (i < s.length && (isNameChar(s[i]) || s[i] == '*')) i++
                            if (i > start) tag = s.substring(start, i).lowercase()
                            else i++
                        }
                    }
                }

                if (tag == null && classes.isEmpty() && id == null && attrs.isEmpty()) {
                    if (steps.isEmpty()) return null
                    continue
                }
                steps.add(Step(tag, classes, id, attrs, ' '))
            }

            return if (steps.isEmpty()) null else CssSelector(steps)
        }

        private fun withCombinator(step: Step, combinator: Char): Step =
            Step(step.tag, step.classes, step.id, step.attrs, combinator)

        private fun parseAttr(body: String): AttrMatcher? {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return null
            val opIndex = trimmed.indexOfFirst { it == '=' || it == '*' || it == '^' || it == '$' || it == '~' }
            if (opIndex < 0) return AttrMatcher(trimmed.lowercase(), null, null)
            val name = trimmed.substring(0, opIndex).trim().lowercase()
            val op = trimmed[opIndex]
            // "*=", "^=" etc. keep their "=" in the operator, so the value must
            // start after it ("href*=/a/" -> op '*', value "/a/").
            val value = trimmed.substring(opIndex + 1)
                .trim()
                .removePrefix("=")
                .trim()
                .trim('"', '\'')
            if (name.isEmpty() || value.isEmpty()) return AttrMatcher(name, null, null)
            return AttrMatcher(name, op, value)
        }

        private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_'
    }
}
