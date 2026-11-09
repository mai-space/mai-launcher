package space.mai.mai_launcher.domain

/** Tonmodus des Telefons (entspricht AudioManager.RINGER_MODE_*). */
enum class SoundMode(val ringerMode: Int) {
    Mute(0), Vibrate(1), Ring(2);

    /** Reihenfolge beim Antippen: Klingeln → Vibrieren → Stumm → Klingeln. */
    fun next(): SoundMode = when (this) {
        Ring -> Vibrate
        Vibrate -> Mute
        Mute -> Ring
    }

    companion object {
        fun of(ringerMode: Int): SoundMode = entries.firstOrNull { it.ringerMode == ringerMode } ?: Ring
    }
}
