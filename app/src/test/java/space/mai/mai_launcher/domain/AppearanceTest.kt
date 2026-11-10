package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.mai.mai_launcher.design.powerOnFrame
import space.mai.mai_launcher.design.powerOnPanel

class AppearanceTest {
    @Test fun indexIsStableAndTolerant() {
        // Gespeichert wird der Index: Reihenfolge nie ändern.
        assertEquals(listOf("Ion", "Signal", "Amber", "Violet", "White"), AccentChoice.entries.map { it.name })
        assertEquals(AccentChoice.Ion, AccentChoice.of(99))
        assertEquals(AccentChoice.Ion, AccentChoice.of(-1))
        assertEquals(AccentChoice.Ion, AccentChoice.White.next())
    }

    @Test fun areasStayDistinguishable() {
        AccentChoice.entries.forEach { base ->
            val a = AppearanceRules.areas(base)
            assertEquals(base, a.deck)
            assertNotEquals(a.deck, a.comms)
            assertNotEquals(a.deck, a.media)
            assertNotEquals(a.comms, a.media)
        }
        assertEquals(AccentChoice.Ion, AppearanceRules.areas(AccentChoice.Signal).comms)
        assertEquals(AccentChoice.Ion, AppearanceRules.areas(AccentChoice.Amber).media)
    }

    @Test fun systemAnimationsOffMeansReduceMotion() {
        assertTrue(AppearanceRules.reduceMotion(pref = false, systemAnimatorScale = 0f))
        assertTrue(AppearanceRules.reduceMotion(pref = true, systemAnimatorScale = 1f))
        assertFalse(AppearanceRules.reduceMotion(pref = false, systemAnimatorScale = 0.5f))
    }

    @Test fun powerOnTiming() {
        // Rahmen nach 180 von 240 ms fertig, Panels versetzt, alles bei 1 sichtbar.
        assertEquals(0f, powerOnFrame(0f), 0.001f)
        assertEquals(1f, powerOnFrame(0.75f), 0.001f)
        assertEquals(0f, powerOnPanel(0f, 0), 0.001f)
        assertTrue(powerOnPanel(0.2f, 0) > powerOnPanel(0.2f, 1))
        assertEquals(0f, powerOnPanel(0.5f, 4), 0.001f) // 120 ms < 160 ms Versatz
        (0..8).forEach { assertEquals(1f, powerOnPanel(1f, it), 0.001f) }
        assertEquals(1f, powerOnPanel(0.999f, 4), 0.05f)
    }
}
