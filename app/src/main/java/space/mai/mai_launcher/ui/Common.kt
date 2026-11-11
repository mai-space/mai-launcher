package space.mai.mai_launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.LocalAccent
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

/**
 * Seitenstriche: wo man zwischen den Wisch-Seiten steht. Die aktuelle Seite
 * ist ein längerer Strich in der Kontextfarbe; reine Anzeige, kein Leuchten.
 */
@Composable
fun PageTicks(index: Int, count: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.page_position, index + 1, count)
    val accent = LocalAccent.current.main
    Row(
        modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val current = i == index
            Box(
                Modifier
                    .size(width = if (current) 18.dp else 8.dp, height = 3.dp)
                    .background(if (current) accent else MaiColors.Text3),
            )
        }
    }
}
