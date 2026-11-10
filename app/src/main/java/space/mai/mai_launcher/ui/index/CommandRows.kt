package space.mai.mai_launcher.ui.index

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.GlowLevel
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.LocalFeedback
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.Command
import space.mai.mai_launcher.domain.Contact
import space.mai.mai_launcher.domain.SettingPage

/** Dauer lesbar: „10 min“, „1 h 30 min“, „90 s“. */
fun durationText(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildList {
        if (h > 0) add("$h h")
        if (m > 0) add("$m min")
        if (s > 0 || (h == 0 && m == 0)) add("$s s")
    }.joinToString(" ")
}

@Composable
private fun settingName(page: SettingPage): String = stringResource(
    when (page) {
        SettingPage.Wifi -> R.string.setting_wifi
        SettingPage.Bluetooth -> R.string.setting_bluetooth
        SettingPage.Display -> R.string.setting_display
        SettingPage.Sound -> R.string.setting_sound
        SettingPage.Battery -> R.string.setting_battery
        SettingPage.Apps -> R.string.setting_apps
        SettingPage.Location -> R.string.setting_location
        SettingPage.Notifications -> R.string.setting_notifications
        SettingPage.Storage -> R.string.setting_storage
        SettingPage.Settings -> R.string.setting_all
    },
)

/** Eine Zeile der Befehlszeile: Kennung links, Text, Hinweis rechts. */
@Composable
fun CommandRow(cmd: Command, highlighted: Boolean, onRun: (Command) -> Unit) {
    val accent = LocalAccent.current.main
    val (tag, text, hint) = when (cmd) {
        is Command.Answer -> Triple("=", cmd.value, stringResource(R.string.cmd_copy))
        is Command.Timer -> Triple(stringResource(R.string.cmd_tag_timer), durationText(cmd.seconds), null)
        is Command.Alarm -> Triple(stringResource(R.string.cmd_tag_alarm), "%02d:%02d".format(cmd.hour, cmd.minute), null)
        Command.Stopwatch -> Triple(stringResource(R.string.cmd_tag_clock), stringResource(R.string.cmd_stopwatch), null)
        is Command.Setting -> Triple(stringResource(R.string.cmd_tag_setting), settingName(cmd.page), null)
        is Command.Dial -> Triple(stringResource(R.string.cmd_tag_dial), cmd.number, null)
        is Command.WebSearch -> Triple(stringResource(R.string.cmd_tag_web), stringResource(R.string.cmd_web, cmd.query), null)
    }
    ResultRow(tag, text, hint, highlighted, if (cmd is Command.WebSearch) MaiColors.Text2 else accent) { onRun(cmd) }
}

/** Kontakt als Treffer: Name, darunter Anrufen/Schreiben. */
@Composable
fun ContactRow(contact: Contact, highlighted: Boolean, onCall: (Contact) -> Unit, onText: (Contact) -> Unit) {
    val signal = MaiColors.Signal
    val shape = ChamferShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 1.dp, bottom = 1.dp)
            .then(if (highlighted) Modifier.background(MaiColors.Panel2, shape).glowBorder(shape, signal, 1.dp, GlowLevel.Focus) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MaiLabel(stringResource(R.string.cmd_tag_contact), Modifier.widthIn(min = 64.dp), color = signal)
            MaiText(contact.name, MaiType.Title, Modifier.weight(1f), maxLines = 1)
        }
        if (contact.primaryNumber != null) {
            Row(Modifier.padding(start = 64.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip(stringResource(R.string.comms_call), signal) { onCall(contact) }
                SmallChip(stringResource(R.string.comms_text), signal) { onText(contact) }
            }
        }
    }
}

@Composable
private fun ResultRow(tag: String, text: String, hint: String?, highlighted: Boolean, color: Color, onClick: () -> Unit) {
    val shape = ChamferShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 1.dp, bottom = 1.dp)
            .then(if (highlighted) Modifier.background(MaiColors.Panel2, shape).glowBorder(shape, color, 1.dp, GlowLevel.Focus) else Modifier)
            .heightIn(min = MaiDims.ListRow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaiLabel(tag, Modifier.widthIn(min = 64.dp), color = color)
        MaiText(text, if (tag == "=") MaiType.Mono.copy(color = MaiColors.Text, fontSize = MaiType.Title.fontSize) else MaiType.Title, Modifier.weight(1f), maxLines = 2)
        if (hint != null) { Spacer(Modifier.width(8.dp)); MaiLabel(hint, color = MaiColors.Text3) }
        if (highlighted) { Spacer(Modifier.width(8.dp)); MaiLabel("↵", color = color) }
    }
}

@Composable
fun SmallChip(text: String, color: Color, onClick: () -> Unit) {
    val feedback = LocalFeedback.current
    val shape = ChamferShape(6.dp)
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .background(MaiColors.Panel, shape)
            .glowBorder(shape, color)
            .clickable(role = Role.Button) { feedback.tap(); onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { MaiLabel(text, color = color) }
}
