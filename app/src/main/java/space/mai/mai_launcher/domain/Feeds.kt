package space.mai.mai_launcher.domain

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Ein Eintrag aus einem RSS-/Atom-Feed (nur Text, keine Bilder). */
data class FeedItem(
    val source: String,
    val title: String,
    val link: String?,
    val publishedMillis: Long?,
)

/** Ergebnis eines Feeds: Titel und Einträge. */
data class Feed(val title: String, val items: List<FeedItem>)

/**
 * Liest RSS 2.0, RSS 1.0 (RDF) und Atom. Reines Java-XML (DOM), ohne
 * externe Entitäten/DTDs. Unbekanntes wird übersprungen.
 */
object FeedParser {
    fun parse(input: InputStream, fallbackTitle: String): Feed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Schutz vor XXE, soweit die Plattform die Schalter kennt.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = factory.newDocumentBuilder().parse(input)
        val root = doc.documentElement
        return when (root.localName ?: root.nodeName) {
            "feed" -> parseAtom(root, fallbackTitle)
            else -> parseRss(root, fallbackTitle)
        }
    }

    private fun parseRss(root: Element, fallback: String): Feed {
        val channel = root.children("channel").firstOrNull()
        val title = channel?.childText("title")?.takeIf { it.isNotBlank() } ?: fallback
        // RSS 2.0: item unter channel; RSS 1.0: item direkt unter rdf:RDF.
        val items = (channel?.children("item").orEmpty() + root.children("item")).mapNotNull { item ->
            val t = item.childText("title")?.let(::cleanText)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FeedItem(
                source = title,
                title = t,
                link = item.childText("link")?.trim()?.ifEmpty { null } ?: item.childText("guid")?.trim()?.takeIf { it.startsWith("http") },
                publishedMillis = (item.childText("pubDate") ?: item.childText("date"))?.let(::parseDate),
            )
        }
        return Feed(title, items)
    }

    private fun parseAtom(root: Element, fallback: String): Feed {
        val title = root.childText("title")?.let(::cleanText)?.takeIf { it.isNotBlank() } ?: fallback
        val items = root.children("entry").mapNotNull { e ->
            val t = e.childText("title")?.let(::cleanText)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val links = e.children("link")
            val link = (links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull())
                ?.getAttribute("href")?.ifEmpty { null }
            FeedItem(title, t, link, (e.childText("published") ?: e.childText("updated"))?.let(::parseDate))
        }
        return Feed(title, items)
    }

    /** RFC 822 (RSS) oder ISO 8601 (Atom, Dublin Core). */
    fun parseDate(raw: String): Long? {
        val s = raw.trim()
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        // Häufige Abweichungen: Zeitzonen-Kürzel wie "GMT"/"EST", fehlender Wochentag.
        val patterns = listOf("EEE, d MMM yyyy HH:mm:ss zzz", "d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm zzz")
        for (p in patterns) {
            runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.ofPattern(p, Locale.ENGLISH)).toInstant().toEpochMilli() }
        }
        return null
    }

    /** HTML-Reste und Leerraum aus Titeln entfernen. */
    fun cleanText(s: String): String =
        s.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("\\s+"), " ").trim()

    private fun Element.children(name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName ?: n.nodeName) == name) out.add(n as Element)
        }
        return out
    }

    private fun Element.childText(name: String): String? = children(name).firstOrNull()?.textContent
}

/** Mischt alle Feeds: neueste zuerst, gleiche Links nur einmal. */
fun mergeFeeds(feeds: List<Feed>, limit: Int = 6): List<FeedItem> =
    feeds.flatMap { it.items }
        .distinctBy { it.link ?: (it.source + it.title) }
        .sortedByDescending { it.publishedMillis ?: 0L }
        .take(limit)

/** Feed-Adressen: nur https, getrimmt, ohne Doppelte. Gespeichert zeilenweise (Schlüssel `feeds`). */
object FeedUrls {
    fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (s.startsWith("http://", ignoreCase = true)) s = "https://" + s.substring(7)
        if (!s.contains("://")) s = "https://$s"
        if (!s.startsWith("https://", ignoreCase = true)) return null
        val host = s.substring(8).substringBefore('/')
        return if (host.contains('.') && host.length >= 4) s else null
    }

    fun encode(urls: List<String>): String = urls.joinToString("\n")
    fun decode(raw: String?): List<String> = raw.orEmpty().lines().mapNotNull { normalize(it) }.distinct()
}
