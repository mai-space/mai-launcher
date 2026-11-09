package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalsTest {
    private val items = listOf(
        SignalItem("a1", "chat.app", "Chat", "Alex", "Bis gleich!", 100),
        SignalItem("a2", "chat.app", "Chat", "Sam", "Kommst du?", 300, number = 3),
        SignalItem("m1", "mail.app", "Mail", "Rechnung", "Ihre Rechnung", 200),
    )

    @Test fun groupsPerAppNewestFirstWithCounts() {
        val g = SignalDigest.group(items, showText = true)
        assertEquals(listOf("chat.app", "mail.app"), g.map { it.packageName })
        assertEquals(4, g[0].count)
        assertEquals("Sam", g[0].latestTitle)
        assertEquals("Kommst du?", g[0].latestText)
        assertEquals("a2", g[0].latestKey)
    }

    @Test fun hidesTextWhenPrivate() {
        val g = SignalDigest.group(items, showText = false)
        assertNull(g[0].latestTitle)
        assertNull(g[0].latestText)
        assertEquals(4, g[0].count)
    }

    @Test fun badges() {
        assertEquals(mapOf("chat.app" to 4, "mail.app" to 1), SignalDigest.badges(items))
        assertNull(SignalDigest.badgeText(0))
        assertEquals("3", SignalDigest.badgeText(3))
        assertEquals("9+", SignalDigest.badgeText(12))
    }
}
