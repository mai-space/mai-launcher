package space.mai.mai_launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.ui.deck.EditChip

/**
 * „Wahrscheinlich jetzt“: vier Sechsecke. Langes Drücken erklärt, warum die
 * App vorgeschlagen wird, und erlaubt, sie nie mehr vorzuschlagen.
 */
@Composable
fun LikelyTokens(
    likely: List<AppTile>,
    reasons: Map<AppKey, Int>,
    window: Pair<Int, Int>,
    size: Dp,
    onApp: (AppTile) -> Unit,
    onHide: (AppKey) -> Unit,
) {
    var explain by remember { mutableStateOf<AppKey?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            likely.take(4).forEach { tile ->
                AppToken(tile, Modifier.weight(1f), size = size, onLongClick = {
                    explain = if (explain == tile.key) null else tile.key
                }) { onApp(tile) }
            }
        }
        val tile = likely.firstOrNull { it.key == explain }
        if (tile != null) {
            val n = reasons[tile.key] ?: 0
            val (from, to) = window
            Spacer(Modifier.height(8.dp))
            MaiText(
                if (n > 0) pluralStringResource(R.plurals.likely_reason, n, tile.label, n, "%02d".format(from), "%02d".format(to))
                else stringResource(R.string.likely_reason_pattern, tile.label),
                MaiType.Body, color = MaiColors.Text2, maxLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditChip(stringResource(R.string.likely_hide), { explain = null; onHide(tile.key) }, MaiColors.Amber)
                EditChip(stringResource(R.string.likely_ok), { explain = null })
            }
        }
    }
}
