package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedsTest {
    private fun parse(xml: String) = FeedParser.parse(xml.byteInputStream(), "fallback.example")

    @Test fun rss2() {
        val f = parse(
            """<?xml version="1.0"?><rss version="2.0"><channel><title>Beispiel News</title>
            <item><title>Erster &amp; bester</title><link>https://ex.org/1</link><pubDate>Mon, 09 Nov 2026 10:00:00 GMT</pubDate></item>
            <item><title><![CDATA[<b>Zweiter</b>  Beitrag]]></title><guid>https://ex.org/2</guid><pubDate>Mon, 9 Nov 2026 08:30:00 +0100</pubDate></item>
            <item><link>https://ex.org/ohne-titel</link></item>
            </channel></rss>""",
        )
        assertEquals("Beispiel News", f.title)
        assertEquals(2, f.items.size)
        assertEquals("Erster & bester", f.items[0].title)
        assertEquals("Zweiter Beitrag", f.items[1].title)
        assertEquals("https://ex.org/2", f.items[1].link)
        assertEquals(1_794_218_400_000L, f.items[0].publishedMillis)
    }

    @Test fun atom() {
        val f = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title>
            <entry><title>Hallo</title><link rel="alternate" href="https://blog.example/hallo"/><updated>2026-11-08T12:00:00Z</updated></entry>
            </feed>""",
        )
        assertEquals("Blog", f.title)
        assertEquals("https://blog.example/hallo", f.items.single().link)
        assertEquals(1_794_139_200_000L, f.items.single().publishedMillis)
    }

    @Test fun rdfAndFallbackTitle() {
        val f = parse(
            """<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <channel><title></title></channel>
            <item><title>RDF-Eintrag</title><link>https://r.example/1</link><dc:date>2026-11-07T09:00:00+01:00</dc:date></item>
            </rdf:RDF>""",
        )
        assertEquals("fallback.example", f.title)
        assertEquals("RDF-Eintrag", f.items.single().title)
        assertTrue(f.items.single().publishedMillis != null)
    }

    @Test fun rejectsDoctype() {
        // Schutz vor XXE: Dokumente mit DOCTYPE werden abgelehnt.
        val evil = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>&x;</title></channel></rss>"""
        assertTrue(runCatching { parse(evil) }.isFailure)
    }

    @Test fun dates() {
        assertEquals(null, FeedParser.parseDate("gestern"))
        assertTrue(FeedParser.parseDate("Sun, 08 Nov 2026 18:00:00 EST") != null)
        assertTrue(FeedParser.parseDate("2026-11-08T18:00:00.123+02:00") != null)
    }

    @Test fun mergeNewestFirstWithoutDuplicates() {
        val a = Feed("A", listOf(FeedItem("A", "alt", "https://x/1", 1), FeedItem("A", "neu", "https://x/2", 5)))
        val b = Feed("B", listOf(FeedItem("B", "doppelt", "https://x/2", 5), FeedItem("B", "ohne Datum", null, null)))
        assertEquals(listOf("neu", "alt", "ohne Datum"), mergeFeeds(listOf(a, b)).map { it.title })
    }

    @Test fun urls() {
        assertEquals("https://example.org/feed", FeedUrls.normalize(" example.org/feed "))
        assertEquals("https://example.org/rss", FeedUrls.normalize("http://example.org/rss"))
        assertNull(FeedUrls.normalize("ftp://example.org"))
        assertNull(FeedUrls.normalize("kein feed"))
        assertNull(FeedUrls.normalize("https://x"))
        assertEquals(listOf("https://a.de/f"), FeedUrls.decode("https://a.de/f\n\nmüll leer\nhttps://a.de/f"))
    }
}
