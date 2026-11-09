package space.mai.mai_launcher.system

import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Alles, was Deck und Seitenleiste vom System zeigen – nur echte Werte. */
data class SystemState(
    val nowMillis: Long = System.currentTimeMillis(),
    val nextAlarmMillis: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val storageFreeBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val wifiConnected: Boolean = false,
    val bluetoothOn: Boolean? = null,
    val dndOn: Boolean = false,
    val torchAvailable: Boolean = false,
    val torchOn: Boolean = false,
    val autoRotate: Boolean = false,
)

/**
 * Beobachtet Uhr, Wecker, Akku, Speicher und die Schalter der Seitenleiste.
 * [start] beim Anzeigen, [stop] beim Verlassen – so läuft im Hintergrund nichts.
 */
class SystemStatus(private val context: Context) {
    private val _state = MutableStateFlow(SystemState())
    val state: StateFlow<SystemState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val torchCameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
                else -> refresh()
            }
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) _state.update { it.copy(torchOn = enabled) }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refreshWifi()
        override fun onLost(network: Network) = refreshWifi()
    }

    private val rotationObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        }
        // Systemsendungen; NOT_EXPORTED genügt, da sie vom System kommen.
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::readBattery)
        runCatching { cameraManager.registerTorchCallback(torchCallback, main) }
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback, main) }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, rotationObserver,
        )
        refresh()
        refreshWifi()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        context.contentResolver.unregisterContentObserver(rotationObserver)
    }

    /** Liest die übrigen Werte neu (Uhr, Wecker, Speicher, BT, Ruhe, Drehen). */
    fun refresh() {
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val bt = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        }.getOrNull()
        val dnd = notificationManager.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        val rotate = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        _state.update {
            it.copy(
                nowMillis = System.currentTimeMillis(),
                nextAlarmMillis = alarmManager.nextAlarmClock?.triggerTime,
                storageFreeBytes = stat?.availableBytes,
                storageTotalBytes = stat?.totalBytes,
                bluetoothOn = bt,
                dndOn = dnd,
                torchAvailable = torchCameraId != null,
                autoRotate = rotate,
            )
        }
    }

    private fun refreshWifi() {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        _state.update { it.copy(wifiConnected = wifi) }
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        _state.update { it.copy(batteryPercent = pct, charging = charging) }
    }

    /** Taschenlampe direkt schalten (ohne Kamera-Berechtigung möglich). */
    fun toggleTorch() {
        val id = torchCameraId ?: return
        runCatching { cameraManager.setTorchMode(id, !_state.value.torchOn) }
    }
}
