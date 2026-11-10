package space.mai.mai_launcher.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** Ist der Launcher die Startbildschirm-App? Und der System-Dialog, um es zu werden. */
class HomeRole(private val context: Context) {
    private val roles = context.getSystemService(RoleManager::class.java)

    fun isHome(): Boolean = runCatching { roles.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)

    fun requestIntent(): Intent? = runCatching {
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }.getOrNull()

    /** Ist der Launcher der Standard-Assistent (Assistenten-Geste öffnet die Befehlszeile)? */
    fun isAssistant(): Boolean = runCatching { roles.isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)

    /** Die Assistenten-Rolle lässt sich nicht per Dialog anfragen: Einstellungsseite „Digitaler Assistent“. */
    fun assistantSettingsIntent(): Intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)

    /** Rückfall: Einstellungsseite „Startbildschirm-App“. */
    fun settingsIntent(): Intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
}
