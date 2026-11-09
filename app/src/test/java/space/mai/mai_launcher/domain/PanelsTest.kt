package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class PanelsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun utcMidnight(day: Int) = LocalDateTime.of(2026, 11, day, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun navDistanceInTitle() {
        val n = NavParser.parse("200 m", "Rechts abbiegen auf Hauptstraße", "Ankunft 14:32", "Maps")!!
        assertEquals("200 m", n.distance)
        assertEquals("Rechts abbiegen auf Hauptstraße", n.instruction)
        assertEquals("Ankunft 14:32", n.eta)
    }

    @Test fun navDistanceInText() {
        val n = NavParser.parse("Turn left", "1.2 km", null, null)!!
        assertEquals("1.2 km", n.distance)
        assertEquals("Turn left", n.instruction)
    }

    @Test fun navWithoutDistance() {
        val n = NavParser.parse("Rerouting", "", " ", null)!!
        assertNull(n.distance)
        assertEquals("Rerouting", n.instruction)
        assertNull(n.eta)
        assertNull(NavParser.parse(null, "  ", null, null))
    }

    @Test fun untilLabels() {
        val now = at(9, 14, 0)
        fun ev(begin: Long, allDay: Boolean = false) = CalendarEvent(1, "x", begin, begin + 3_600_000, allDay)
        assertEquals(Until.Now, TimeLabels.until(ev(at(9, 13, 30)), now, zone))
        assertEquals(Until.Minutes(25), TimeLabels.until(ev(at(9, 14, 25)), now, zone))
        assertEquals(Until.Hours(4), TimeLabels.until(ev(at(9, 18, 0)), now, zone))
        assertEquals(Until.Tomorrow, TimeLabels.until(ev(at(10, 9, 15)), now, zone))
        assertEquals(Until.Days(3), TimeLabels.until(ev(at(12, 9, 0)), now, zone))
        // Ganztägig: Kalendertage, gespeichert als UTC-Mitternacht.
        assertEquals(Until.Now, TimeLabels.until(ev(utcMidnight(9), true), now, zone))
        assertEquals(Until.Tomorrow, TimeLabels.until(ev(utcMidnight(10), true), now, zone))
    }

    @Test fun widgetIdsTolerant() {
        assertEquals(listOf(3, 7), WidgetIds.decode("3, 7,x,,-1,3"))
        assertEquals(emptyList<Int>(), WidgetIds.decode(null))
        assertEquals("3,7", WidgetIds.encode(listOf(3, 7)))
    }

    @Test fun positionAdvancesOnlyWhilePlaying() {
        val np = NowPlaying("t", null, null, playing = true, positionMs = 10_000, durationMs = 60_000, positionUpdatedAt = 1_000)
        assertEquals(15_000L, np.positionAt(6_000))
        assertEquals(60_000L, np.positionAt(1_000_000))
        assertEquals(10_000L, np.copy(playing = false).positionAt(6_000))
    }

    @Test fun soundModeCycle() {
        assertEquals(SoundMode.Vibrate, SoundMode.Ring.next())
        assertEquals(SoundMode.Mute, SoundMode.Vibrate.next())
        assertEquals(SoundMode.Ring, SoundMode.Mute.next())
        assertEquals(SoundMode.Ring, SoundMode.of(99))
        assertEquals(SoundMode.Vibrate, SoundMode.of(1))
    }

    @Test fun seekRingAngles() {
        assertEquals(0f, SeekRing.fraction(50f, 0f, 100f, 100f), 0.001f)
        assertEquals(0.25f, SeekRing.fraction(100f, 50f, 100f, 100f), 0.001f)
        assertEquals(0.5f, SeekRing.fraction(50f, 100f, 100f, 100f), 0.001f)
        assertEquals(0.75f, SeekRing.fraction(0f, 50f, 100f, 100f), 0.001f)
    }
}
