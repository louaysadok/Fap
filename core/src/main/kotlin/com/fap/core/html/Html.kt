package com.fap.core.html

/**
 * A tiny, dependency-free HTML parser.
 *
 * The whole point of this file is that the scraper core stays 100% pure Kotlin:
 * no jsoup, no Android classes, no third party jars. That keeps the core module
 * unit-testable on a plain JVM and keeps the Android build graph tiny.
 *
 * It is deliberately not a full HTML5 implementation. It handles the things a
 * scraper actually needs:
 *   - a forgiving tree builder (unclosed tags, junk, comments, raw text blocks)
 *   - attributes (double quoted, single quoted, unquoted and boolean)
 *   - entity decoding
 *   - a small CSS subset: "div", ".class", "#id", "[attr*=value]", "a > img",
 *     "div a", and comma separated groups.
 */
public open class Element internal constructor(public val tag: String) : Node() {

    private val attrs: LinkedHashMap<String, String> = LinkedHashMap()
    internal val childNodes: MutableList<Node> = ArrayList()

    public var parent: Element? = null
        internal set

    /** Attributes as a lowercase-keyed map (HTML attribute names are case insensitive). */
    public val attributes: Map<String, String> get() = attrs

    public fun attr(name: String): String? = attrs[name.lowercase()]

    public fun attr(name: String, fallback: String): String = attr(name) ?: fallback

    public fun hasAttr(name: String): Boolean = attrs.containsKey(name.lowercase())

    public fun id(): String? = attr("id")

    public fun classes(): Set<String> = classList(attr("class"))

    public fun hasClass(name: String): Boolean = classes().contains(name)

    /** Text of this element and all descendants, with entities decoded. */
    public fun text(): String = buildString { appendText(this) }

    /** Same as [text] but with whitespace collapsed and trimmed. */
    public fun cleanText(): String = collapse(text())

    /**
     * Text of this element only, ignoring nested elements. Useful for
     * "N Likes" style counters that live next to a child node.
     */
    public fun ownText(): String {
        val sb = StringBuilder()
        for (node in childNodes) {
            if (node is TextNode) sb.append(node.raw)
        }
        return decodeEntities(normaliseWhitespace(sb.toString()))
    }

    private fun appendText(sb: StringBuilder) {
        for (node in childNodes) {
            when (node) {
                is TextNode -> sb.append(node.value)
                is Element -> {
                    // Block level boundaries become newlines so that regexes like
                    // "^\s*Posted by\s+(.+)$" behave the way a reader expects.
                    val block = node.tag in BLOCK_TAGS
                    if (block) sb.append('\n')
                    node.appendText(sb)
                    if (block) sb.append('\n')
                }
            }
        }
    }

    public fun elements(): List<Element> = childNodes.filterIsInstance<Element>()

    /** Direct children with the given tag name ("*" for any). */
    public fun children(tag: String = "*"): List<Element> =
        elements().filter { tag == "*" || it.tag == tag }

    public fun first(selector: String): Element? = select(selector).firstOrNull()

    public fun select(selector: String): List<Element> {
        val result = LinkedHashSet<Element>()
        for (group in CssSelector.parse(selector)) {
            result.addAll(group.findIn(this))
        }
        return result.toList()
    }

    /** This element plus all descendants, in document order. */
    public fun allElements(): List<Element> {
        val out = ArrayList<Element>()
        collectInto(out)
        return out
    }

    private fun collectInto(out: MutableList<Element>) {
        out.add(this)
        for (child in elements()) child.collectInto(out)
    }

    internal fun descendants(): List<Element> {
        val out = ArrayList<Element>()
        for (child in elements()) child.collectInto(out)
        return out
    }

    /** Walk up the tree. Starts with this element. */
    public fun ancestorsAndSelf(): Sequence<Element> = generateSequence(this) { it.parent }

    /**
     * Nearest ancestor (including self) that satisfies [predicate], looking at
     * most [maxLevels] levels up. Used to find the "card" an item belongs to.
     */
    public fun closest(maxLevels: Int = 8, predicate: (Element) -> Boolean): Element? =
        ancestorsAndSelf().take(maxLevels).firstOrNull(predicate)

    override fun toString(): String = "<$tag${if (attrs.isEmpty()) "" else " " + attrs.entries.joinToString(" ")}>"
}

public sealed class Node

internal class TextNode(rawText: String) : Node() {
    /** Raw (still encoded) source text, kept for [Element.ownText]. */
    public val raw: String = normaliseWhitespace(rawText)
    /** Decoded text. */
    public val value: String = decodeEntities(raw)
}

public class Document internal constructor() : Element("#root") {

    /** <title> text, if the page had one. */
    public fun title(): String? = first("title")?.cleanText()?.takeIf { it.isNotEmpty() }

    /** <meta property="og:image" content="..."> etc. */
    public fun metaContent(nameOrProperty: String): String? {
        for (meta in select("meta")) {
            val key = meta.attr("property") ?: meta.attr("name")
            if (key != null && key.equals(nameOrProperty, ignoreCase = true)) {
                val content = meta.attr("content")
                if (!content.isNullOrBlank()) return content.trim()
            }
        }
        return null
    }
}

public object Html {

