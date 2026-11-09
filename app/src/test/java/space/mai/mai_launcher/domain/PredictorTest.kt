package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PredictorTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val p = Predictor(zone)
    private val news = AppKey("news", "M")
    private val music = AppKey("music", "M")
    private val bank = AppKey("bank", "M")
    private val all = setOf(news, music, bank)

    private fun at(day: Int, h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    // 2.–6.11.2026 sind Montag bis Freitag.
    private val history = buildList {
        for (d in 2..6) {
            add(LaunchEvent(news, at(d, 7, 30)))
            add(LaunchEvent(music, at(d, 18, 0)))
        }
        add(LaunchEvent(bank, at(3, 12, 0)))
    }

    @Test fun morningSuggestsMorningApp() {
        val r = p.predict(history, at(9, 7, 45), all)
        assertEquals(news, r.first())
        assertTrue(music !in r)
    }

    @Test fun eveningSuggestsEveningApp() {
        assertEquals(music, p.predict(history, at(9, 18, 10), all).first())
    }

    @Test fun excludesDockAndUninstalled() {
        assertTrue(p.predict(history, at(9, 7, 45), all, exclude = setOf(news)).isEmpty())
        assertTrue(p.predict(history, at(9, 7, 45), setOf(music, bank)).isEmpty())
    }

    @Test fun singleOldLaunchIsNoise() {
        val old = listOf(LaunchEvent(bank, at(3, 12, 0) - 60L * 86_400_000L))
        assertTrue(p.predict(old, at(9, 12, 0), all).isEmpty())
    }

    @Test fun ignoresFutureEvents() {
        assertTrue(p.predict(listOf(LaunchEvent(bank, at(20, 12))), at(9, 12), all).isEmpty())
    }

    @Test fun windowWrapsMidnight() {
        assertEquals(Pair(23, 1), p.window(at(9, 0, 30)))
        assertEquals(Pair(6, 8), p.window(at(9, 7, 30)))
    }
}
