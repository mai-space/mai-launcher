package space.mai.mai_launcher.domain

/** Reiter im Index: alle Apps, Arbeitsprofil, ausgeblendete. */
enum class IndexTab { All, Work, Hidden }

/** App-Verknüpfung (Android-Shortcut) aus dem Langdruck-Menü. */
data class AppShortcut(val id: String, val packageName: String, val label: String, val profile: Long)

/**
 * Aufteilung der App-Liste: Ausgeblendete erscheinen nur im Reiter
 * „Ausgeblendet“ (nicht in Suche, Vorschlägen und A–Z), Arbeitsprofil-Apps
 * im eigenen Reiter, aber in der Suche.
 */
object AppCatalog {
    fun tabs(apps: List<AppInfo>, hidden: Set<AppKey>): List<IndexTab> = buildList {
        add(IndexTab.All)
        if (apps.any { it.isWork && it.key !in hidden }) add(IndexTab.Work)
        if (apps.any { it.key in hidden }) add(IndexTab.Hidden)
    }

    fun inTab(apps: List<AppInfo>, hidden: Set<AppKey>, tab: IndexTab): List<AppInfo> = when (tab) {
        IndexTab.All -> apps.filter { !it.isWork && it.key !in hidden }
        IndexTab.Work -> apps.filter { it.isWork && it.key !in hidden }
        IndexTab.Hidden -> apps.filter { it.key in hidden }
    }

    /** Für Suche und Vorschläge: alles außer Ausgeblendetem. */
    fun searchable(apps: List<AppInfo>, hidden: Set<AppKey>): List<AppInfo> = apps.filter { it.key !in hidden }
}

/**
 * Eigene Suchnamen („Aliase“) je App. Gespeichert als Menge von Zeilen
 * `appkey<TAB>alias` – tolerant lesen, Kaputtes überspringen.
 */
object AliasCodec {
    private const val SEP = '\t'
    const val MAX_LENGTH = 40

    fun encode(aliases: Map<AppKey, String>): Set<String> =
        aliases.mapNotNullTo(HashSet()) { (k, a) -> clean(a)?.let { k.encode() + SEP + it } }

    fun decode(lines: Collection<String>): Map<AppKey, String> = lines.mapNotNull { line ->
        val i = line.indexOf(SEP)
        if (i <= 0) return@mapNotNull null
        val key = AppKey.decode(line.substring(0, i)) ?: return@mapNotNull null
        clean(line.substring(i + 1))?.let { key to it }
    }.toMap()

    /** Leer = kein Alias; Zeilenumbrüche/Tabs raus, Länge begrenzt. */
    fun clean(raw: String): String? =
        raw.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(MAX_LENGTH).ifBlank { null }
}
