package space.mai.mai_launcher.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.StateFlow
import space.mai.mai_launcher.design.Feedback
import space.mai.mai_launcher.domain.FeedbackKind
import space.mai.mai_launcher.domain.HapticLevel
import space.mai.mai_launcher.domain.SoundLevel
import space.mai.mai_launcher.domain.ToneSynth
import java.io.File

/**
 * Spielt Vibration und Töne nach den Einstellungen. Töne nur, wenn das Telefon
 * auf „Klingeln“ steht (nicht bei Vibration/Stumm), über den System-Kanal für
 * Bedienungstöne – die Systemlautstärke gilt also zusätzlich.
 */
class FeedbackPlayer(
    private val context: Context,
    private val haptics: StateFlow<HapticLevel>,
    private val sounds: StateFlow<SoundLevel>,
) : Feedback {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }.getOrNull()

    private var pool: SoundPool? = null
    private val ids = HashMap<FeedbackKind, Int>()

    override fun tap() = play(FeedbackKind.Tap)
    override fun longPress() = play(FeedbackKind.LongPress)
    override fun open() = play(FeedbackKind.Open)
    override fun tick() = vibrate(FeedbackKind.Tap, haptics.value)

    fun play(kind: FeedbackKind) {
        vibrate(kind, haptics.value)
        sound(kind, sounds.value)
    }

    private fun vibrate(kind: FeedbackKind, level: HapticLevel) {
        val v = vibrator ?: return
        if (level == HapticLevel.Off || !v.hasVibrator()) return
        val effect = when (kind) {
            FeedbackKind.Tap -> if (level == HapticLevel.Strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
            FeedbackKind.LongPress -> if (level == HapticLevel.Strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
            FeedbackKind.Open -> VibrationEffect.EFFECT_TICK
        }
        runCatching { v.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    private fun sound(kind: FeedbackKind, level: SoundLevel) {
        if (level == SoundLevel.Off) return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val p = pool ?: load() ?: return
        val id = ids[kind] ?: return
        p.play(id, level.gain, level.gain, 1, 0, 1f)
    }

    /** Töne einmalig erzeugen (Cache-Ordner) und in den SoundPool laden. */
    private fun load(): SoundPool? = runCatching {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        val dir = File(context.cacheDir, "tones").apply { mkdirs() }
        for (kind in FeedbackKind.entries) {
            val (freqs, ms) = ToneSynth.spec(kind)
            val file = File(dir, "${kind.name.lowercase()}_v1.wav")
            if (!file.exists()) file.writeBytes(ToneSynth.wav(ToneSynth.samples(freqs, ms)))
            ids[kind] = p.load(file.path, 1)
        }
        pool = p
        p
    }.getOrNull()

    fun release() {
        pool?.release()
        pool = null
        ids.clear()
    }
}
