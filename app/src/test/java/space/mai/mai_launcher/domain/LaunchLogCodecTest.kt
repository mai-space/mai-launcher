package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchLogCodecTest {
    @Test fun roundTrip() {
        val events = listOf(LaunchEvent(AppKey("a", "b", 0), 1L), LaunchEvent(AppKey("c", "d", 10), 2L))
        assertEquals(events, LaunchLogCodec.decode(LaunchLogCodec.encode(events)))
    }

    @Test fun skipsBrokenLinesAndUnknownHeader() {
        val text = "v1\n1\ta/b#0\nkaputt\n\tx\n2\tc/d#0\textra-feld-aus-zukunft\n"
        val decoded = LaunchLogCodec.decode(text)
        assertEquals(1, decoded.size)
        assertTrue(LaunchLogCodec.decode("v9\n1\ta/b#0").isEmpty())
    }

    @Test fun capsSize() {
        val many = (1..LaunchLogCodec.MAX_EVENTS + 50).map { LaunchEvent(AppKey("a", "b"), it.toLong()) }
        val decoded = LaunchLogCodec.decode(LaunchLogCodec.encode(many))
        assertEquals(LaunchLogCodec.MAX_EVENTS, decoded.size)
        assertEquals((LaunchLogCodec.MAX_EVENTS + 50).toLong(), decoded.last().epochMillis)
    }
}
