package space.mai.mai_launcher.domain

/** Kontakt, wie ihn Comms braucht (aus dem Adressbuch, nur gelesen). */
data class Contact(
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val numbers: List<String>,
) {
    val monogram: String get() = monogram(name)
    val primaryNumber: String? get() = numbers.firstOrNull()
}

/** Eigener Verlauf: wie oft und wann zuletzt über den Launcher angerufen/geschrieben. */
data class CommsEntry(val count: Int, val lastMillis: Long)

/**
 * Speichert nur Kontakt-Schlüssel, Anzahl und Zeitpunkt – keine Nummern, keine Namen.
 * Format je Zeile: `lookupKey<TAB>anzahl<TAB>millis`, Kopf `v1`. Unbekanntes wird übersprungen.
 */
object CommsLogCodec {
    private const val HEADER = "v1"

    fun encode(log: Map<String, CommsEntry>): String = buildString {
        append(HEADER)
        log.forEach { (key, e) ->
            if ('\t' in key || '\n' in key) return@forEach
            append('\n').append(key).append('\t').append(e.count).append('\t').append(e.lastMillis)
        }
    }

    fun decode(text: String?): Map<String, CommsEntry> {
        if (text.isNullOrBlank()) return emptyMap()
        val lines = text.lines()
        if (lines.first() != HEADER) return emptyMap()
        return lines.drop(1).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) return@mapNotNull null
            val count = p[1].toIntOrNull() ?: return@mapNotNull null
            val last = p[2].toLongOrNull() ?: return@mapNotNull null
            p[0] to CommsEntry(count, last)
        }.toMap()
    }

    fun record(log: Map<String, CommsEntry>, key: String, now: Long): Map<String, CommsEntry> {
        val old = log[key]
        return log + (key to CommsEntry((old?.count ?: 0) + 1, now))
    }
}

object ContactRanking {
    /** Favoriten = im Adressbuch markiert, alphabetisch, höchstens [max]. */
    fun favourites(contacts: List<Contact>, max: Int = 6): List<Contact> =
        contacts.filter { it.starred }.sortedBy { normalizeForSearch(it.name) }.take(max)

    /**
     * Häufige = eigener Verlauf; Anzahl zählt, ältere Kontakte verlieren
     * (Halbwertszeit 30 Tage). Favoriten tauchen hier nicht doppelt auf.
     */
    fun frequent(contacts: List<Contact>, log: Map<String, CommsEntry>, now: Long, max: Int = 5): List<Contact> {
        val fav = favourites(contacts).map { it.lookupKey }.toSet()
        val halfLife = 30L * 24 * 3600 * 1000
        return contacts
            .filter { it.lookupKey in log && it.lookupKey !in fav }
            .sortedByDescending { c ->
                val e = log.getValue(c.lookupKey)
                val age = (now - e.lastMillis).coerceAtLeast(0)
                e.count * Math.pow(0.5, age.toDouble() / halfLife)
            }
            .take(max)
    }

    /** Suche wie bei Apps (Anfang, Wort, Initialen, enthält) plus Nummer. */
    fun search(contacts: List<Contact>, query: String, log: Map<String, CommsEntry> = emptyMap()): List<Contact> {
        val q = query.trim()
        if (q.isEmpty()) return contacts
        val digits = q.filter(Char::isDigit)
        return contacts
            .map { c ->
                val s = AppSearch.score(c.name, q).let { if (it == 20) 0 else it }
                val n = if (digits.length >= 3 && c.numbers.any { it.filter(Char::isDigit).contains(digits) }) 40 else 0
                c to maxOf(s, n)
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Contact, Int>> { it.second }
                    .thenByDescending { log[it.first.lookupKey]?.count ?: 0 }
                    .thenBy { normalizeForSearch(it.first.name) },
            )
            .map { it.first }
    }
}

/** Verb + Name in der Befehlszeile: „call sam“, „anrufen sam“, „text alex“. */
object ContactVerb {
    enum class Kind { Call, Text }

    private val words = mapOf(
        "call" to Kind.Call, "anrufen" to Kind.Call, "ruf" to Kind.Call, "anruf" to Kind.Call, "tel" to Kind.Call,
        "text" to Kind.Text, "sms" to Kind.Text, "schreib" to Kind.Text, "message" to Kind.Text, "nachricht" to Kind.Text,
    )

    /** Liefert Verb und Rest-Name, oder null ohne Verb. */
    fun parse(query: String): Pair<Kind, String>? {
        val parts = query.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null
        val kind = words[parts[0].lowercase()] ?: return null
        val rest = parts[1].removePrefix("an ").trim()
        return if (rest.isEmpty()) null else kind to rest
    }
}

/** Wähltastatur: nur Ziffern, *, #, + (führend); Länge begrenzt. */
object Keypad {
    fun press(current: String, key: Char): String {
        if (current.length >= 24) return current
        return when {
            key.isDigit() || key == '*' || key == '#' -> current + key
            key == '+' && current.isEmpty() -> "+"
            else -> current
        }
    }

    fun backspace(current: String): String = current.dropLast(1)

    /** Lesbare Gruppen zu je drei/vier Ziffern, nur zur Anzeige. */
    fun pretty(number: String): String {
        val plus = number.startsWith("+")
        val body = number.removePrefix("+")
        val groups = body.chunked(if (body.length > 7) 4 else 3)
        return (if (plus) "+" else "") + groups.joinToString(" ")
    }
}
