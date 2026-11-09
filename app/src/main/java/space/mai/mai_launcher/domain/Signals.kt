package space.mai.mai_launcher.domain

/** Eine Benachrichtigung, wie die Übersicht sie braucht (nur im Speicher, nie gesichert). */
data class SignalItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    /** Meist Absender oder Betreff. */
    val title: String?,
    val text: String?,
    val postedAt: Long,
    /** Von der App selbst gemeldete Anzahl (z. B. „5 neue Nachrichten“), sonst 0. */
    val number: Int = 0,
)

/** Eine Zeile der Übersicht: je App zusammengefasst. */
data class SignalGroup(
    val packageName: String,
    val appLabel: String,
    val count: Int,
    val latestTitle: String?,
    val latestText: String?,
    val latestAt: Long,
    /** Schlüssel der neuesten Benachrichtigung (zum Öffnen). */
    val latestKey: String,
)

object SignalDigest {
    /**
     * Fasst je App zusammen, neueste App zuerst. Anzahl = Summe der gemeldeten
     * Zahlen, mindestens eine je Benachrichtigung. Ohne [showText] bleiben Titel
     * und Text leer – auf dem Startbildschirm stehen dann nur App und Anzahl.
     */
    fun group(items: Collection<SignalItem>, showText: Boolean, max: Int = 5): List<SignalGroup> =
        items.groupBy { it.packageName }
            .map { (pkg, list) ->
                val latest = list.maxBy { it.postedAt }
                SignalGroup(
                    packageName = pkg,
                    appLabel = latest.appLabel,
                    count = list.sumOf { maxOf(1, it.number) },
                    latestTitle = if (showText) latest.title?.takeIf { it.isNotBlank() } else null,
                    latestText = if (showText) latest.text?.takeIf { it.isNotBlank() } else null,
                    latestAt = latest.postedAt,
                    latestKey = latest.key,
                )
            }
            .sortedByDescending { it.latestAt }
            .take(max)

    /** Zähler je Paket für Dock und Index (Kennzeichen „9+“ ab zehn). */
    fun badges(items: Collection<SignalItem>): Map<String, Int> =
        items.groupBy { it.packageName }.mapValues { (_, l) -> l.sumOf { maxOf(1, it.number) } }

    fun badgeText(count: Int): String? = when {
        count <= 0 -> null
        count > 9 -> "9+"
        else -> count.toString()
    }
}
