package space.mai.mai_launcher.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Ein App-Start aus dem lokalen Verlauf. */
data class LaunchEvent(val key: AppKey, val epochMillis: Long)

/**
 * Sagt voraus, welche Apps jetzt wahrscheinlich gebraucht werden – nur aus dem
 * eigenen Startverlauf, ohne Nutzungsstatistik-Berechtigung.
 *
 * Gewicht je Start = Tageszeit-Nähe (Gauß, σ = 1,5 h, über Mitternacht hinweg)
 *                  × Tagesart (Werktag/Wochenende gleich: 1, sonst 0,5)
 *                  × Aktualität (Halbwertszeit 14 Tage).
 */
class Predictor(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val sigmaHours: Double = 1.5,
    private val halfLifeDays: Double = 14.0,
) {
    fun predict(
        events: List<LaunchEvent>,
        nowMillis: Long,
        installed: Set<AppKey>,
        exclude: Set<AppKey> = emptySet(),
        limit: Int = 4,
    ): List<AppKey> {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val nowHour = now.hour + now.minute / 60.0
        val nowWeekend = now.dayOfWeek.isWeekend()
        val scores = HashMap<AppKey, Double>()
        for (e in events) {
            if (e.key !in installed || e.key in exclude) continue
            if (e.epochMillis > nowMillis) continue
            val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(e.epochMillis), zone)
            val hour = t.hour + t.minute / 60.0
            val diff = abs(hour - nowHour).let { min(it, 24 - it) }
            val timeWeight = exp(-(diff * diff) / (2 * sigmaHours * sigmaHours))
            val dayWeight = if (t.dayOfWeek.isWeekend() == nowWeekend) 1.0 else 0.5
            val ageDays = (nowMillis - e.epochMillis) / 86_400_000.0
            val recency = 0.5.pow(ageDays / halfLifeDays)
            scores.merge(e.key, timeWeight * dayWeight * recency, Double::plus)
        }
        return scores.entries
            .filter { it.value >= MIN_SCORE }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /**
     * Begründung für einen Vorschlag: wie oft die App in den letzten
     * [days] Tagen um diese Tageszeit (± [windowHours]) gestartet wurde.
     */
    fun reason(events: List<LaunchEvent>, key: AppKey, nowMillis: Long, days: Int = 28, windowHours: Double = 1.5): Int {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val nowHour = now.hour + now.minute / 60.0
        val from = nowMillis - days * 86_400_000L
        return events.count { e ->
            if (e.key != key || e.epochMillis > nowMillis || e.epochMillis < from) return@count false
            val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(e.epochMillis), zone)
            val diff = abs(t.hour + t.minute / 60.0 - nowHour).let { min(it, 24 - it) }
            diff <= windowHours
        }
    }

    /** Zeitfenster für die Beschriftung ("07–09"): aktuelle Stunde ±1. */
    fun window(nowMillis: Long): Pair<Int, Int> {
        val h = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone).hour
        return Pair((h + 23) % 24, (h + 1) % 24)
    }

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

    companion object {
        /** Unterhalb davon ist ein Vorschlag eher Rauschen als Gewohnheit. */
        const val MIN_SCORE = 0.3
    }
}

/** Startanzahl je App (für die Rangfolge bei Suchtreffern). */
fun launchCounts(events: List<LaunchEvent>): Map<AppKey, Int> =
    events.groupingBy { it.key }.eachCount()
