package space.mai.mai_launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.mai.mai_launcher.domain.WidgetIds

/** Ein Widget-Angebot einer App für die Auswahl. */
data class WidgetOption(
    val provider: ComponentName,
    val label: String,
    val appLabel: String,
    val cellsWide: Int,
    val cellsHigh: Int,
    val info: AppWidgetProviderInfo,
)

/**
 * Bettet Android-Widgets anderer Apps ins Deck ein. Die IDs werden gespeichert
 * (Schlüssel `widgets` – nie umbenennen); die Inhalte zeichnet die jeweilige App.
 */
class WidgetHost(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(readIds())
    val ids: StateFlow<List<Int>> = _ids.asStateFlow()

    private fun readIds(): List<Int> = WidgetIds.decode(prefs.getString(KEY, null))

    private fun save(ids: List<Int>) {
        prefs.edit { putString(KEY, WidgetIds.encode(ids)) }
        _ids.value = ids
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun options(): List<WidgetOption> {
        val pm = context.packageManager
        return manager.getInstalledProvidersForProfile(Process.myUserHandle()).map { info ->
            val appLabel = runCatching {
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            val dm = context.resources.displayMetrics
            val wDp = info.minWidth / dm.density
            val hDp = info.minHeight / dm.density
            val (cw, ch) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
                info.targetCellWidth to info.targetCellHeight
            } else {
                cells(wDp) to cells(hDp)
            }
            WidgetOption(info.provider, info.loadLabel(pm) ?: appLabel, appLabel, cw, ch, info)
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun preview(option: WidgetOption): Drawable? = runCatching {
        option.info.loadPreviewImage(context, 0) ?: option.info.loadIcon(context, 0)
    }.getOrNull()

    /** Schritt 1: ID holen und binden. Liefert die ID und ob eine Freigabe nötig ist. */
    fun allocate(provider: ComponentName): Pair<Int, Boolean> {
        val id = host.allocateAppWidgetId()
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider) }.getOrDefault(false)
        return id to !bound
    }

    fun needsConfigure(id: Int): Boolean = manager.getAppWidgetInfo(id)?.configure != null

    fun configureLauncher(activity: android.app.Activity, id: Int, requestCode: Int): Boolean = runCatching {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    }.getOrDefault(false)

    /** Schritt 2: fertig gebundenes Widget aufs Deck legen. */
    fun commit(id: Int) {
        if (manager.getAppWidgetInfo(id) == null) { discard(id); return }
        if (id !in _ids.value) save(_ids.value + id)
    }

    fun discard(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun remove(id: Int) {
        discard(id)
        save(_ids.value - id)
    }

    fun move(id: Int, delta: Int) {
        val list = _ids.value.toMutableList()
        val i = list.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.removeAt(i)
        list.add(j, id)
        save(list)
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(context.applicationContext, id, info)
    }

    /** Größe in dp an das Widget melden, damit es passend zeichnet. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }

    companion object {
        private const val HOST_ID = 1024
        private const val KEY = "widgets"

        /** Android-Faustformel: n Zellen ≈ 70·n − 30 dp. */
        fun cells(dp: Float): Int = ((dp + 30f) / 70f).toInt().coerceIn(1, 5)
    }
}
