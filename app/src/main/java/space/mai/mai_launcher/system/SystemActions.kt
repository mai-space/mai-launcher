package space.mai.mai_launcher.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

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