    /** Parse a whole document. Never throws on malformed markup. */
    public fun parse(html: String): Document {
        val doc = Document()
        val stack = ArrayList<Element>()
        stack.add(doc)
        var i = 0
        val n = html.length

        while (i < n) {
            val open = html.indexOf('<', i)
            if (open < 0) {
                addText(stack.last(), html.substring(i))
                break
            }
            if (open > i) addText(stack.last(), html.substring(i, open))
            i = open

            if (html.startsWith("<!--", i)) {
                val end = html.indexOf("-->", i + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            if (html.startsWith("<![", i) || html.startsWith("<!D", i) || html.startsWith("<?", i)) {
                val end = html.indexOf('>', i)
                i = if (end < 0) n else end + 1
                continue
            }

            // Tag name
            var j = i + 1
            var closing = false
            if (j < n && html[j] == '/') {
                closing = true
                j++
            }
            val nameStart = j
            while (j < n && isTagNameChar(html[j])) j++
            if (j == nameStart) {
                // Stray '<' - treat as text
                addText(stack.last(), "<")
                i++
                continue
            }
            val tagName = html.substring(nameStart, j).lowercase()
            val element = Element(tagName)
            var selfClosing = false

            // Attributes
            while (j < n) {
                while (j < n && isSpace(html[j])) j++
                if (j >= n) break
                if (html[j] == '>') {
                    j++
                    break
                }
                if (html[j] == '/') {
                    selfClosing = true
                    j++
                    continue
                }
                val aStart = j
                while (j < n && !isSpace(html[j]) && html[j] != '=' && html[j] != '>' && html[j] != '/') j++
                if (j == aStart) {
                    j++
                    continue
                }
                val attrName = html.substring(aStart, j).lowercase()
                var k = j
                while (k < n && isSpace(html[k])) k++
                var value: String? = null
                if (k < n && html[k] == '=') {
                    k++
                    while (k < n && isSpace(html[k])) k++
                    if (k < n && (html[k] == '"' || html[k] == '\'')) {
                        val quote = html[k]
                        val vStart = k + 1
                        val vEnd = html.indexOf(quote, vStart)
                        value = if (vEnd < 0) html.substring(vStart) else html.substring(vStart, vEnd)
                        k = if (vEnd < 0) n else vEnd + 1
                    } else {
                        val vStart = k
                        while (k < n && !isSpace(html[k]) && html[k] != '>') k++
                        value = html.substring(vStart, k)
                    }
                }
                if (attrName.isNotEmpty()) {
                    element.attributes as LinkedHashMap<String, String>
                    element.setAttribute(attrName, value ?: "")
                }
                j = k
            }
            i = j

            if (closing) {
                // Pop until we find a matching open tag. Unclosed tags are discarded.
                for (s in stack.size - 1 downTo 1) {
                    if (stack[s].tag == tagName) {
                        for (x in stack.size - 1 downTo s) stack.removeAt(x)
                        break
                    }
                }
                continue
            }

            stack.last().appendChild(element)
            if (!selfClosing && tagName !in VOID_TAGS) {
                if (tagName == "script" || tagName == "style") {
                    val end = html.indexOf("</$tagName", i)
                    if (end < 0) {
                        i = n
                    } else {
                        addText(element, html.substring(i, end))
                        i = html.indexOf('>', end).let { if (it < 0) n else it + 1 }
                    }
                    // raw text blocks are always closed by their end tag
                    continue
                }
                stack.add(element)
            }
        }
        return doc
    }

    private fun addText(parent: Element, raw: String) {
        if (raw.isEmpty()) return
        val existing = parent.childNodes.lastOrNull()
        if (existing is TextNode) {
            parent.childNodes.removeAt(parent.childNodes.size - 1)
            val merged = TextNode(existing.raw + raw)
            parent.childNodes.add(merged)
        } else {
            parent.childNodes.add(TextNode(raw))
        }
    }

    private fun isTagNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '-' || c == ':' || c == '_'

    private fun isSpace(c: Char): Boolean = c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\u000c'

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )
}

internal fun Element.appendChild(child: Element) {
    child.parent = this
    childNodes.add(child)
}

internal fun Element.setAttribute(name: String, value: String) {
    val map = attributes as LinkedHashMap<String, String>
    map[name.lowercase()] = value
}

internal fun classList(raw: String?): Set<String> =
    if (raw.isNullOrBlank()) emptySet()
    else raw.split(' ', '\n', '\r', '\t', '\u000c').filter { it.isNotBlank() }.toSet()

internal fun normaliseWhitespace(value: String): String =
    value.replace('\u00a0', ' ')

internal fun collapse(value: String): String =
    value.trim().replace(Regex("\\s+"), " ")

internal fun decodeEntities(input: String): String {
    if (input.indexOf('&') < 0) return input
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c != '&') {
            sb.append(c)
            i++
            continue
        }
        val semi = input.indexOf(';', i)
        if (semi < 0 || semi - i > 12) {
            sb.append(c)
            i++
            continue
        }
        val body = input.substring(i + 1, semi)
        val decoded = when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.substring(2).toIntOrNull(16)?.toChar()?.toString()
            body.startsWith("#") ->
                body.substring(1).toIntOrNull()?.toChar()?.toString()
            else -> NAMED[body]
        }
        if (decoded == null) {
            sb.append(c)
            i++
        } else {
            sb.append(decoded)
            i = semi + 1
        }
    }
    return sb.toString()
}

private val BLOCK_TAGS = setOf(
    "address", "article", "aside", "blockquote", "br", "button", "caption", "dd", "div",
    "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
    "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section",
    "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul"
)

private val NAMED: Map<String, String> = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
    "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
    "copy" to "©", "trade" to "™", "euro" to "€", "times" to "×"
)
