package space.mai.mai_launcher.domain

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Eigene Dashboards („Boards“): zusätzliche Seiten neben Comms, Deck und Media,
 * z. B. „Banking“ oder „Fitness“. Ein Board sammelt Apps (Sechsecke), eingebettete
 * Android-Widgets und – falls passend – den Stand einer Mai-App. Alles lokal;
 * gespeichert werden nur App-Schlüssel, Widget-IDs, Name, Vorlage und Farbe.
 */
enum class BoardKind {
    // Gespeichert wird der Name (nicht der Index) – trotzdem nur ergänzen, nie umbenennen.
    Custom, Finance, Fitness, Home, Health, Travel, Work;

    companion object {
        fun of(name: String?): BoardKind = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Custom

        /** Vorlagen in der Reihenfolge der Auswahl („Leer“ zuletzt). */
        val templates = listOf(Finance, Fitness, Home, Health, Travel, Work, Custom)
    }
}

data class Board(
    /** Kurze, zufällige Kennung („b1x9k2“) – nie ändern, Seitenreihenfolge hängt daran. */
    val id: String,
    /** Eigener Name; null = Name der Vorlage in der Systemsprache. */
    val title: String? = null,
    val kind: BoardKind = BoardKind.Custom,
    val accent: AccentChoice = BoardTemplates.accent(kind),
    val apps: List<AppKey> = emptyList(),
    /** Eingebettete Android-Widgets (IDs gelten nur auf diesem Gerät). */
    val widgets: List<Int> = emptyList(),
)

object BoardTemplates {
    /** Kontextfarbe je Vorlage (Rot bleibt Warnungen vorbehalten). */
    fun accent(kind: BoardKind): AccentChoice = when (kind) {
        BoardKind.Finance -> AccentChoice.Violet
        BoardKind.Fitness -> AccentChoice.Signal
        BoardKind.Home -> AccentChoice.Amber
        BoardKind.Health -> AccentChoice.White
        BoardKind.Travel, BoardKind.Work, BoardKind.Custom -> AccentChoice.Ion
    }

    /** Mai-App, deren Stand (Prognose) auf dem Board erscheint. */
    fun maiPackage(kind: BoardKind): String? = when (kind) {
        BoardKind.Finance -> MaiSummaryApps.BUDGET
        BoardKind.Fitness -> MaiSummaryApps.FITNESS
        BoardKind.Home -> MaiSummaryApps.HOUSEHOLD
        else -> null
    }

    /** Mai-Apps, die zur Vorlage gehören (kommen zuerst). */
    private fun maiApps(kind: BoardKind): List<String> = when (kind) {
        BoardKind.Finance -> listOf(MaiSummaryApps.BUDGET)
        BoardKind.Fitness -> listOf(MaiSummaryApps.FITNESS)
        BoardKind.Home -> listOf(MaiSummaryApps.HOUSEHOLD)
        BoardKind.Health -> listOf("space.mai.mai_doctor_hub")
        else -> emptyList()
    }

    /**
     * Stichwörter für den Vorschlag beim Anlegen: geprüft gegen Paketname und
     * App-Namen (klein, ohne Akzente), nur auf dem Gerät. Android kennt keine
     * Kategorie „Finanzen“ oder „Fitness“, darum diese einfache Liste.
     */
    fun keywords(kind: BoardKind): List<String> = when (kind) {
        BoardKind.Finance -> listOf(
            "bank", "sparkasse", "volksbank", "raiffeisen", "comdirect", "consorsbank", "dkb", "n26", "revolut",
            "paypal", "wallet", "finanz", "finance", "broker", "trade republic", "traderepublic", "scalable",
            "postbank", "commerzbank", "deutsche bank", "de.ing", "klarna", "vivid", "bunq", "wise",
            "banking", "budget", "geld", "money", "steuer", "tax",
        )
        BoardKind.Fitness -> listOf(
            "fitness", "fit", "workout", "training", "strava", "garmin", "komoot", "runtastic", "adidas running",
            "nike run", "polar", "suunto", "zepp", "huawei health", "samsung health", "freeletics", "gym",
            "yoga", "lauf", "running",
        )
        BoardKind.Home -> listOf(
            "household", "haushalt", "einkauf", "shopping list", "bring", "todo", "recipe", "rezept", "home",
            "smart home", "hue", "tado", "ikea", "alexa", "google home", "muell", "abfall",
        )
        BoardKind.Health -> listOf(
            "health", "gesund", "doctor", "arzt", "doctolib", "apotheke", "pharmacy", "krankenkasse", "tk-app",
            "aok", "barmer", "medikament", "medication", "clue", "sleep", "schlaf", "ecg", "ekg",
        )
        BoardKind.Travel -> listOf(
            "maps", "karten", "navigation", "db navigator", "bahn", "flix", "lufthansa", "ryanair", "booking",
            "airbnb", "uber", "bolt", "free now", "freenow", "miles", "share now", "tier", "lime", "transit",
            "weather", "wetter", "translate", "übersetz", "uebersetz",
        )
        BoardKind.Work -> listOf(
            "mail", "outlook", "gmail", "calendar", "kalender", "teams", "slack", "zoom", "meet", "office",
            "word", "excel", "powerpoint", "docs", "sheets", "drive", "onedrive", "dropbox", "notion", "trello",
            "jira", "confluence", "authenticator",
        )
        BoardKind.Custom -> emptyList()
    }

