package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCatalogTest {
    private val maps = AppInfo(AppKey("app.maps", "Main"), "Maps")
    private val bank = AppInfo(AppKey("app.sparkasse", "Main"), "Sparkasse")
    private val mail = AppInfo(AppKey("app.mail", "Main", 10), "Mail", isWork = true)
    private val game = AppInfo(AppKey("app.game", "Main"), "Game")
    private val all = listOf(maps, bank, mail, game)

    @Test fun tabsOnlyWhenNeeded() {
        assertEquals(listOf(IndexTab.All), AppCatalog.tabs(listOf(maps, bank), emptySet()))
        assertEquals(listOf(IndexTab.All, IndexTab.Work), AppCatalog.tabs(all, emptySet()))
        assertEquals(listOf(IndexTab.All, IndexTab.Work, IndexTab.Hidden), AppCatalog.tabs(all, setOf(game.key)))
        // Ausgeblendete, nicht mehr installierte App: kein Reiter.
        assertEquals(listOf(IndexTab.All, IndexTab.Work), AppCatalog.tabs(all, setOf(AppKey("gone", "X"))))
    }

    @Test fun hiddenAppsOnlyInHiddenTab() {
        val hidden = setOf(game.key, mail.key)
        assertEquals(listOf(maps, bank), AppCatalog.inTab(all, hidden, IndexTab.All))
        assertEquals(emptyList<AppInfo>(), AppCatalog.inTab(all, hidden, IndexTab.Work))
        assertEquals(listOf(mail, game), AppCatalog.inTab(all, hidden, IndexTab.Hidden))
        assertEquals(listOf(maps, bank), AppCatalog.searchable(all, hidden))
        // Arbeits-Apps: eigener Reiter, aber in der Suche.
        assertEquals(listOf(mail), AppCatalog.inTab(all, emptySet(), IndexTab.Work))
        assertEquals(all, AppCatalog.searchable(all, emptySet()))
    }

    @Test fun aliasFindsAndRanksApp() {
        val aliases = mapOf(bank.key to "Bank")
        assertEquals(listOf(bank), AppSearch.filter(all, "bank", aliases = aliases))
        assertEquals(emptyList<AppInfo>(), AppSearch.filter(all, "bank"))
        // Alias-Präfix schlägt einen Namens-Präfix nicht aus – beide erscheinen, Alias zuerst.
        val m = mapOf(game.key to "Mahjong")
        assertEquals(listOf(game, mail, maps), AppSearch.filter(all, "ma", aliases = m))
    }

    @Test fun aliasCodecRoundTripAndTolerance() {
        val map = mapOf(bank.key to "Bank", mail.key to "Work mail")
        assertEquals(map, AliasCodec.decode(AliasCodec.encode(map)))
        assertEquals(emptyMap<AppKey, String>(), AliasCodec.decode(listOf("kaputt", "\tleer", "app.x/Y#0\t   ")))
        assertEquals("a b", AliasCodec.clean("a\tb"))
        assertNull(AliasCodec.clean("  "))
        assertEquals(AliasCodec.MAX_LENGTH, AliasCodec.clean("x".repeat(100))!!.length)
    }
}
