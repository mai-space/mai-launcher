package space.mai.mai_launcher.design

import androidx.compose.runtime.staticCompositionLocalOf

/** Rückmeldung bei Interaktionen (Vibration, Ton) – einstellbar, in Tests stumm. */
interface Feedback {
    fun tap() {}
    fun longPress() {}
    fun open() {}
    /** Nur ein leichtes Ticken (z. B. A–Z-Leiste), nie ein Ton. */
    fun tick() {}
}

val LocalFeedback = staticCompositionLocalOf<Feedback> { object : Feedback {} }
