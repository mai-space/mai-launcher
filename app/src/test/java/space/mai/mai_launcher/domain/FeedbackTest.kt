package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FeedbackTest {
    @Test fun levelsCycleAndReadTolerantly() {
        assertEquals(HapticLevel.Strong, HapticLevel.Light.next())
        assertEquals(HapticLevel.Off, HapticLevel.Strong.next())
        assertEquals(SoundLevel.Quiet, SoundLevel.Off.next())
        assertEquals(SoundLevel.Off, SoundLevel.Loud.next())
        // Unbekannte gespeicherte Werte → Standard.
        assertEquals(HapticLevel.Light, HapticLevel.of(42))
        assertEquals(SoundLevel.Off, SoundLevel.of(-1))
        // Gespeichert wird der Index: Reihenfolge festhalten.
        assertEquals(listOf("Off", "Light", "Strong"), HapticLevel.entries.map { it.name })
        assertEquals(listOf("Off", "Quiet", "Medium", "Loud"), SoundLevel.entries.map { it.name })
    }

    @Test fun tonesAreShortAndFadeOut() {
        for (kind in FeedbackKind.entries) {
            val (freqs, ms) = ToneSynth.spec(kind)
            assertTrue(ms <= 100)
            val s = ToneSynth.samples(freqs, ms)
            assertEquals(ToneSynth.SAMPLE_RATE * ms / 1000, s.size)
            assertTrue(kotlin.math.abs(s.first().toInt()) < 2000)
            val peak = s.maxOf { kotlin.math.abs(it.toInt()) }
            val tail = s.takeLast(s.size / 10).maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue(tail < peak / 2)
        }
    }

    @Test fun wavHeader() {
        val wav = ToneSynth.wav(ShortArray(100) { 1 })
        assertEquals(44 + 200, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(ToneSynth.SAMPLE_RATE, b.getInt(24))
        assertEquals(200, b.getInt(40))
    }
}
