package space.mai.mai_launcher.domain

/**
 * Filtert Apps nach Eingabe. Reihenfolge der Treffer:
 * Name beginnt mit Eingabe > ein Wort beginnt damit > Initialen > enthält > Buchstabenfolge (ab 3 Zeichen).
 * Bei Gleichstand gewinnt die häufiger gestartete App, dann das Alphabet.
 */
object AppSearch {
    private const val PREFIX = 100
    private const val WORD_PREFIX = 80
    private const val INITIALS = 70
    private const val CONTAINS = 50
    private const val SUBSEQUENCE = 20

    fun score(label: String, query: String): Int {
        val q = normalizeForSearch(query).trim()
        if (q.isEmpty()) return 0
        val name = normalizeForSearch(label)
        if (name.startsWith(q)) return PREFIX
        val words = name.split(Regex("[\\s\\-_.]+")).filter { it.isNotEmpty() }
        if (words.drop(1).any { it.startsWith(q) }) return WORD_PREFIX
        val compactQuery = q.replace(" ", "")
        if (compactQuery.length >= 2 && words.size >= 2) {
            val initials = words.joinToString("") { it.take(1) }
            if (initials.startsWith(compactQuery)) return INITIALS
        }
        if (name.contains(q)) return CONTAINS
        if (isSubsequence(compactQuery, name.replace(" ", ""))) return SUBSEQUENCE
        return 0
    }

    /** Eigener Suchname schlägt den App-Namen knapp (z. B. „bank“ → Sparkasse). */
    private const val ALIAS_BONUS = 5

    fun filter(
        apps: List<AppInfo>,
        query: String,
        launchCounts: Map<AppKey, Int> = emptyMap(),
        aliases: Map<AppKey, String> = emptyMap(),
    ): List<AppInfo> {
        if (query.isBlank()) return apps
        return apps
            .map { app ->
                val alias = aliases[app.key]?.let { a -> score(a, query).takeIf { it > 0 }?.plus(ALIAS_BONUS) } ?: 0
                app to maxOf(score(app.label, query), alias)
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Int>> { it.second }
                    .thenByDescending { launchCounts[it.first.key] ?: 0 }
                    .thenBy { normalizeForSearch(it.first.label) },
            )
            .map { it.first }
    }

    private fun isSubsequence(needle: String, hay: String): Boolean {
        // Erst ab drei Zeichen, sonst trifft fast alles.
        if (needle.length < 3) return false
        var i = 0
        for (ch in hay) {
            if (ch == needle[i]) {
                i++
                if (i == needle.length) return true
            }
        }
        return false
    }
}
