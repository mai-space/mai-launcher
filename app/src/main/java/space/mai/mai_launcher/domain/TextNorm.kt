package space.mai.mai_launcher.domain

import java.text.Normalizer
import java.util.Locale

/** Text für Vergleiche vereinheitlichen: klein, ohne Akzente (Ä → a, é → e). */
fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val sb = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
        sb.append(ch)
    }
    return sb.toString().lowercase(Locale.ROOT).replace('ß', 's')
}

/** Anfangsbuchstabe für die A–Z-Leiste; alles außer A–Z landet unter '#'. */
fun indexLetter(label: String): Char {
    val first = normalizeForSearch(label.trim()).firstOrNull() ?: return '#'
    val up = first.uppercaseChar()
    return if (up in 'A'..'Z') up else '#'
}

/** Zwei Buchstaben als Ersatzsymbol ("Ca" für Calendar, "RD" für "Ruf durch"). */
fun monogram(label: String): String {
    val words = label.trim().split(Regex("[\\s\\-_.]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    return if (words.size >= 2) {
        (words[0].take(1) + words[1].take(1)).uppercase()
    } else {
        val w = words[0]
        w.take(1).uppercase() + w.drop(1).take(1).lowercase()
    }
}
