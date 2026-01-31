/*
 * Pure Kotlin XML parser for linuxArm64 target.
 * Implements a simple DOM parser for Android vector drawable XML files.
 */
package org.jetbrains.compose.resources.vector.xmldom

/**
 * Parse XML string into a DOM Element.
 */
internal fun parse(xml: String): Element {
    val parser = KotlinXmlParser(xml)
    return parser.parse()
}

private class KotlinXmlParser(private val xml: String) {
    private var pos = 0

    fun parse(): Element {
        skipWhitespaceAndDeclaration()
        return parseElement() ?: throw MalformedXMLException("No root element found")
    }

    private fun skipWhitespaceAndDeclaration() {
        skipWhitespace()
        // Skip XML declaration <?xml ... ?>
        while (pos < xml.length && xml.startsWith("<?", pos)) {
            val end = xml.indexOf("?>", pos)
            if (end == -1) throw MalformedXMLException("Unclosed XML declaration")
            pos = end + 2
            skipWhitespace()
        }
        // Skip comments <!-- ... -->
        while (pos < xml.length && xml.startsWith("<!--", pos)) {
            val end = xml.indexOf("-->", pos)
            if (end == -1) throw MalformedXMLException("Unclosed comment")
            pos = end + 3
            skipWhitespace()
        }
        // Skip DOCTYPE
        while (pos < xml.length && xml.startsWith("<!DOCTYPE", pos)) {
            var depth = 1
            pos += 9
            while (pos < xml.length && depth > 0) {
                when (xml[pos]) {
                    '<' -> depth++
                    '>' -> depth--
                }
                pos++
            }
            skipWhitespace()
        }
    }

    private fun parseElement(): ElementImpl? {
        skipWhitespace()
        if (pos >= xml.length || xml[pos] != '<') return null
        if (xml.startsWith("</", pos)) return null // End tag
        if (xml.startsWith("<!--", pos)) {
            // Skip comment
            val end = xml.indexOf("-->", pos)
            if (end == -1) throw MalformedXMLException("Unclosed comment")
            pos = end + 3
            return parseElement()
        }

        pos++ // Skip '<'
        skipWhitespace()

        // Parse tag name (possibly with namespace prefix)
        val tagName = parseTagName()
        val (prefix, localName) = splitQualifiedName(tagName)

        // Parse attributes and namespace declarations
        val attributes = mutableMapOf<String, String>()
        val namespaceDeclarations = mutableMapOf<String, String>()

        skipWhitespace()
        while (pos < xml.length && xml[pos] != '>' && xml[pos] != '/') {
            val attrName = parseAttributeName()
            if (attrName.isEmpty()) break

            skipWhitespace()
            if (pos < xml.length && xml[pos] == '=') {
                pos++ // Skip '='
                skipWhitespace()
                val attrValue = parseAttributeValue()

                // Check if it's a namespace declaration
                if (attrName.startsWith("xmlns:")) {
                    val nsPrefix = attrName.substring(6)
                    namespaceDeclarations[nsPrefix] = attrValue
                } else if (attrName == "xmlns") {
                    namespaceDeclarations[""] = attrValue
                } else {
                    attributes[attrName] = attrValue
                }
            }
            skipWhitespace()
        }

        // Determine namespace URI
        val namespaceURI = if (prefix.isEmpty()) {
            namespaceDeclarations[""] ?: ""
        } else {
            namespaceDeclarations[prefix] ?: ""
        }

        // Build prefix map (namespace URI -> prefix)
        val prefixMap = namespaceDeclarations.entries.associate { (k, v) -> v to k }

        val element = ElementImpl(
            localName = localName,
            nodeName = tagName,
            namespaceURI = namespaceURI,
            prefixMap = prefixMap,
            attributes = attributes
        )

        // Check for self-closing tag
        if (pos < xml.length && xml[pos] == '/') {
            pos++ // Skip '/'
            if (pos < xml.length && xml[pos] == '>') {
                pos++ // Skip '>'
            }
            return element
        }

        if (pos < xml.length && xml[pos] == '>') {
            pos++ // Skip '>'
        }

        // Parse children
        val textBuilder = StringBuilder()
        while (pos < xml.length) {
            // Skip comments in content
            if (xml.startsWith("<!--", pos)) {
                val end = xml.indexOf("-->", pos)
                if (end == -1) throw MalformedXMLException("Unclosed comment")
                pos = end + 3
                continue
            }

            if (xml.startsWith("</", pos)) {
                // End tag
                pos += 2 // Skip '</'
                skipWhitespace()
                val endTagName = parseTagName()
                skipWhitespace()
                if (pos < xml.length && xml[pos] == '>') {
                    pos++ // Skip '>'
                }
                if (endTagName != tagName) {
                    throw MalformedXMLException("Mismatched tags: expected </$tagName>, found </$endTagName>")
                }
                break
            } else if (xml[pos] == '<') {
                // Child element
                val child = parseElement()
                if (child != null) {
                    element.children.add(child)
                }
            } else {
                // Text content
                val textStart = pos
                while (pos < xml.length && xml[pos] != '<') {
                    pos++
                }
                val text = xml.substring(textStart, pos)
                textBuilder.append(decodeEntities(text))
            }
        }

        val textContent = textBuilder.toString().trim()
        if (textContent.isNotEmpty()) {
            element.textContent = textContent
        }

        return element
    }

    private fun parseTagName(): String {
        val start = pos
        while (pos < xml.length && isNameChar(xml[pos])) {
            pos++
        }
        return xml.substring(start, pos)
    }

    private fun parseAttributeName(): String {
        val start = pos
        while (pos < xml.length && isNameChar(xml[pos])) {
            pos++
        }
        return xml.substring(start, pos)
    }

    private fun parseAttributeValue(): String {
        if (pos >= xml.length) return ""

        val quote = xml[pos]
        if (quote != '"' && quote != '\'') {
            throw MalformedXMLException("Expected quote for attribute value at position $pos")
        }
        pos++ // Skip opening quote

        val start = pos
        while (pos < xml.length && xml[pos] != quote) {
            pos++
        }
        val value = xml.substring(start, pos)

        if (pos < xml.length) {
            pos++ // Skip closing quote
        }

        return decodeEntities(value)
    }

    private fun skipWhitespace() {
        while (pos < xml.length && xml[pos].isWhitespace()) {
            pos++
        }
    }

    private fun isNameChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == ':' || c == '_' || c == '-' || c == '.'
    }

    private fun splitQualifiedName(name: String): Pair<String, String> {
        val colonIndex = name.indexOf(':')
        return if (colonIndex == -1) {
            "" to name
        } else {
            name.substring(0, colonIndex) to name.substring(colonIndex + 1)
        }
    }

    private fun decodeEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}

internal class ElementImpl(
    override val localName: String,
    override val nodeName: String,
    override val namespaceURI: String,
    private val prefixMap: Map<String, String>,
    private val attributes: Map<String, String>
) : Element {

    override var textContent: String? = null

    internal val children = mutableListOf<Node>()

    override val childNodes: NodeList
        get() = object : NodeList {
            override fun item(i: Int): Node = children[i]
            override val length: Int get() = children.size
        }

    override fun getAttributeNS(nameSpaceURI: String, localName: String): String {
        val prefix = prefixMap[nameSpaceURI] ?: ""
        val attrKey = if (prefix.isEmpty()) localName else "$prefix:$localName"
        return getAttribute(attrKey)
    }

    override fun getAttribute(name: String): String = attributes[name] ?: ""

    override fun lookupPrefix(namespaceURI: String): String = prefixMap[namespaceURI] ?: ""
}
