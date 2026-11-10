package space.mai.mai_launcher.domain

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Sicherung von Layout und Einstellungen als lesbare Textdatei – lokal, vom
 * Nutzer gespeichert. Bewusst NICHT enthalten: Startverlauf, Comms-Verlauf,
 * Kontakte, Benachrichtigungen. Format „mai-launcher-layout v1“: eine Zeile
 * je Eintrag `schlüssel=wert` (URL-kodiert). Nur erweitern; unbekannte
 * Schlüssel und kaputte Zeilen werden beim Lesen übersprungen.
 */
data class LayoutBackup(
    val dock: List<AppKey?> = emptyList(),
    val destinations: List<String> = emptyList(),
    val feeds: List<String> = emptyList(),
    /** Widget-Anbieter in Deck-Reihenfolge (Widget-IDs gelten nur auf einem Gerät). */
    val widgets: List<ProviderRef> = emptyList(),
    val signalsShowText: Boolean? = null,
    val haptics: HapticLevel? = null,
    val sounds: SoundLevel? = null,
    val accent: AccentChoice? = null,
    val highContrast: Boolean? = null,
    val reduceMotion: Boolean? = null,
    val large: Boolean? = null,
    val hidden: Set<AppKey> = emptySet(),
    val likelyHidden: Set<AppKey> = emptySet(),
    val aliases: Map<AppKey, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = dock.all { it == null } && destinations.all { it.isBlank() } && feeds.isEmpty() &&
            widgets.isEmpty() && signalsShowText == null && haptics == null && sounds == null &&
            accent == null && highContrast == null && reduceMotion == null && large == null &&
            hidden.isEmpty() && likelyHidden.isEmpty() && aliases.isEmpty()

    /** Darstellung aus der Sicherung über die aktuelle legen (fehlende Werte bleiben). */
    fun appearanceOver(current: Appearance): Appearance = current.copy(
        accent = accent ?: current.accent,
        highContrast = highContrast ?: current.highContrast,
        reduceMotion = reduceMotion ?: current.reduceMotion,
        large = large ?: current.large,
    )
}

object LayoutBackupCodec {
    const val HEADER = "mai-launcher-layout v1"

    fun encode(b: LayoutBackup): String = buildString {
        appendLine(HEADER)
        b.dock.forEachIndexed { i, k -> if (k != null) line("dock.$i", k.encode()) }
        b.destinations.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { line("dest.home", it) }
        b.destinations.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { line("dest.work", it) }
        b.feeds.forEach { line("feed", it) }
        b.widgets.forEach { line("widget", "${it.packageName}/${it.className}") }
        b.signalsShowText?.let { line("signals_show_text", it.toString()) }
        b.haptics?.let { line("haptics", it.name.lowercase()) }
        b.sounds?.let { line("sounds", it.name.lowercase()) }
        b.accent?.let { line("accent", it.name.lowercase()) }
        b.highContrast?.let { line("comfort_contrast", it.toString()) }
        b.reduceMotion?.let { line("comfort_motion", it.toString()) }
        b.large?.let { line("comfort_large", it.toString()) }
        b.hidden.map { it.encode() }.sorted().forEach { line("hidden", it) }
        b.likelyHidden.map { it.encode() }.sorted().forEach { line("likely_hidden", it) }
        AliasCodec.encode(b.aliases).sorted().forEach { line("alias", it) }
    }

    private fun StringBuilder.line(key: String, value: String) {
        append(key).append('=').append(URLEncoder.encode(value, "UTF-8")).append('\n')
    }

