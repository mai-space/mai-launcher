package space.mai.mai_launcher.signals

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import space.mai.mai_launcher.domain.NavParser

/**
 * Benachrichtigungszugriff (optional, vom Nutzer freigegeben). Wird gebraucht für:
 * Media-Sessions anderer Apps (Musik-Panel) und laufende Navigation.
 * Liest nur, speichert nichts, sendet nichts.
 */
class MaiNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        SignalsHub.setConnected(true)
        runCatching { activeNotifications?.forEach(::consider) }
    }

    override fun onListenerDisconnected() {
        SignalsHub.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = consider(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        SignalsHub.removeNavigation(sbn.key)
    }

    private fun consider(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_NAVIGATION) return
        val extras = n.extras
        val info = NavParser.parse(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            appLabel = appLabel(sbn.packageName),
        ) ?: return
        SignalsHub.setNavigation(SignalsHub.NavEntry(sbn.key, info, n.contentIntent))
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()
}
