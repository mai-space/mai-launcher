package space.mai.mai_launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.domain.AppKey

/** Einfarbiges Symbol asynchron laden (null = Monogramm zeigen). */
@Composable
fun rememberAppIcon(key: AppKey): ImageBitmap? {
    val provider = LocalIconProvider.current
    val icon by produceState<ImageBitmap?>(null, key, provider) { value = provider.icon(key) }
    return icon
}

/** App als Sechseck-Token, optional mit Namen darunter. In Reihen mit `Modifier.weight(1f)` verwenden. */
@Composable
fun AppToken(
    tile: AppTile,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showName: Boolean = true,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val icon = rememberAppIcon(tile.key)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        HexToken(
            contentDescription = tile.label,
            size = size,
            text = if (icon == null) tile.monogram else null,
            icon = icon,
            badge = badge,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        if (showName) {
            Spacer(Modifier.height(2.dp))
            MaiLabel(tile.label, color = MaiColors.Text2, align = TextAlign.Center)
        }
    }
}
