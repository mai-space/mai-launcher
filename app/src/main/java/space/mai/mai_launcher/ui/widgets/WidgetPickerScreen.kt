package space.mai.mai_launcher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.design.glowBorder

/** Ein Eintrag der Widget-Auswahl (reine Daten). */
@Immutable
data class WidgetChoice(val key: String, val label: String, val appLabel: String, val cellsWide: Int, val cellsHigh: Int)

/** Auswahl aller Widgets installierter Apps, nach App gruppiert. */
@Composable
fun WidgetPickerScreen(
    choices: List<WidgetChoice>,
    preview: suspend (WidgetChoice) -> ImageBitmap?,
    onPick: (WidgetChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = choices.groupBy { it.appLabel }
    Row(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 6.dp, bottom = 8.dp, end = MaiDims.RailWidth),
    ) {
        MainFrame(
            title = stringResource(R.string.tab_widgets),
            meta = choices.size.toString(),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            if (choices.isEmpty()) {
                MaiText(stringResource(R.string.widgets_none), MaiType.Body, Modifier.padding(top = 24.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                groups.forEach { (app, list) ->
                    item(key = "h_$app") {
                        MaiText(app.uppercase(), MaiType.Letter.copy(fontSize = MaiType.Tab.fontSize * 1.3f),
                            Modifier.padding(top = 16.dp, bottom = 4.dp), color = LocalAccent.current.main)
                    }
                    items(list, key = { it.key }) { c -> ChoiceRow(c, preview, onPick) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(c: WidgetChoice, preview: suspend (WidgetChoice) -> ImageBitmap?, onPick: (WidgetChoice) -> Unit) {
    val img by produceState<ImageBitmap?>(null, c.key) { value = preview(c) }
    val shape = ChamferShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 3.dp)
            .background(MaiColors.Panel, shape)
            .glowBorder(shape, MaiColors.Line)
            .clickable(role = Role.Button) { onPick(c) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp, 48.dp), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MaiText(c.label, MaiType.Body.copy(color = MaiColors.Text, fontSize = MaiType.Body.fontSize * 1.1f), maxLines = 2)
            Spacer(Modifier.height(2.dp))
            MaiLabel(stringResource(R.string.widget_size, c.cellsWide, c.cellsHigh), color = MaiColors.Text3)
        }
    }
}
