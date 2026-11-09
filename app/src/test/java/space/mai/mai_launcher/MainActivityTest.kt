package space.mai.mai_launcher

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Startet die echte Activity (mit ViewModel, Systemstatus, App-Katalog). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MainActivityTest {
    @Test fun registeredAsHomeApp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = ctx.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(found.any { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test fun startsResumesAndHandlesHome() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            // Home-Taste, während der Launcher vorne ist (singleTask → onNewIntent).
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            s.recreate()
            s.onActivity { assertEquals(false, it.isFinishing) }
        }
    }
}
