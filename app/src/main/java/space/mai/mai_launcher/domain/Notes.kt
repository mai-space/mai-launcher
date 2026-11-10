package space.mai.mai_launcher.domain

/** Kurze Notiz aus der Befehlszeile („note Milch kaufen“). */
data class Note(val id: Long, val text: String, val createdAt: Long)

/**
 * Notizen als Text (verschlüsselt gespeichert, siehe `data/NotesStore`).
 * Format „notes v1“: eine Zeile je Notiz `id<TAB>erstellt<TAB>text`, im
 * Text sind `\`, Tab und Zeilenumbruch maskiert. Nur erweitern; unbekannte
 * Zusatzfelder und kaputte Zeilen werden übersprungen.
 */
object NotesCodec {
    const val HEADER = "notes v1"
    const val MAX_NOTES = 200
    const val MAX_LENGTH = 500

    fun encode(notes: List<Note>): String = buildString {
        appendLine(HEADER)
        notes.takeLast(MAX_NOTES).forEach { n -> append(n.id).append('\t').append(n.createdAt).append('\t').append(escape(n.text)).append('\n') }
    }

    fun decode(text: String): List<Note> {
        val lines = text.lines()
        if (lines.firstOrNull()?.startsWith("notes ") != true) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val created = parts[1].toLongOrNull() ?: return@mapNotNull null
            clean(unescape(parts[2]))?.let { Note(id, it, created) }
        }.takeLast(MAX_NOTES)
    }

    /** Leer = keine Notiz; Länge begrenzt. */
    fun clean(raw: String): String? = raw.trim().take(MAX_LENGTH).ifBlank { null }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c); i++
            }
        }
    }
}