    /**
     * Passende installierte Apps für eine neue Seite: zuerst die Mai-App(s) der
     * Vorlage, dann Treffer nach Stichwort, alphabetisch. Arbeitsprofil nur,
     * wenn es die App nicht im Hauptprofil gibt.
     */
    fun suggest(kind: BoardKind, installed: List<AppInfo>, max: Int = Boards.MAX_APPS): List<AppKey> {
        if (kind == BoardKind.Custom) return emptyList()
        val words = keywords(kind).map(::normalizeForSearch)
        val mai = maiApps(kind)
        val main = installed.sortedWith(compareBy({ it.isWork }, { normalizeForSearch(it.label) }))
        val picked = LinkedHashMap<String, AppKey>()
        mai.forEach { pkg -> main.firstOrNull { it.key.packageName == pkg }?.let { picked.putIfAbsent(pkg, it.key) } }
        for (app in main) {
            if (picked.size >= max) break
            if (app.key.packageName in picked) continue
            val name = normalizeForSearch(app.label)
            val pkg = app.key.packageName.lowercase()
            if (words.any { w -> matches(name, w) || matchesPackage(pkg, w) }) picked[app.key.packageName] = app.key
        }
        return picked.values.take(max)
    }

    /** Kurze Wörter (≤ 3 Zeichen) nur als ganzes Wort, sonst als Teil. */
    private fun matches(name: String, word: String): Boolean =
        if (word.length <= 3) name.split(' ', '-', '.', '_').any { it == word } else name.contains(word)

    /** Paketname: kurze Wörter nur am Anfang eines Abschnitts („fit“ → „…apps.fitness“). */
    private fun matchesPackage(pkg: String, word: String): Boolean {
        val w = word.replace(" ", "")
        return if (w.length <= 3) pkg.split('.', '_').any { it.startsWith(w) } else pkg.contains(w)
    }
}

object Boards {
    /** Obergrenzen: ein Board soll eine Seite bleiben, keine zweite App-Liste. */
    const val MAX_BOARDS = 8
    const val MAX_APPS = 12

    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    /** Neue Kennung, die es noch nicht gibt. */
    fun newId(existing: Set<String>, random: java.util.Random = java.util.Random()): String {
        while (true) {
            val id = "b" + (1..5).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            if (id !in existing) return id
        }
    }

    fun create(kind: BoardKind, existing: Set<String>, installed: List<AppInfo>, random: java.util.Random = java.util.Random()): Board =
        Board(id = newId(existing, random), kind = kind, apps = BoardTemplates.suggest(kind, installed))

    fun addApp(board: Board, key: AppKey): Board =
        if (key in board.apps || board.apps.size >= MAX_APPS) board else board.copy(apps = board.apps + key)

    fun removeApp(board: Board, key: AppKey): Board = board.copy(apps = board.apps - key)

    fun moveApp(board: Board, key: AppKey, delta: Int): Board = board.copy(apps = moved(board.apps, key, delta))

    fun moveWidget(board: Board, id: Int, delta: Int): Board = board.copy(widgets = moved(board.widgets, id, delta))

    /** Eigener Name: getrimmt, höchstens 24 Zeichen; leer = Name der Vorlage. */
    fun cleanTitle(raw: String?): String? = raw?.trim()?.replace(Regex("\\s+"), " ")?.take(24)?.ifEmpty { null }

    fun <T> moved(list: List<T>, item: T, delta: Int): List<T> {
        val i = list.indexOf(item)
        val j = i + delta
        if (i < 0 || j !in list.indices) return list
        return list.toMutableList().apply { removeAt(i); add(j, item) }
    }
}

/**
 * Speicherformat der Boards (Einstellung `boards`), Kopf „boards v1“:
 * je Zeile `schlüssel=wert` (URL-kodiert); `board=<id>` beginnt ein Board,
 * die folgenden Zeilen gehören dazu. Nur erweitern; unbekannte Schlüssel
 * und kaputte Zeilen werden übersprungen.
 */
object BoardCodec {
    const val HEADER = "boards v1"

    fun encode(boards: List<Board>): String = buildString {
        appendLine(HEADER)
        boards.forEach { b ->
            line("board", b.id)
            b.title?.let { line("title", it) }
            line("kind", b.kind.name.lowercase())
            line("accent", b.accent.name.lowercase())
            b.apps.forEach { line("app", it.encode()) }
            b.widgets.forEach { line("widget", it.toString()) }
        }
    }

