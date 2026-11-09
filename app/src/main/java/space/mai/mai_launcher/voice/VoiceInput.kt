package space.mai.mai_launcher.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spracheingabe für die Befehlszeile. Bevorzugt die Erkennung **auf dem Gerät**
 * (Android 12+, braucht Mikrofon-Freigabe); der Launcher selbst sendet nichts.
 * Ohne Geräte-Erkennung übernimmt der Sprach-Dialog des Systems (bevorzugt offline).
 */
class VoiceInput(private val context: Context) {
    enum class Mode { OnDevice, SystemDialog, None }

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun mode(): Mode = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false) -> Mode.OnDevice
        systemIntent().resolveActivity(context.packageManager) != null -> Mode.SystemDialog
        else -> Mode.None
    }

    fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Dialog des Systems (Fallback). */
    fun systemIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** Auf dem Gerät zuhören; [onText] bekommt Zwischen- und Endergebnisse. */
    fun listen(onText: (String, Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !hasMic()) return
        stop()
        val r = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _listening.value = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) = stop()
            override fun onPartialResults(partialResults: Bundle?) {
                first(partialResults)?.let { onText(it, false) }
            }
            override fun onResults(results: Bundle?) {
                first(results)?.let { onText(it, true) }
                stop()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        runCatching { r.startListening(intent); _listening.value = true }.onFailure { stop() }
    }

    fun stop() {
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        _listening.value = false
    }

    private fun first(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