    /** Liest tolerant; null, wenn die Datei keine Launcher-Sicherung ist. */
    fun decode(text: String): LayoutBackup? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val head = lines.firstOrNull() ?: return null
        if (!head.startsWith("mai-launcher-layout ")) return null
        val dock = arrayOfNulls<AppKey>(DOCK_SLOTS)
        var home = ""
        var work = ""
        val feeds = mutableListOf<String>()
        val widgets = mutableListOf<ProviderRef>()
        var showText: Boolean? = null
        var haptics: HapticLevel? = null
        var sounds: SoundLevel? = null
        var accent: AccentChoice? = null
        var contrast: Boolean? = null
        var motion: Boolean? = null
        var large: Boolean? = null
        val hidden = HashSet<AppKey>()
        val likelyHidden = HashSet<AppKey>()
        val aliasLines = mutableListOf<String>()
        for (l in lines.drop(1)) {
            val eq = l.indexOf('=')
            if (eq <= 0) continue
            val key = l.substring(0, eq)
            val value = runCatching { URLDecoder.decode(l.substring(eq + 1), "UTF-8") }.getOrNull() ?: continue
            when {
                key.startsWith("dock.") -> {
                    val i = key.removePrefix("dock.").toIntOrNull() ?: continue
                    if (i in 0 until DOCK_SLOTS) dock[i] = AppKey.decode(value)
                }
                key == "dest.home" -> home = value
                key == "dest.work" -> work = value
                key == "feed" -> FeedUrls.normalize(value)?.let { if (it !in feeds) feeds += it }
                key == "widget" -> provider(value)?.let { if (it !in widgets) widgets += it }
                key == "signals_show_text" -> showText = value.toBooleanStrictOrNull()
                key == "haptics" -> haptics = HapticLevel.entries.firstOrNull { it.name.equals(value, true) }
                key == "sounds" -> sounds = SoundLevel.entries.firstOrNull { it.name.equals(value, true) }
                key == "accent" -> accent = AccentChoice.entries.firstOrNull { it.name.equals(value, true) }
                key == "comfort_contrast" -> contrast = value.toBooleanStrictOrNull()
                key == "comfort_motion" -> motion = value.toBooleanStrictOrNull()
                key == "comfort_large" -> large = value.toBooleanStrictOrNull()
                key == "hidden" -> AppKey.decode(value)?.let(hidden::add)
                key == "likely_hidden" -> AppKey.decode(value)?.let(likelyHidden::add)
                key == "alias" -> aliasLines += value
            }
        }
        return LayoutBackup(dock.toList(), listOf(home, work), feeds, widgets, showText, haptics, sounds, accent, contrast, motion, large,
            hidden, likelyHidden, AliasCodec.decode(aliasLines))
    }

    private fun provider(raw: String): ProviderRef? {
        val slash = raw.indexOf('/')
        if (slash <= 0 || slash == raw.length - 1) return null
        return ProviderRef(raw.substring(0, slash), raw.substring(slash + 1))
    }

    /**
     * Dock-Einträge auf die installierten Apps abbilden. Die Profil-Nummer ist
     * gerätespezifisch: erst exakt, dann gleiche Activity, dann gleiches Paket
     * (Hauptprofil bevorzugt). Fehlt die App, bleibt der Platz beim System-Standard.
     */
    fun resolveDock(saved: List<AppKey?>, installed: List<AppInfo>): List<AppKey?> = saved.map { k ->
        if (k == null) return@map null
        val main = installed.sortedBy { it.isWork }
        main.firstOrNull { it.key == k }?.key
            ?: main.firstOrNull { it.key.packageName == k.packageName && it.key.className == k.className }?.key
            ?: main.firstOrNull { it.key.packageName == k.packageName }?.key
    }

    /**
     * App-Schlüssel (ausgeblendet, Aliase) auf installierte Apps abbilden –
     * wie beim Dock über Paket und Activity, weil die Profil-Nummer
     * gerätespezifisch ist. Fehlende Apps entfallen.
     */
    fun resolveKeys(saved: Collection<AppKey>, installed: List<AppInfo>): Map<AppKey, AppKey> =
        saved.mapNotNull { k -> resolveDock(listOf(k), installed).firstOrNull()?.let { k to it } }.toMap()

    /** Dateiname mit Datum, z. B. „mai-launcher-layout-2026-11-09.txt“. */
    fun fileName(date: java.time.LocalDate): String = "mai-launcher-layout-$date.txt"

    private const val DOCK_SLOTS = 4
}
