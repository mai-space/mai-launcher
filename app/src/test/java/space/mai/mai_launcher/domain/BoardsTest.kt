package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardsTest {
    private val bank = AppKey("de.example.bank", "Main")
    private val pay = AppKey("com.example.pay", "Main")
    private val budget = AppKey(MaiSummaryApps.BUDGET, "MainActivity")

    private val sample = listOf(
        Board("b1abcd", title = "Geld & Co", kind = BoardKind.Finance, accent = AccentChoice.Amber, apps = listOf(budget, bank), widgets = listOf(12, 7)),
        Board("b2efgh", kind = BoardKind.Fitness),
    )

    // ------------------------------------------------------------ Speicherformat

    @Test fun codecRoundTrip() {
        val text = BoardCodec.encode(sample)
        assertTrue(text.startsWith(BoardCodec.HEADER))
        assertEquals(sample, BoardCodec.decode(text))
    }

    @Test fun codecReadsTolerantly() {
        val text = """
            boards v2
            title=ohne Board wird übersprungen
            board=b1
            kind=finance
            app=kaputt
            app=de.example.bank/Main#0
            app=de.example.bank/Main#0
            widget=abc
            widget=-3
            widget=5
            future=egal
            garbage line
            board=b1
            title=doppelt
            board=b2
            kind=unbekannt
            accent=pink
        """.trimIndent()
        val boards = BoardCodec.decode(text)
        assertEquals(2, boards.size)
        assertEquals(Board("b1", kind = BoardKind.Finance, apps = listOf(bank), widgets = listOf(5)), boards[0])
        // Unbekannte Vorlage = leer, unbekannte Farbe = Farbe der Vorlage.
        assertEquals(Board("b2", kind = BoardKind.Custom, accent = AccentChoice.Ion), boards[1])
    }

    @Test fun codecRejectsForeignText() {
        assertEquals(emptyList<Board>(), BoardCodec.decode(null))
        assertEquals(emptyList<Board>(), BoardCodec.decode("hallo"))
    }

    @Test fun explicitAccentWinsOverKind() {
        val text = "boards v1\nboard=b1\naccent=amber\nkind=fitness\n"
        assertEquals(AccentChoice.Amber, BoardCodec.decode(text).single().accent)
    }

    @Test fun kindIsStoredByName() {
        // Gespeichert wird der Name – die Reihenfolge der Enum-Werte darf sich nie auswirken.
        assertEquals(BoardKind.Health, BoardKind.of("HEALTH"))
        assertEquals(BoardKind.Custom, BoardKind.of(null))
    }

    // ------------------------------------------------------------ Bearbeiten

    @Test fun titleIsCleaned() {
        assertEquals("Mein Geld", Boards.cleanTitle("  Mein   Geld "))
        assertNull(Boards.cleanTitle("   "))
        assertEquals(24, Boards.cleanTitle("x".repeat(40))!!.length)
    }

    @Test fun addAppIgnoresDuplicatesAndLimit() {
        var b = Board("b1")
        b = Boards.addApp(b, bank)
        b = Boards.addApp(b, bank)
        assertEquals(listOf(bank), b.apps)
        repeat(20) { b = Boards.addApp(b, AppKey("p$it", "Main")) }
        assertEquals(Boards.MAX_APPS, b.apps.size)
    }

    @Test fun moveAndRemoveApps() {
        val b = Board("b1", apps = listOf(budget, bank, pay))
        assertEquals(listOf(bank, budget, pay), Boards.moveApp(b, bank, -1).apps)
        assertEquals(b, Boards.moveApp(b, budget, -1))
        assertEquals(listOf(budget, pay), Boards.removeApp(b, bank).apps)
    }

    @Test fun newIdIsUnique() {
        val random = java.util.Random(1)
        val first = Boards.newId(emptySet(), java.util.Random(1))
        val second = Boards.newId(setOf(first), random)
        assertTrue(first.startsWith("b") && first.length == 6)
        assertTrue(first != second)
    }

    // ------------------------------------------------------------ Vorlagen

    private val installed = listOf(
        AppInfo(AppKey("de.sparkasse.app", "Main"), "Sparkasse"),
        AppInfo(AppKey("com.paypal.android", "Main"), "PayPal"),
        AppInfo(AppKey("com.booking", "Main"), "Booking.com"),
        AppInfo(AppKey("com.spotify.music", "Main"), "Spotify"),
        AppInfo(AppKey(MaiSummaryApps.BUDGET, "Main"), "Mai Budget"),
        AppInfo(AppKey("com.google.android.apps.fitness", "Main"), "Fit"),
        AppInfo(AppKey("com.strava", "Main"), "Strava"),
        AppInfo(AppKey("com.example.profit", "Main"), "Profit Calculator"),
        AppInfo(AppKey("de.sparkasse.app", "Main", 10), "Sparkasse", isWork = true),
    )

    @Test fun financeSuggestsMaiAppFirstThenMatches() {
        val keys = BoardTemplates.suggest(BoardKind.Finance, installed)
        assertEquals(AppKey(MaiSummaryApps.BUDGET, "Main"), keys.first())
        assertTrue(AppKey("com.paypal.android", "Main") in keys)
        // Hauptprofil vor Arbeitsprofil, jede App nur einmal.
        assertTrue(AppKey("de.sparkasse.app", "Main") in keys)
        assertTrue(AppKey("de.sparkasse.app", "Main", 10) !in keys)
        assertTrue(AppKey("com.spotify.music", "Main") !in keys)
    }

    @Test fun shortKeywordsOnlyMatchWholeWords() {
        val keys = BoardTemplates.suggest(BoardKind.Fitness, installed)
        assertTrue(AppKey("com.google.android.apps.fitness", "Main") in keys)
        assertTrue(AppKey("com.strava", "Main") in keys)
        // „fit“ darf nicht in „Profit“ treffen.
        assertTrue(AppKey("com.example.profit", "Main") !in keys)
    }

    @Test fun customTemplateIsEmpty() {
        assertEquals(emptyList<AppKey>(), BoardTemplates.suggest(BoardKind.Custom, installed))
    }

    @Test fun suggestionsAreCapped() {
        val many = (1..30).map { AppInfo(AppKey("com.bank$it", "Main"), "Bank $it") }
        assertEquals(3, BoardTemplates.suggest(BoardKind.Finance, many, max = 3).size)
    }

    @Test fun maiPanelPerTemplate() {
        assertEquals(MaiSummaryApps.BUDGET, BoardTemplates.maiPackage(BoardKind.Finance))
        assertEquals(MaiSummaryApps.FITNESS, BoardTemplates.maiPackage(BoardKind.Fitness))
        assertNull(BoardTemplates.maiPackage(BoardKind.Travel))
        // Rot bleibt Warnungen vorbehalten – keine Vorlage nutzt eine Warnfarbe.
        BoardKind.entries.forEach { assertTrue(BoardTemplates.accent(it) in AccentChoice.entries) }
    }

    // ------------------------------------------------------------ Seitenreihenfolge

    @Test fun defaultOrderMatchesOldGestures() {
        val order = PageOrder.decode(null, emptyList())
        assertEquals(PageOrder.DEFAULT, order)
        // Wie bisher: vom Deck nach rechts wischen = Comms, nach links = Media.
        assertEquals(PageRef.Comms, PageOrder.neighbour(order, PageRef.Deck, -1))
        assertEquals(PageRef.Media, PageOrder.neighbour(order, PageRef.Deck, 1))
        assertNull(PageOrder.neighbour(order, PageRef.Comms, -1))
        assertNull(PageOrder.neighbour(order, PageRef.Media, 1))
    }

    @Test fun orderRoundTripWithBoards() {
        val order = listOf(PageRef.OfBoard("b1"), PageRef.Comms, PageRef.Deck, PageRef.Media, PageRef.OfBoard("b2"))
        assertEquals(order, PageOrder.decode(PageOrder.encode(order), listOf("b1", "b2")))
    }

    @Test fun orderIsRepaired() {
        // Unbekanntes und Doppeltes fällt weg, gelöschte Boards auch, fehlende kommen dazu.
        val order = PageOrder.decode("deck,zzz,board:weg,deck,board:b2", listOf("b1", "b2"))
        assertEquals(listOf(PageRef.Comms, PageRef.Deck, PageRef.Media, PageRef.OfBoard("b2"), PageRef.OfBoard("b1")), order)
    }

    @Test fun movePagesIncludingBuiltIns() {
        val order = PageOrder.normalize(PageOrder.DEFAULT, listOf("b1"))
        val moved = PageOrder.move(order, PageRef.OfBoard("b1"), -1)
        assertEquals(listOf(PageRef.Comms, PageRef.Deck, PageRef.OfBoard("b1"), PageRef.Media), moved)
        assertEquals(PageRef.OfBoard("b1"), PageOrder.neighbour(moved, PageRef.Deck, 1))
        // Am Rand bleibt alles, wie es ist.
        assertEquals(order, PageOrder.move(order, PageRef.Comms, -1))
    }
}
