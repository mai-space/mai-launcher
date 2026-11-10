package space.mai.mai_launcher.system

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.net.toUri
import space.mai.mai_launcher.domain.Command
import space.mai.mai_launcher.domain.SettingPage

/**
 * Öffnet Systemseiten. Android 10+ erlaubt Apps nicht, WLAN/Bluetooth direkt
 * zu schalten – daher das passende Schnell-Panel bzw. die Einstellungsseite.
 */
class SystemActions(private val context: Context) {

    private fun start(vararg intents: Intent) {
        for (intent in intents) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (ok) return
        }
    }

    fun wifiPanel() = start(Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS))
    fun wifiSettings() = start(Intent(Settings.ACTION_WIFI_SETTINGS))
    fun bluetoothSettings() = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun dndSettings() = start(Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SOUND_SETTINGS))
    fun policyAccessSettings() = start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
    fun soundSettings() = start(Intent(Settings.ACTION_SOUND_SETTINGS))
    fun displaySettings() = start(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun batterySettings() = start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_SETTINGS))
    fun storageSettings() = start(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    fun alarms() = start(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun homeSettings() = start(Intent(Settings.ACTION_HOME_SETTINGS))
    fun systemSettings() = start(Intent(Settings.ACTION_SETTINGS))
    fun launcherInfo() = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))

    /** Befehl aus der Befehlszeile ausführen (Wählen nur über die Telefon-App). */
    fun run(cmd: Command) {
        when (cmd) {
            is Command.Answer -> copy(cmd.value)
            is Command.Timer -> start(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, cmd.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                Intent(AlarmClock.ACTION_SHOW_TIMERS),
            )
            is Command.Alarm -> start(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, cmd.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
            )
            // Für die Stoppuhr gibt es keinen Standard-Intent: Uhr-App öffnen.
            Command.Stopwatch -> start(Intent(AlarmClock.ACTION_SHOW_TIMERS), Intent(AlarmClock.ACTION_SHOW_ALARMS))
            is Command.Setting -> setting(cmd.page)
            is Command.Dial -> start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", cmd.number, null)))
            // Erst auf Tippen; Suchmaschine bestimmt die Such-App bzw. der Browser.
            // Notizen speichert das ViewModel selbst.
            is Command.Note -> Unit
            is Command.WebSearch -> start(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, cmd.query),
                Intent(Intent.ACTION_VIEW, "https://duckduckgo.com/?q=${Uri.encode(cmd.query)}".toUri()),
            )
        }
    }

    private fun setting(page: SettingPage) = when (page) {
        SettingPage.Wifi -> wifiSettings()
        SettingPage.Bluetooth -> bluetoothSettings()
        SettingPage.Display -> displaySettings()
        SettingPage.Sound -> soundSettings()
        SettingPage.Battery -> batterySettings()
        SettingPage.Apps -> start(Intent(Settings.ACTION_APPLICATION_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        SettingPage.Location -> start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        SettingPage.Notifications -> start(Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
        SettingPage.Storage -> storageSettings()
        SettingPage.Settings -> start(Intent(Settings.ACTION_SETTINGS))
    }

    private fun copy(text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Mai", text))
    }

    /**
     * Benachrichtigungen aufklappen. Keine öffentliche API; viele Launcher nutzen
     * StatusBarManager#expandNotificationsPanel (Berechtigung EXPAND_STATUS_BAR).
     */
    @SuppressLint("WrongConstant")
    fun expandNotifications() {
        runCatching {
            val sbm = context.getSystemService("statusbar") ?: return
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }
}
