package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AlphaIndexTest {
    private fun app(label: String) = AppInfo(AppKey("p.$label", "C"), label)

    @Test fun groupsSortsAndPutsHashLast() {
        val s = AlphaIndex.sections(
            listOf(app("Ärzte"), app("9GAG"), app("apotheke"), app("Bank"), app("Übersetzer")),
            Locale.GERMAN,
        )
        assertEquals(listOf('A', 'B', 'U', '#'), s.map { it.letter })
        assertEquals(listOf("apotheke", "Ärzte"), s[0].apps.map { it.label })
    }

    @Test fun monogram() {
        assertEquals("Ca", monogram("Calendar"))
        assertEquals("RD", monogram("Ruf durch"))
        assertEquals("?", monogram("  "))
    }

    @Test fun indexLetterFallback() {
        assertEquals('#', indexLetter("😀 Fun"))
        assertEquals('O', indexLetter("Öffi"))
    }
}
