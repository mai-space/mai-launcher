package space.mai.mai_launcher.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Was gerade läuft (aus einer Media-Session irgendeiner App). */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val appLabel: String?,
    val playing: Boolean,
    val positionMs: Long?,
    val durationMs: Long?,
    /** Zeitpunkt (elapsedRealtime) der letzten Positionsmeldung, für die Fortschreibung. */
    val positionUpdatedAt: Long = 0,
    val speed: Float = 1f,
) {
    /** Aktuelle Position, fortgeschrieben ab der letzten Meldung. */
    fun positionAt(elapsedRealtime: Long): Long? {
        val pos = positionMs ?: return null
        if (!playing || positionUpdatedAt <= 0) return pos
        val now = pos + ((elapsedRealtime - positionUpdatedAt) * speed).toLong()
        return durationMs?.let { now.coerceIn(0, it) } ?: now.coerceAtLeast(0)
    }
}

/** Laufende Navigation, gelesen aus der Benachrichtigung der Karten-App. */
data class NavInfo(
    val distance: String?,
    val instruction: String,
    val eta: String?,
    val appLabel: String?,
)

/**
 * Liest die Navigations-Benachrichtigung. Karten-Apps legen meist die Distanz
 * in den Titel („200 m“) und die Anweisung in den Text („Rechts abbiegen auf …“);
 * manche umgekehrt. Ohne verwertbaren Text: null.
 */
object NavParser {
    private val distance = Regex("^\\s*\\d+([.,]\\d+)?\\s*(m|km|ft|mi|yd)\\b.*", RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String?, subText: String?, appLabel: String?): NavInfo? {
        val t = title?.trim().orEmpty()
        val x = text?.trim().orEmpty()
        if (t.isEmpty() && x.isEmpty()) return null
        return when {
            distance.matches(t) && x.isNotEmpty() -> NavInfo(t, x, subText?.trim()?.ifEmpty { null }, appLabel)
            distance.matches(x) && t.isNotEmpty() -> NavInfo(x, t, subText?.trim()?.ifEmpty { null }, appLabel)
            else -> NavInfo(null, listOf(t, x).filter { it.isNotEmpty() }.joinToString(" · "), subText?.trim()?.ifEmpty { null }, appLabel)
        }
    }
}

/** Ein kommender Termin. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String? = null,
)

/** Abstand bis zu einem Zeitpunkt, für Beschriftungen wie „IN 2 H“ oder „MORGEN“. */
sealed interface Until {
    data object Now : Until
    data class Minutes(val m: Long) : Until
    data class Hours(val h: Long) : Until
    data object Tomorrow : Until
    data class Days(val d: Long) : Until
}

object TimeLabels {
    /** Ganztägige Termine zählen nach Kalendertagen, alle anderen nach Uhrzeit. */
    fun until(event: CalendarEvent, nowMillis: Long, zone: ZoneId): Until {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        if (event.allDay) {
            // Ganztägig wird in UTC gespeichert (Mitternacht UTC).
            val day = Instant.ofEpochMilli(event.beginMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            return dayDistance(today, day, nowMillis >= event.beginMillis)
        }
        if (nowMillis >= event.beginMillis) return Until.Now
        val minutes = (event.beginMillis - nowMillis + 59_999) / 60_000
        val day = Instant.ofEpochMilli(event.beginMillis).atZone(zone).toLocalDate()
        return when {
            minutes < 60 -> Until.Minutes(minutes)
            day == today -> Until.Hours((minutes + 30) / 60)
            else -> dayDistance(today, day, false)
        }
    }

    private fun dayDistance(today: LocalDate, day: LocalDate, started: Boolean): Until {
        val d = day.toEpochDay() - today.toEpochDay()
        return when {
            d <= 0 || started -> Until.Now
            d == 1L -> Until.Tomorrow
            else -> Until.Days(d)
        }
    }

    fun localStart(event: CalendarEvent, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(event.beginMillis).atZone(if (event.allDay) ZoneId.of("UTC") else zone)
}

/** Liste der eingebauten Android-Widgets (IDs), gespeichert als "1,7,9". Nur erweitern. */
object WidgetIds {
    fun encode(ids: List<Int>): String = ids.joinToString(",")
    fun decode(raw: String?): List<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct()
}
