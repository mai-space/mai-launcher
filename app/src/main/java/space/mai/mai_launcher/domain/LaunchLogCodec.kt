package space.mai.mai_launcher.domain

/**
 * Textformat des Startverlaufs (vor dem Verschlüsseln), eine Zeile je Start:
 * "v1" als Kopfzeile, danach "<epochMillis>\t<AppKey>". Nur erweitern;
 * unbekannte oder kaputte Zeilen werden beim Lesen übersprungen.
 */
object LaunchLogCodec {
    private const val HEADER = "v1"
    const val MAX_EVENTS = 3000

    fun encode(events: List<LaunchEvent>): String = buildString {
        append(HEADER).append('\n')
        for (e in events.takeLast(MAX_EVENTS)) {
            append(e.epochMillis).append('\t').append(e.key.encode()).append('\n')
        }
    }

    fun decode(text: String): List<LaunchEvent> {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext() || lines.next().trim() != HEADER) return emptyList()
        val out = ArrayList<LaunchEvent>()
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val millis = line.substring(0, tab).toLongOrNull() ?: continue
            val key = AppKey.decode(line.substring(tab + 1).trim()) ?: continue
            out.add(LaunchEvent(key, millis))
        }
        return out.takeLast(MAX_EVENTS)
    }
}
