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
import space.mai.mai_launcher.design.Glyphs
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.domain.SignalDigest
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
    val onNotifications: () -> Unit = {},
    val onSettings: () -> Unit = {},
)

private data class RailItem(val label: String, val on: Boolean, val onClick: () -> Unit, val onLong: (() -> Unit)? = null)

/** Seitenleiste außerhalb des Rahmens: Gerätezustand, nicht Inhalt. */
@Composable
fun SideRail(sys: SystemState, actions: RailActions, modifier: Modifier = Modifier, notifications: Int = 0) {
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
        modifier.padding(top = 110.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { RailButton(it) }
        Spacer(Modifier.weight(1f))
        // Benachrichtigungen und Launcher-Einstellungen: unten, abgesetzt von den Geräteschaltern.
        GlyphButton(
            stringResource(R.string.rail_notifications), stringResource(R.string.rail_notifications_desc),
            Glyphs.Bell, actions.onNotifications, badge = SignalDigest.badgeText(notifications),
        )
        GlyphButton(stringResource(R.string.rail_settings), stringResource(R.string.settings_title), Glyphs.Sliders, actions.onSettings)
    }
}

@Composable
private fun GlyphButton(
    label: String,
    description: String,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.graphics.Color) -> Unit,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val accent = LocalAccent.current.main
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HexToken(
            contentDescription = description,
            size = 28.dp,
            glyph = glyph,
            color = accent,
            filled = false,
            badge = badge,
            onClick = onClick,
        )
        MaiLabel(label, color = accent, align = TextAlign.Center)
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
