package space.mai.mai_launcher.domain

import java.text.Collator
import java.util.Locale

/** Ein Abschnitt der App-Liste: Buchstabe und seine Apps. */
data class AlphaSection(val letter: Char, val apps: List<AppInfo>)

object AlphaIndex {
    /** Sortiert sprachgerecht (Collator) und gruppiert nach Anfangsbuchstabe; '#' am Ende. */
    fun sections(apps: List<AppInfo>, locale: Locale = Locale.getDefault()): List<AlphaSection> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val sorted = apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
        val groups = LinkedHashMap<Char, MutableList<AppInfo>>()
        for (app in sorted) groups.getOrPut(indexLetter(app.label)) { mutableListOf() }.add(app)
        val letters = groups.keys.sortedWith(compareBy<Char> { it == '#' }.thenBy { it })
        return letters.map { AlphaSection(it, groups.getValue(it)) }
    }
}
