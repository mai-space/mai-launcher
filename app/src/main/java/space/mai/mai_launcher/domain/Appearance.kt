package space.mai.mai_launcher.domain

/**
 * Grundfarbe des Launchers. Gespeichert wird der Index – nur am Ende erweitern.
 */
enum class AccentChoice {
    Ion, Signal, Amber, Violet, White;

    fun next(): AccentChoice = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(index: Int): AccentChoice = entries.getOrElse(index) { Ion }
    }
}

/** Darstellung: Grundfarbe und Komfort-Modi (hoher Kontrast, weniger Bewegung, große Ansicht). */
data class Appearance(
    val accent: AccentChoice = AccentChoice.Ion,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val large: Boolean = false,
)

/** Farbe je Bereich: Deck/Index, Comms, Media. */
data class AreaAccents(val deck: AccentChoice, val comms: AccentChoice, val media: AccentChoice)

object AppearanceRules {
    /**
     * Die Bereichsfarben folgen der Grundfarbe, bleiben aber unterscheidbar:
     * Comms ist Signal, Media ist Amber – außer die Grundfarbe ist schon
     * diese Farbe, dann tauscht der Bereich auf Ion.
     */
    fun areas(base: AccentChoice): AreaAccents = AreaAccents(
        deck = base,
        comms = if (base == AccentChoice.Signal) AccentChoice.Ion else AccentChoice.Signal,
        media = if (base == AccentChoice.Amber) AccentChoice.Ion else AccentChoice.Amber,
    )

    /** Weniger Bewegung gilt auch, wenn Android die Animationen abgeschaltet hat. */
    fun reduceMotion(pref: Boolean, systemAnimatorScale: Float): Boolean = pref || systemAnimatorScale == 0f

    /** Schriftvergrößerung der großen Ansicht. */
    const val LARGE_FONT_SCALE = 1.15f
}
