package space.mai.mai_launcher.domain

/**
 * Stabiler Schlüssel einer startbaren App: Paket, Activity und Profil
 * (Seriennummer des Nutzerprofils, 0 = Hauptprofil). Wird gespeichert
 * (Dock, Startverlauf) – Format "paket/klasse#profil" nie ändern.
 */
data class AppKey(val packageName: String, val className: String, val profile: Long = 0) {
    fun encode(): String = "$packageName/$className#$profile"

    companion object {
        /** Tolerant lesen: Unbekanntes oder Kaputtes ergibt null. */
        fun decode(raw: String?): AppKey? {
            if (raw.isNullOrBlank()) return null
            val hash = raw.lastIndexOf('#')
            val body = if (hash >= 0) raw.substring(0, hash) else raw
            val profile = if (hash >= 0) raw.substring(hash + 1).toLongOrNull() ?: return null else 0L
            val slash = body.indexOf('/')
            if (slash <= 0 || slash == body.length - 1) return null
            return AppKey(body.substring(0, slash), body.substring(slash + 1), profile)
        }
    }
}

/** Reine Beschreibung einer App für Suche, Liste und Vorhersage. */
data class AppInfo(
    val key: AppKey,
    val label: String,
    val isWork: Boolean = false,
)
