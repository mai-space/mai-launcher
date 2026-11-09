package space.mai.mai_launcher.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Stärke der Vibration bei Interaktionen. Gespeichert wird der Index – nur am Ende erweitern. */
enum class HapticLevel {
    Off, Light, Strong;

    fun next(): HapticLevel = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Tolerant lesen: Unbekanntes ergibt den Standard. */
        fun of(index: Int): HapticLevel = entries.getOrElse(index) { Light }
    }
}

/** Lautstärke der Interaktions-Töne. Gespeichert wird der Index – nur am Ende erweitern. */
enum class SoundLevel(val gain: Float) {
    Off(0f), Quiet(0.25f), Medium(0.5f), Loud(0.85f);

    fun next(): SoundLevel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(index: Int): SoundLevel = entries.getOrElse(index) { Off }
    }
}

/** Arten von Rückmeldung. */
enum class FeedbackKind { Tap, LongPress, Open }

/**
 * Erzeugt die kurzen, synthetischen Töne (keine Audiodateien im Paket):
 * leises „Tick“ fürs Tippen, Doppelton fürs Halten, kurzer Anstieg beim
 * Öffnen eines Bildschirms.
 */
object ToneSynth {
    const val SAMPLE_RATE = 44_100

    /** Frequenzverlauf und Dauer je Art. */
    fun spec(kind: FeedbackKind): Pair<List<Float>, Int> = when (kind) {
        FeedbackKind.Tap -> listOf(1760f) to 22
        FeedbackKind.LongPress -> listOf(880f, 1320f) to 60
        FeedbackKind.Open -> listOf(660f, 990f, 1320f) to 90
    }

    /**
     * Sinus mit weichem Ein- und exponentiellem Ausklang; die Frequenzen werden
     * gleichmäßig nacheinander gespielt. Ergebnis: 16-Bit-Mono-Samples.
     */
    fun samples(freqs: List<Float>, millis: Int, rate: Int = SAMPLE_RATE): ShortArray {
        require(freqs.isNotEmpty() && millis > 0)
        val n = rate * millis / 1000
        val attack = (rate * 0.002).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val f = freqs[(i * freqs.size / n).coerceAtMost(freqs.size - 1)]
            phase += 2 * PI * f / rate
            val t = i.toDouble() / n
            val env = minOf(1.0, i.toDouble() / attack) * exp(-4.0 * t)
            out[i] = (sin(phase) * env * 0.6 * Short.MAX_VALUE).roundToInt().toShort()
        }
        return out
    }

    /** Verpackt Samples als WAV (PCM 16 Bit, mono) für SoundPool. */
    fun wav(samples: ShortArray, rate: Int = SAMPLE_RATE): ByteArray {
        val data = samples.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + data); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(rate); b.putInt(rate * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data)
        samples.forEach { b.putShort(it) }
        return b.array()
    }
}
