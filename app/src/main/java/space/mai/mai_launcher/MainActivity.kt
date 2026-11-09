package space.mai.mai_launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import space.mai.mai_launcher.design.MaiColors
import androidx.compose.ui.graphics.toArgb
import space.mai.mai_launcher.ui.LauncherApp
import space.mai.mai_launcher.ui.LauncherViewModel

/** Startbildschirm. Läuft als HOME-Activity (singleTask). */
class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer dunkel: helle Symbole in Status- und Navigationsleiste.
        val dark = SystemBarStyle.dark(MaiColors.Void.copy(alpha = 0f).toArgb())
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        setContent { LauncherApp(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.system.start()
    }

    override fun onResume() {
        super.onResume()
        // Werte ohne eigene Benachrichtigung (BT, Speicher) beim Zurückkehren auffrischen.
        vm.system.refresh()
    }

    override fun onStop() {
        vm.system.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home-Taste, während der Launcher schon vorne ist: zurück zum Deck.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHome()
    }
}
