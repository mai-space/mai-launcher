package space.mai.mai_launcher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.signals.MaiNotificationListener
import space.mai.mai_launcher.system.HomeRole
import space.mai.mai_launcher.ui.LauncherApp
import space.mai.mai_launcher.ui.LauncherHost
import space.mai.mai_launcher.ui.LauncherViewModel

/** Startbildschirm. Läuft als HOME-Activity (singleTask). */
class MainActivity : ComponentActivity(), LauncherHost {
    private val vm: LauncherViewModel by viewModels()
    private val homeRole by lazy { HomeRole(this) }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshAccess()
    }
    private val calendarRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshAccess()
    }
    private val contactsRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshAccess()
    }
    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onMicPermission(it)
    }
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        vm.onSpeechResult(text)
    }
    /** Sicherung: Datei anlegen bzw. öffnen (Speicherzugriffs-Framework, keine Berechtigung). */
    private var pendingExport: String? = null
    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri == null || text == null) return@registerForActivityResult
        val ok = runCatching { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess
        vm.onLayoutExported(ok)
    }
    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        // Sicherungen sind klein; größere Dateien gar nicht erst lesen.
        val text = runCatching {
            contentResolver.openInputStream(uri)!!.use { String(it.readNBytesCompat(MAX_LAYOUT_BYTES)) }
        }.getOrNull()
        vm.onLayoutImported(text)
    }

    private val widgetBind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onWidgetBound(it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(MaiColors.Void.copy(alpha = 0f).toArgb())
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        vm.host = this
        setContent { LauncherApp(vm) }
    }

    override fun onDestroy() {
        if (vm.host === this) vm.host = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        vm.onVisible()
    }

    override fun onResume() {
        super.onResume()
        // Freigaben können in den Einstellungen geändert worden sein.
        vm.refreshAccess()
        vm.system.refresh()
    }

    override fun onStop() {
        vm.onHidden()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHome()
    }

    // --- LauncherHost ---

    override fun requestHomeRole() {
        val intent = homeRole.requestIntent()
        if (intent != null) runCatching { roleRequest.launch(intent) }.onFailure { openHomeSettings() }
        else openHomeSettings()
    }

    private fun openHomeSettings() {
        runCatching { startActivity(homeRole.settingsIntent()) }
    }

    override fun requestCalendar() = calendarRequest.launch(Manifest.permission.READ_CALENDAR)

    override fun requestContacts() = contactsRequest.launch(Manifest.permission.READ_CONTACTS)

    override fun requestMic() = micRequest.launch(Manifest.permission.RECORD_AUDIO)

    override fun startSystemSpeech() {
        runCatching { speech.launch(vm.voice.systemIntent()) }
    }

    override fun openNotificationAccess() {
        val component = ComponentName(this, MaiNotificationListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else null
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val ok = detail != null && runCatching { startActivity(detail) }.isSuccess
        if (!ok) runCatching { startActivity(list) }
    }

    override fun bindWidget(id: Int, provider: ComponentName) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        runCatching { widgetBind.launch(intent) }.onFailure { vm.onWidgetBound(false) }
    }

    override fun exportLayout(fileName: String, text: String) {
        pendingExport = text
        runCatching { exportDoc.launch(fileName) }.onFailure { vm.onLayoutExported(false) }
    }

    override fun importLayout() {
        runCatching { importDoc.launch(arrayOf("text/plain", "application/octet-stream")) }
            .onFailure { vm.onLayoutImported(null) }
    }

    override fun configureWidget(id: Int) {
        if (!vm.widgets.configureLauncher(this, id, REQUEST_CONFIGURE)) vm.onWidgetConfigured(false)
    }

    @Deprecated("AppWidgetHost liefert das Ergebnis der Einrichtung nur über diesen Weg.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) vm.onWidgetConfigured(resultCode == RESULT_OK)
    }

    private companion object {
        const val REQUEST_CONFIGURE = 4711
        const val MAX_LAYOUT_BYTES = 256 * 1024
    }

    /** Liest höchstens [max] Bytes (readNBytes gibt es erst ab API 33). */
    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
