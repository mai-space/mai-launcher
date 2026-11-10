package space.mai.mai_launcher.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import space.mai.mai_launcher.domain.AccentChoice

/**
 * Komfort-Modi aus dem Konzept (Abschnitt 1, „Accessibility and comfort modes“).
 * Bewusst als globaler Snapshot-Zustand: Farb-Tokens und Kontur-Modifier lesen
 * ihn direkt, so folgen alle Bildschirme ohne weitere Parameter.
 */
@Immutable
data class Comfort(
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val large: Boolean = false,
)

object ComfortState {
    var current by mutableStateOf(Comfort())

    /** Zähler für die Einschalt-Animation (Rahmen zeichnet sich, Panels blenden ein). */
    var powerOn by mutableIntStateOf(0)

    /** Zuletzt abgespielter Zählerstand (kein Snapshot-Zustand, nur Merker). */
    var powerOnPlayed = 0
}

/** Animation, die „weniger Bewegung“ beachtet: dann sofort. */
fun <T> motion(durationMs: Int, delayMs: Int = 0): AnimationSpec<T> =
    if (ComfortState.current.reduceMotion) snap() else tween(durationMs, delayMs)

/** Farbwerte der wählbaren Grundfarben. */
fun AccentChoice.toAccent(): Accent = when (this) {
    AccentChoice.Ion -> Accents.Ion
    AccentChoice.Signal -> Accents.Signal
    AccentChoice.Amber -> Accents.Amber
    AccentChoice.Violet -> Accents.Violet
    AccentChoice.White -> Accents.White
}

/** Farbmuster für die Auswahl in den Einstellungen. */
val AccentChoice.swatch: Color get() = toAccent().main

/** Gesamtdauer der Einschalt-Animation: Rahmen 180 ms, Panels je 40 ms versetzt. */
const val POWER_ON_MS = 240
private const val FRAME_DRAW_MS = 180f
private const val PANEL_STEP_MS = 40
private const val PANEL_FADE_MS = 80f

/** Fortschritt der Einschalt-Animation (0..1), in der Zeichenphase gelesen. 1 = fertig. */
val LocalPowerOn = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * Spielt die Einschalt-Animation einmal je [ComfortState.powerOn]-Zähler ab
 * (Home-Taste, Entsperren). Bei „weniger Bewegung“ nie.
 */
@Composable
fun rememberPowerOn(): () -> Float {
    val count = ComfortState.powerOn
    val pending = count != ComfortState.powerOnPlayed && !ComfortState.current.reduceMotion
    val progress = remember { Animatable(if (pending) 0f else 1f) }
    LaunchedEffect(count) {
        val play = count != ComfortState.powerOnPlayed && !ComfortState.current.reduceMotion
        ComfortState.powerOnPlayed = count
        if (play) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(POWER_ON_MS, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    return remember(progress) { { progress.value } }
}

/** Anteil der gezeichneten Rahmenkontur zum Fortschritt [p]. */
fun powerOnFrame(p: Float): Float = (p * POWER_ON_MS / FRAME_DRAW_MS).coerceIn(0f, 1f)

/** Deckkraft des Panels Nummer [order] zum Fortschritt [p]. */
fun powerOnPanel(p: Float, order: Int): Float {
    if (p >= 1f) return 1f
    val ms = p * POWER_ON_MS - PANEL_STEP_MS * order.coerceIn(0, 4)
    return (ms / PANEL_FADE_MS).coerceIn(0f, 1f)
}

/** Blendet ein Panel bei der Einschalt-Animation versetzt ein. */
@Composable
fun PowerOnFade(order: Int, content: @Composable () -> Unit) {
    val progress = LocalPowerOn.current
    Box(Modifier.graphicsLayer { alpha = powerOnPanel(progress(), order) }) { content() }
}

/** Zahl, die zum neuen Wert hochzählt (Monospace hält sie an Ort und Stelle). */
@Composable
fun rememberTicking(target: Int): Int = animateIntAsState(target, motion(400), label = "tick").value