    private fun StringBuilder.line(key: String, value: String) {
        append(key).append('=').append(URLEncoder.encode(value, "UTF-8")).append('\n')
    }

    fun decode(raw: String?): List<Board> {
        val lines = raw.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull()?.startsWith("boards ") != true) return emptyList()
        val out = mutableListOf<Board>()
        var cur: Board? = null
        var accentSet = false
        fun flush() { cur?.let { b -> if (out.none { it.id == b.id }) out += b }; cur = null }
        for (l in lines.drop(1)) {
            val eq = l.indexOf('=')
            if (eq <= 0) continue
            val key = l.substring(0, eq)
            val value = runCatching { URLDecoder.decode(l.substring(eq + 1), "UTF-8") }.getOrNull() ?: continue
            if (key == "board") {
                flush()
                if (value.isNotBlank()) { cur = Board(id = value.trim()); accentSet = false }
                continue
            }
            val b = cur ?: continue
            cur = when (key) {
                "title" -> b.copy(title = Boards.cleanTitle(value))
                "kind" -> BoardKind.of(value).let { k -> b.copy(kind = k, accent = if (accentSet) b.accent else BoardTemplates.accent(k)) }
                "accent" -> AccentChoice.entries.firstOrNull { it.name.equals(value, true) }
                    ?.let { accentSet = true; b.copy(accent = it) } ?: b
                "app" -> AppKey.decode(value)?.let { if (it in b.apps) b else b.copy(apps = b.apps + it) } ?: b
                "widget" -> value.toIntOrNull()?.takeIf { it > 0 && it !in b.widgets }?.let { b.copy(widgets = b.widgets + it) } ?: b
                else -> b
            }
        }
        flush()
        return out
    }
}

/** Eine Seite, zwischen denen man auf dem Startbildschirm wischt. */
sealed interface PageRef {
    data object Comms : PageRef
    data object Deck : PageRef
    data object Media : PageRef
    data class OfBoard(val id: String) : PageRef
}

/**
 * Reihenfolge der Seiten (Einstellung `page_order`, z. B. „comms,deck,media,board:b1x9k“).
 * Das Deck bleibt immer Startseite (Home-Taste), lässt sich aber verschieben.
 * Nach rechts wischen = Seite links davon, nach links wischen = Seite rechts davon.
 */
object PageOrder {
    val DEFAULT: List<PageRef> = listOf(PageRef.Comms, PageRef.Deck, PageRef.Media)

    fun encode(order: List<PageRef>): String = order.joinToString(",") {
        when (it) {
            PageRef.Comms -> "comms"
            PageRef.Deck -> "deck"
            PageRef.Media -> "media"
            is PageRef.OfBoard -> "board:" + it.id
        }
    }

    /**
     * Liest tolerant und bringt die Liste in einen gültigen Zustand: Unbekanntes
     * und Doppeltes fällt weg, Boards ohne Daten auch; fehlende eingebaute
     * Seiten kommen an ihre Standardstelle, fehlende Boards ans Ende.
     */
    fun decode(raw: String?, boardIds: List<String>): List<PageRef> {
        val parsed = raw.orEmpty().split(',').mapNotNull { token ->
            val t = token.trim()
            when {
                t == "comms" -> PageRef.Comms
                t == "deck" -> PageRef.Deck
                t == "media" -> PageRef.Media
                t.startsWith("board:") -> PageRef.OfBoard(t.removePrefix("board:"))
                else -> null
            }
        }
        return normalize(parsed, boardIds)
    }

    fun normalize(order: List<PageRef>, boardIds: List<String>): List<PageRef> {
        val ids = boardIds.toSet()
        val out = order.distinct().filter { it !is PageRef.OfBoard || it.id in ids }.toMutableList()
        if (PageRef.Comms !in out) out.add(0, PageRef.Comms)
        if (PageRef.Deck !in out) out.add(out.indexOf(PageRef.Comms) + 1, PageRef.Deck)
        if (PageRef.Media !in out) out.add(out.indexOf(PageRef.Deck) + 1, PageRef.Media)
        boardIds.forEach { id -> PageRef.OfBoard(id).let { if (it !in out) out += it } }
        return out
    }

    /** Nachbarseite: [direction] −1 = links (nach rechts wischen), +1 = rechts. */
    fun neighbour(order: List<PageRef>, current: PageRef, direction: Int): PageRef? {
        val i = order.indexOf(current)
        if (i < 0) return null
        return order.getOrNull(i + direction.coerceIn(-1, 1))
    }

    fun move(order: List<PageRef>, page: PageRef, delta: Int): List<PageRef> = Boards.moved(order, page, delta)
}
