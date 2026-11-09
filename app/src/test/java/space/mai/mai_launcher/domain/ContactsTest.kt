package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {
    private val alex = Contact("k-alex", "Alex Berg", starred = true, numbers = listOf("+49 151 1111111"))
    private val sam = Contact("k-sam", "Sam Ober", starred = false, numbers = listOf("040 2222222"))
    private val mama = Contact("k-mama", "Mama", starred = true, numbers = listOf("0171 3333333"))
    private val kim = Contact("k-kim", "Kim Ödegaard", starred = false, numbers = emptyList())
    private val all = listOf(sam, kim, alex, mama)
    private val day = 24L * 3600 * 1000

    @Test fun favouritesAreStarredAlphabetical() {
        assertEquals(listOf(alex, mama), ContactRanking.favourites(all))
    }

    @Test fun frequentDecaysAndSkipsFavourites() {
        val now = 100 * day
        val log = mapOf(
            "k-sam" to CommsEntry(4, now - 90 * day),
            "k-kim" to CommsEntry(2, now - 1 * day),
            "k-alex" to CommsEntry(10, now),
        )
        assertEquals(listOf(kim, sam), ContactRanking.frequent(all, log, now))
    }

    @Test fun searchByNameInitialsAccentsAndNumber() {
        assertEquals(listOf(sam), ContactRanking.search(all, "sam"))
        assertEquals(listOf(kim), ContactRanking.search(all, "odeg"))
        assertEquals(listOf(alex), ContactRanking.search(all, "ab"))
        assertEquals(listOf(mama), ContactRanking.search(all, "3333"))
        assertEquals(listOf(sam), ContactRanking.search(all, "ober"))
        assertTrue(ContactRanking.search(all, "zzz").isEmpty())
    }

    @Test fun logCodecRoundTripAndTolerance() {
        val log = CommsLogCodec.record(CommsLogCodec.record(emptyMap(), "k-sam", 5), "k-sam", 9)
        assertEquals(CommsEntry(2, 9), log["k-sam"])
        assertEquals(log, CommsLogCodec.decode(CommsLogCodec.encode(log)))
        assertEquals(emptyMap<String, CommsEntry>(), CommsLogCodec.decode("v9\nx\t1\t2"))
        assertEquals(mapOf("a" to CommsEntry(1, 2)), CommsLogCodec.decode("v1\na\t1\t2\nkaputt\nb\tx\t3"))
    }

    @Test fun contactVerbs() {
        assertEquals(ContactVerb.Kind.Call to "sam", ContactVerb.parse("call sam"))
        assertEquals(ContactVerb.Kind.Text to "alex", ContactVerb.parse("SMS an alex"))
        assertNull(ContactVerb.parse("call"))
        assertNull(ContactVerb.parse("spotify music"))
    }

    @Test fun keypad() {
        var n = ""
        "+0+40x1#".forEach { n = Keypad.press(n, it) }
        assertEquals("+0401#", n)
        assertEquals("+0401", Keypad.backspace(n))
        assertEquals("+4915 1123 4567", Keypad.pretty("+4915112345 67".replace(" ", "")))
        assertEquals("110", Keypad.pretty("110"))
    }
}
