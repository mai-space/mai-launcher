package space.mai.mai_launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.staticCompositionLocalOf
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.monogram

/** App, wie sie die Oberfläche braucht. */
@Immutable
data class AppTile(val key: AppKey, val label: String, val isWork: Boolean = false) {
    val monogram: String get() = monogram(label)
}

fun AppInfo.toTile() = AppTile(key, label, isWork)

enum class Screen { Deck, Index, Widgets }

/** Liefert einfarbige App-Symbole; in Tests/Vorschau ohne Symbole. */
fun interface IconProvider {
    suspend fun icon(key: AppKey): ImageBitmap?
}

val LocalIconProvider = staticCompositionLocalOf { IconProvider { null } }
