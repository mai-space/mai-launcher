package space.mai.mai_launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey

/**
 * Katalog aller startbaren Apps über [LauncherApps] – inkl. Arbeitsprofil.
 * Meldet sich bei jeder Paketänderung neu.
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val me: UserHandle = Process.myUserHandle()

    /** Liefert die App-Liste sofort und nach jeder Installation/Entfernung erneut. */
    fun apps(): Flow<List<AppInfo>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
        }
        trySend(Unit)
        launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate().map { loadAll() }.flowOn(Dispatchers.IO)

    private fun loadAll(): List<AppInfo> {
        val out = ArrayList<AppInfo>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            for (info in launcherApps.getActivityList(null, user)) {
                // Sich selbst nicht auflisten.
                if (info.componentName.packageName == context.packageName) continue
                out.add(info.toAppInfo(serial, user != me))
            }
        }
        return out
    }

    private fun LauncherActivityInfo.toAppInfo(serial: Long, work: Boolean) = AppInfo(
        key = AppKey(componentName.packageName, componentName.className, serial),
        label = label?.toString().orEmpty().ifBlank { componentName.packageName },
        isWork = work,
    )

    fun userFor(key: AppKey): UserHandle? = userManager.getUserForSerialNumber(key.profile)

    fun activityInfo(key: AppKey): LauncherActivityInfo? {
        val user = userFor(key) ?: return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(key.packageName, key.className))
        return launcherApps.resolveActivity(intent, user)
    }

    /** Startet die App; liefert false, wenn sie nicht (mehr) startbar ist. */
    fun launch(key: AppKey, sourceBounds: Rect? = null): Boolean = runCatching {
        val user = userFor(key) ?: return false
        launcherApps.startMainActivity(ComponentName(key.packageName, key.className), user, sourceBounds, null)
        true
    }.getOrDefault(false)

    fun openAppInfo(key: AppKey) {
        val user = userFor(key) ?: return
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.className), user, null, null)
        }
    }

    fun uninstall(key: AppKey) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, "package:${key.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Standard-Dock: Telefon, Nachrichten, Kamera, Browser – jeweils die
     * Standard-App des Systems, sofern sie eine Start-Activity hat.
     */
    fun defaultDock(apps: List<AppInfo>): List<AppKey?> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, "https://example.org".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
        )
        val mainApps = apps.filter { !it.isWork }
        return intents.map { intent ->
            val pkg = context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" }
            pkg?.let { p -> mainApps.firstOrNull { it.key.packageName == p }?.key }
        }
    }
}
