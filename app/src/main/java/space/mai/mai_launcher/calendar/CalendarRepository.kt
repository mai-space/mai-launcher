package space.mai.mai_launcher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.mai.mai_launcher.domain.CalendarEvent

/** Kommende Termine der nächsten 7 Tage (nur mit Kalender-Berechtigung, nur lesen). */
class CalendarRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun hasAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasAccess()) return
        if (!observing) {
            runCatching {
                context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
                observing = true
            }
        }
        refresh()
    }

    fun stop() {
        if (observing) context.contentResolver.unregisterContentObserver(observer)
        observing = false
    }

    fun refresh() {
        if (!hasAccess()) return
        scope.launch(Dispatchers.IO) { _events.value = runCatching { query() }.getOrDefault(emptyList()) }
    }

    private fun query(): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - DAY)
            ContentUris.appendId(it, now + 7 * DAY)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1"
        val out = ArrayList<CalendarEvent>()
        context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                val end = c.getLong(3)
                // Schon vorbei? Ganztägige gelten bis Tagesende (UTC-Mitternacht des Endes).
                if (end <= now) continue
                out.add(
                    CalendarEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.ifBlank { null } ?: "–",
                        beginMillis = c.getLong(2),
                        endMillis = end,
                        allDay = allDay,
                        location = c.getString(5)?.ifBlank { null },
                    ),
                )
            }
        }
        return out.take(MAX)
    }

    fun openEvent(id: Long) = start(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)))

    fun openCalendar() {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also {
            ContentUris.appendId(it, System.currentTimeMillis())
        }.build()
        start(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private companion object {
        const val DAY = 86_400_000L
        const val MAX = 4
    }
}
