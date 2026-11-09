package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutBackupTest {
    private val sample = LayoutBackup(
        dock = listOf(AppKey("app.phone", "Main"), null, AppKey("app.music", "Player", 0), AppKey("app.browser", "Main", 10)),
        destinations = listOf("Hafenstraße 1, Hamburg", ""),
        feeds = listOf("https://example.org/feed.xml"),
        widgets = listOf(ProviderRef("space.mai.mai_fitness", "NextWorkoutWidgetProvider")),
        signalsShowText = true,
        haptics = HapticLevel.Strong,
        sounds = SoundLevel.Quiet,
    )

    @Test fun roundTrip() {
        val text = LayoutBackupCodec.encode(sample)
        assertTrue(text.startsWith(LayoutBackupCodec.HEADER))
        assertEquals(sample, LayoutBackupCodec.decode(text))
    }

    @Test fun containsNoHistoryOrContacts() {
        val text = LayoutBackupCodec.encode(sample)
        // Nur bekannte Schlüssel – kein Startverlauf, kein Comms-Verlauf.
        val keys = text.lines().drop(1).filter { it.isNotBlank() }.map { it.substringBefore('=').substringBefore('.') }.toSet()
        assertEquals(setOf("dock", "dest", "feed", "widget", "signals_show_text", "haptics", "sounds"), keys)
    }

    @Test fun readsTolerantly() {
        val text = """
            mai-launcher-layout v2
            dock.0=app.phone%2FMain%230
            dock.9=app.x%2FY%230
            dock.1=kaputt
            feed=http://unsicher.example/feed
            feed=https://example.org/a
            widget=nur-paket
            haptics=vielleicht
            sounds=LOUD
            future_key=whatever
            ohne-gleich
        """.trimIndent()
        val b = LayoutBackupCodec.decode(text)!!
        assertEquals(AppKey("app.phone", "Main"), b.dock[0])
        assertNull(b.dock[1])
        // http wird wie beim Eintragen auf https gehoben.
        assertEquals(listOf("https://unsicher.example/feed", "https://example.org/a"), b.feeds)
        assertTrue(b.widgets.isEmpty())
        assertNull(b.haptics)
        assertEquals(SoundLevel.Loud, b.sounds)
    }

    @Test fun rejectsOtherFiles() {
        assertNull(LayoutBackupCodec.decode("hello"))
        assertNull(LayoutBackupCodec.decode(""))
    }

    @Test fun resolveDockAcrossDevices() {
        val installed = listOf(
            AppInfo(AppKey("app.music", "Player", 0), "Music"),
            AppInfo(AppKey("app.browser", "Main", 11), "Browser (work)", isWork = true),
            AppInfo(AppKey("app.browser", "Main", 0), "Browser"),
            AppInfo(AppKey("app.phone", "NewMain", 0), "Phone"),
        )
        val r = LayoutBackupCodec.resolveDock(sample.dock, installed)
        assertEquals(AppKey("app.phone", "NewMain", 0), r[0])
        assertNull(r[1])
        assertEquals(AppKey("app.music", "Player", 0), r[2])
        // Profil 10 gibt es hier nicht: Hauptprofil bevorzugt.
        assertEquals(AppKey("app.browser", "Main", 0), r[3])
        assertNull(LayoutBackupCodec.resolveDock(listOf(AppKey("gone", "X")), installed)[0])
    }
}
