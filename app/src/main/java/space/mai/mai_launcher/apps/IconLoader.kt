package space.mai.mai_launcher.apps

import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.mai.mai_launcher.domain.AppKey

/**
 * Lädt das EINFARBIGE Symbol einer App (Ebene „monochrome“ der adaptiven
 * Symbole, ab Android 13). Apps ohne diese Ebene bekommen null – die
 * Oberfläche zeigt dann ein Monogramm. So passt jede App in die Kontextfarbe.
 */
class IconLoader(private val repo: AppRepository, private val sizePx: Int) {
    private val cache = LruCache<AppKey, Holder>(256)

    private class Holder(val bitmap: ImageBitmap?)

    suspend fun monochrome(key: AppKey): ImageBitmap? {
        cache.get(key)?.let { return it.bitmap }
        val bmp = withContext(Dispatchers.IO) { render(key) }
        cache.put(key, Holder(bmp))
        return bmp
    }

    private fun render(key: AppKey): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val info = repo.activityInfo(key) ?: return null
        val drawable = runCatching { info.getIcon(0) }.getOrNull() as? AdaptiveIconDrawable ?: return null
        val mono = drawable.monochrome ?: return null
        // Adaptive Ebenen sind 108 dp groß, sichtbar sind die mittleren 72 dp (2/3).
        val n = sizePx
        val bitmap = createBitmap(n, n)
        val canvas = Canvas(bitmap)
        mono.setBounds(-n / 4, -n / 4, n + n / 4, n + n / 4)
        mono.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
