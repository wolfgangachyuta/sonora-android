package com.sonora.player

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class EpubChapter(val href: String, val title: String, val html: String)
data class EpubDocument(val title: String, val author: String, val chapters: List<EpubChapter>)

/** EPUB 2/3 container, OPF manifest/spine and NCX/EPUB3 navigation reader. */
object EpubParser {
    fun parse(bytes: ByteArray): EpubDocument {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) files[entry.name.replace('\\', '/')] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val container = files["META-INF/container.xml"] ?: error("Invalid EPUB: container.xml is missing")
        val containerParser = parser(container)
        var opfPath = ""
        while (containerParser.eventType != XmlPullParser.END_DOCUMENT) {
            if (containerParser.eventType == XmlPullParser.START_TAG && containerParser.name == "rootfile") {
                opfPath = containerParser.getAttributeValue(null, "full-path").orEmpty()
                if (opfPath.isNotBlank()) break
            }
            containerParser.next()
        }
        require(opfPath.isNotBlank()) { "Invalid EPUB: package document was not found" }
        val opf = files[opfPath] ?: error("Invalid EPUB: package document is missing")
        val base = opfPath.substringBeforeLast('/', "")
        val manifest = linkedMapOf<String, String>()
        val media = linkedMapOf<String, String>()
        val spine = mutableListOf<String>()
        var navHref = ""
        var ncxHref = ""
        var title = ""
        var author = ""
        val p = parser(opf)
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> if (title.isBlank()) title = p.nextText().trim()
                    "creator" -> if (author.isBlank()) author = p.nextText().trim()
                    "item" -> {
                        val id = p.getAttributeValue(null, "id").orEmpty()
                        val href = p.getAttributeValue(null, "href").orEmpty()
                        val type = p.getAttributeValue(null, "media-type").orEmpty()
                        val properties = p.getAttributeValue(null, "properties").orEmpty()
                        if (id.isNotBlank() && href.isNotBlank()) {
                            manifest[id] = resolve(base, href.substringBefore('#'))
                            media[id] = type
                            if (properties.split(' ').contains("nav")) navHref = resolve(base, href.substringBefore('#'))
                            if (type == "application/x-dtbncx+xml") ncxHref = resolve(base, href.substringBefore('#'))
                        }
                    }
                    "itemref" -> p.getAttributeValue(null, "idref")?.let { spine += it }
                }
            }
            p.next()
        }
        val navTitles = when {
            navHref.isNotBlank() -> parseEpub3Nav(files[navHref])
            ncxHref.isNotBlank() -> parseNcx(files[ncxHref])
            else -> emptyMap()
        }
        val chapters = spine.mapNotNull { id ->
            val href = manifest[id] ?: return@mapNotNull null
            val type = media[id].orEmpty()
            if (type.isNotBlank() && !type.contains("html") && !type.contains("xhtml")) return@mapNotNull null
            val raw = files[href]?.toString(Charsets.UTF_8) ?: return@mapNotNull null
            EpubChapter(href, navTitles[href] ?: href.substringAfterLast('/').substringBeforeLast('.'), style(raw))
        }
        require(chapters.isNotEmpty()) { "This EPUB has no readable chapters in its spine" }
        return EpubDocument(title, author, chapters)
    }

    private fun parser(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun parseEpub3Nav(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val p = parser(bytes)
        var href = ""
        var inAnchor = false
        var text = StringBuilder()
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> if (p.name == "a") {
                    href = p.getAttributeValue(null, "href").orEmpty().substringBefore('#')
                    text = StringBuilder()
                    inAnchor = true
                }
                XmlPullParser.TEXT -> if (inAnchor) text.append(p.text)
                XmlPullParser.END_TAG -> if (p.name == "a") {
                    if (href.isNotBlank() && text.isNotBlank()) result[href.substringAfterLast('/')] = text.toString().trim()
                    inAnchor = false
                }
            }
            p.next()
        }
        return result
    }

    private fun parseNcx(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val p = parser(bytes)
        var label = ""
        var src = ""
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "text" -> label = p.nextText().trim()
                    "content" -> {
                        src = p.getAttributeValue(null, "src").orEmpty().substringBefore('#')
                        if (src.isNotBlank() && label.isNotBlank()) result[src.substringAfterLast('/')] = label
                    }
                }
            }
            p.next()
        }
        return result
    }

    private fun resolve(base: String, href: String): String {
        val parts = (if (base.isBlank()) href else "$base/$href").split('/')
        val clean = mutableListOf<String>()
        parts.forEach { part ->
            when (part) { "", "." -> Unit; ".." -> if (clean.isNotEmpty()) clean.removeAt(clean.lastIndex); else -> clean += part }
        }
        return clean.joinToString("/")
    }

    private fun style(html: String): String {
        val css = "<meta name='viewport' content='width=device-width, initial-scale=1'><style>body{font-family:serif;font-size:1.08em;line-height:1.75;padding:1.2em;color:#252722;background:#f7f6f1;max-width:48em;margin:auto}img{max-width:100%;height:auto}a{color:#56745d}p{margin:0 0 1em}h1,h2,h3{line-height:1.3}</style>"
        return if (html.contains("<head", true)) html.replaceFirst(Regex("(?i)<head[^>]*>"), "$0$css")
        else "<html><head>$css</head><body>$html</body></html>"
    }
}
