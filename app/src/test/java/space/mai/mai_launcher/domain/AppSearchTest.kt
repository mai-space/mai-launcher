package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    private fun app(label: String) = AppInfo(AppKey("p.$label", "C"), label)
    private val apps = listOf(
        app("Calendar"), app("Calculator"), app("Camera"), app("Google Maps"),
        app("Ruf durch"), app("Ärzte"), app("Messages"), app("Music"),
    )

    @Test fun prefixBeatsContains() {
        // "ar": Ärzte beginnt damit (ohne Umlaut), Calendar enthält es nur.
        assertEquals(listOf("Ärzte", "Calendar"), AppSearch.filter(apps, "ar").map { it.label })
        // Zwei Buchstaben treffen keine bloße Buchstabenfolge (Messages enthält m…a).
        assertEquals(listOf("Google Maps"), AppSearch.filter(apps, "ma").map { it.label })
    }

    @Test fun initials() {
        assertEquals("Google Maps", AppSearch.filter(apps, "gm").first().label)
        assertEquals("Ruf durch", AppSearch.filter(apps, "rd").first().label)
    }

    @Test fun ignoresAccentsAndCase() {
        assertEquals("Ärzte", AppSearch.filter(apps, "ARZ").single().label)
    }

    @Test fun launchCountBreaksTies() {
        val counts = mapOf(AppKey("p.Calculator", "C") to 5)
        val r = AppSearch.filter(apps, "cal").map { it.label }
        val r2 = AppSearch.filter(apps, "cal", counts).map { it.label }
        assertEquals(listOf("Calculator", "Calendar"), r)
        assertEquals(listOf("Calculator", "Calendar"), r2)
        val counts2 = mapOf(AppKey("p.Calendar", "C") to 3)
        assertEquals("Calendar", AppSearch.filter(apps, "cal", counts2).first().label)
    }

    @Test fun blankReturnsAll() {
        assertEquals(apps, AppSearch.filter(apps, "  "))
    }

    @Test fun noMatch() {
        assertTrue(AppSearch.filter(apps, "zzz").isEmpty())
    }

    @Test fun subsequence() {
        assertTrue(AppSearch.score("Messages", "msg") > 0)
        assertEquals(0, AppSearch.score("Music", "x"))
    }
}
