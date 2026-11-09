package space.mai.mai_launcher.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.GlowLevel
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.domain.SoundMode
import space.mai.mai_launcher.system.SystemState

/** Aktionen der Seitenleiste: Tippen = Panel/Schalter, Halten = volle Einstellung. */
class RailActions(
    val onWifi: () -> Unit = {},
    val onWifiLong: () -> Unit = {},
    val onBluetooth: () -> Unit = {},
    val onDnd: () -> Unit = {},
    val onDndLong: () -> Unit = {},
    val onSound: () -> Unit = {},
    val onSoundLong: () -> Unit = {},
    val onTorch: () -> Unit = {},
    val onRotate: () -> Unit = {},
)

private data class RailItem(val label: String, val on: Boolean, val onClick: () -> Unit, val onLong: (() -> Unit)? = null)

/** Seitenleiste außerhalb des Rahmens: Gerätezustand, nicht Inhalt. */
@Composable
fun SideRail(sys: SystemState, actions: RailActions, modifier: Modifier = Modifier) {
    val items = buildList {
        add(RailItem(stringResource(R.string.rail_wifi), sys.wifiConnected, actions.onWifi, actions.onWifiLong))
        add(RailItem(stringResource(R.string.rail_bluetooth), sys.bluetoothOn == true, actions.onBluetooth))
        // Tonmodus: hervorgehoben, wenn nicht „Klingeln“. Antippen schaltet weiter.
        val soundLabel = stringResource(
            when (sys.soundMode) {
                SoundMode.Ring -> R.string.rail_sound_ring
                SoundMode.Vibrate -> R.string.rail_sound_vibrate
                SoundMode.Mute -> R.string.rail_sound_mute
            },
        )
        add(RailItem(soundLabel, sys.soundMode != SoundMode.Ring, actions.onSound, actions.onSoundLong))
        add(RailItem(stringResource(R.string.rail_dnd), sys.dndOn, actions.onDnd, actions.onDndLong))
        if (sys.torchAvailable) add(RailItem(stringResource(R.string.rail_torch), sys.torchOn, actions.onTorch))
        add(RailItem(stringResource(R.string.rail_rotate), sys.autoRotate, actions.onRotate))
    }
    Column(
        modifier.padding(top = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { RailButton(it) }
    }
}

@Composable
private fun RailButton(item: RailItem) {
    val accent = LocalAccent.current.main
    val color = if (item.on) accent else MaiColors.Text3
    val state = stringResource(if (item.on) R.string.rail_state_on else R.string.rail_state_off, item.label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            HexToken(
                contentDescription = state,
                size = 28.dp,
                color = color,
                filled = item.on,
                glow = if (item.on) GlowLevel.Focus else GlowLevel.None,
                onClick = item.onClick,
                onLongClick = item.onLong,
            )
            if (item.on) Box(Modifier.size(6.dp).background(accent, CircleShape))
        }
        MaiLabel(item.label, color = color, align = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
    }
}
