package space.mai.mai_launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.GripLine
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.LocalFeedback
import space.mai.mai_launcher.domain.HapticLevel
import space.mai.mai_launcher.domain.SoundLevel
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.GlowLevel
import space.mai.mai_launcher.design.swatch
import space.mai.mai_launcher.domain.AccentChoice
import space.mai.mai_launcher.domain.Appearance
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.ui.AppTile
import space.mai.mai_launcher.ui.AppToken
import space.mai.mai_launcher.ui.deck.EditChip
import space.mai.mai_launcher.ui.boards.boardKindName
import space.mai.mai_launcher.domain.BoardKind
import space.mai.mai_launcher.domain.BoardTemplates
import space.mai.mai_launcher.domain.PageRef
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource

/** Ein Dock-Platz: belegte App und ob sie selbst gewählt (nicht System-Standard) ist. */
@Immutable
data class DockSlot(val tile: AppTile?, val custom: Boolean)

/** Ergebnis von Export/Import. */
@Immutable
sealed interface BackupStatus {
    data object Exported : BackupStatus
    data object Failed : BackupStatus
    data class Imported(val dock: Int, val feeds: Int, val widgets: Int, val boards: Int = 0) : BackupStatus
}

/** Alles, was die Launcher-Einstellungen zeigen (reine Daten). */
@Immutable
data class SettingsState(
    val dock: List<DockSlot>,
    val isHome: Boolean = true,
    val signals: Boolean = true,
    val calendar: Boolean = true,
    val contacts: Boolean = true,
    val assistant: Boolean = false,
    val signalsShowText: Boolean = false,
    val forecast: Boolean = true,
    val haptics: HapticLevel = HapticLevel.Light,
    val sounds: SoundLevel = SoundLevel.Off,
    val widgetCount: Int = 0,
    val feedCount: Int = 0,
    val version: String = "",
    val backup: BackupStatus? = null,
    val appearance: Appearance = Appearance(),
    /** Wisch-Seiten in ihrer Reihenfolge (Comms, Deck, Media und eigene Dashboards). */
    val pages: List<PageEntry> = emptyList(),
    val canAddBoard: Boolean = true,
)

/** Eine Seite in der Liste: eingebaut (kind = null) oder eigenes Dashboard. */
@Immutable
data class PageEntry(
    val page: PageRef,
    val title: String? = null,
    val kind: BoardKind? = null,
    val accent: AccentChoice? = null,
    val appCount: Int = 0,
)

class SettingsActions(
    val onDockSlot: (Int) -> Unit = {},
    val onDockReset: () -> Unit = {},
    val onArrangeDeck: () -> Unit = {},
    val onAddWidget: () -> Unit = {},
    val onToggleSignalText: () -> Unit = {},
    val onForecast: () -> Unit = {},
    val onHaptics: () -> Unit = {},
    val onSounds: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onCalendar: () -> Unit = {},
    val onContacts: () -> Unit = {},
    val onAssistant: () -> Unit = {},
    val onSystemSettings: () -> Unit = {},
    val onAppInfo: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onAccent: (AccentChoice) -> Unit = {},
    val onContrast: () -> Unit = {},
    val onReduceMotion: () -> Unit = {},
    val onLarge: () -> Unit = {},
    val onMovePage: (PageRef, Int) -> Unit = { _, _ -> },
    val onOpenPage: (PageRef) -> Unit = {},
    val onNewBoard: (BoardKind) -> Unit = {},
)

/** Launcher-Einstellungen: Dock, Deck, Freigaben, System. Über die Seitenleiste erreichbar. */
@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
            // Nach rechts wischen = zurück zum Deck.
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > threshold) actions.onClose() },
                ) { _, dx -> total += dx }
            },
    ) {
        MainFrame(
            title = stringResource(R.string.settings_title).uppercase(),
            meta = state.version.takeIf { it.isNotEmpty() }?.let { "V$it" },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DockPanel(state.dock, actions)
                AppearancePanel(state.appearance, actions)
                if (state.pages.isNotEmpty()) BoardsPanel(state, actions)
                MaiPanel(stringResource(R.string.settings_deck), Modifier.fillMaxWidth()) {
                    SettingRow(stringResource(R.string.settings_arrange), stringResource(R.string.settings_arrange_body),
                        stringResource(R.string.settings_open), actions.onArrangeDeck)
                    SettingRow(stringResource(R.string.settings_add_widget), stringResource(R.string.settings_widgets_count, state.widgetCount),
                        "+", actions.onAddWidget)
                    SettingRow(stringResource(R.string.settings_signal_text), stringResource(R.string.settings_signal_text_body),
                        onOff(state.signalsShowText), actions.onToggleSignalText, on = state.signalsShowText)
                    SettingRow(stringResource(R.string.settings_forecast), stringResource(R.string.settings_forecast_body),
                        onOff(state.forecast), actions.onForecast, on = state.forecast)
                }
                MaiPanel(stringResource(R.string.settings_feedback), Modifier.fillMaxWidth()) {
                    SettingRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_body),
                        hapticLabel(state.haptics), actions.onHaptics, on = state.haptics != HapticLevel.Off)
                    SettingRow(stringResource(R.string.settings_sounds), stringResource(R.string.settings_sounds_body),
                        soundLabel(state.sounds), actions.onSounds, on = state.sounds != SoundLevel.Off)
                }
                MaiPanel(stringResource(R.string.settings_access), Modifier.fillMaxWidth()) {
                    SettingRow(stringResource(R.string.settings_home), stringResource(R.string.settings_home_body),
                        access(state.isHome), actions.onHome, on = state.isHome)
                    SettingRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_body),
                        access(state.signals), actions.onNotifications, on = state.signals)
                    SettingRow(stringResource(R.string.settings_calendar), stringResource(R.string.setup_calendar),
                        access(state.calendar), actions.onCalendar, on = state.calendar)
                    SettingRow(stringResource(R.string.settings_contacts), stringResource(R.string.settings_contacts_body),
                        access(state.contacts), actions.onContacts, on = state.contacts)
                    SettingRow(stringResource(R.string.settings_assistant), stringResource(R.string.settings_assistant_body),
                        if (state.assistant) stringResource(R.string.settings_on) else stringResource(R.string.settings_open), actions.onAssistant)
                }
                MaiPanel(stringResource(R.string.settings_backup), Modifier.fillMaxWidth()) {
                    SettingRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_body),
                        stringResource(R.string.settings_save), actions.onExport)
                    SettingRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_body),
                        stringResource(R.string.settings_open), actions.onImport)
                    state.backup?.let { b ->
                        val (text, color) = when (b) {
                            BackupStatus.Exported -> stringResource(R.string.settings_backup_exported) to LocalAccent.current.main
                            BackupStatus.Failed -> stringResource(R.string.settings_backup_failed) to MaiColors.Amber
                            is BackupStatus.Imported -> {
                                val base = stringResource(R.string.settings_backup_imported, b.dock, b.feeds, b.widgets)
                                val boards = if (b.boards > 0) " " + stringResource(R.string.settings_backup_boards, b.boards) else ""
                                (base + boards) to LocalAccent.current.main
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        MaiText(text, MaiType.Body, color = color, maxLines = 3)
                    }
                }
                MaiPanel(stringResource(R.string.settings_system), Modifier.fillMaxWidth()) {
                    SettingRow(stringResource(R.string.settings_android), null, stringResource(R.string.settings_open), actions.onSystemSettings)
                    SettingRow(stringResource(R.string.settings_app_info), null, stringResource(R.string.settings_open), actions.onAppInfo)
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            GripLine()
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                EditChip(stringResource(R.string.edit_done), actions.onClose)
            }
        }
    }
}

@Composable
private fun DockPanel(dock: List<DockSlot>, actions: SettingsActions) {
    MaiPanel(
        title = stringResource(R.string.settings_dock),
        meta = stringResource(R.string.settings_dock_meta),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            dock.forEachIndexed { i, slot ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val tile = slot.tile
                    if (tile != null) {
                        AppToken(tile, size = 44.dp) { actions.onDockSlot(i) }
                    } else {
                        HexToken(
                            contentDescription = stringResource(R.string.dock_slot_empty),
                            size = 44.dp, text = "+", color = MaiColors.Text3, filled = false,
                            onClick = { actions.onDockSlot(i) },
                        )
                        MaiLabel(stringResource(R.string.dock_slot_empty_short), color = MaiColors.Text3, align = TextAlign.Center)
                    }
                    MaiLabel(
                        stringResource(if (slot.custom) R.string.settings_dock_custom else R.string.settings_dock_system),
                        color = if (slot.custom) LocalAccent.current.main else MaiColors.Text3,
                        align = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MaiText(stringResource(R.string.settings_dock_body), MaiType.Body, color = MaiColors.Text2, maxLines = 3)
        if (dock.any { it.custom }) {
            Spacer(Modifier.height(10.dp))
            EditChip(stringResource(R.string.settings_dock_reset), actions.onDockReset, MaiColors.Text2)
        }
    }
}

/** Grundfarbe (fünf Sechsecke) und Komfort-Modi. */
@Composable
private fun AppearancePanel(a: Appearance, actions: SettingsActions) {
    MaiPanel(
        title = stringResource(R.string.settings_appearance),
        meta = accentName(a.accent).uppercase(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            AccentChoice.entries.forEach { c ->
                val selected = c == a.accent
                val name = accentName(c)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HexToken(
                        contentDescription = if (selected) stringResource(R.string.settings_accent_selected, name) else name,
                        size = 40.dp,
                        text = if (selected) "●" else null,
                        color = c.swatch,
                        filled = selected,
                        glow = if (selected) GlowLevel.Focus else GlowLevel.None,
                        onClick = { actions.onAccent(c) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        MaiText(stringResource(R.string.settings_accent_body), MaiType.Body, color = MaiColors.Text3, maxLines = 2)
        SettingRow(stringResource(R.string.settings_contrast), stringResource(R.string.settings_contrast_body),
            onOff(a.highContrast), actions.onContrast)
        SettingRow(stringResource(R.string.settings_motion), stringResource(R.string.settings_motion_body),
            onOff(a.reduceMotion), actions.onReduceMotion)
        SettingRow(stringResource(R.string.settings_large), stringResource(R.string.settings_large_body),
            onOff(a.large), actions.onLarge)
    }
}

/**
 * Dashboards: alle Wisch-Seiten in Reihenfolge mit ◂/▸ zum Verschieben,
 * darunter Vorlagen für ein neues Dashboard.
 */
@Composable
private fun BoardsPanel(state: SettingsState, actions: SettingsActions) {
    var choosing by remember { mutableStateOf(false) }
    MaiPanel(
        title = stringResource(R.string.boards_title),
        meta = stringResource(R.string.boards_meta, state.pages.size),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(4.dp))
        MaiText(stringResource(R.string.boards_body), MaiType.Body, color = MaiColors.Text3, maxLines = 3)
        state.pages.forEachIndexed { i, p ->
            val name = when (p.page) {
                PageRef.Comms -> stringResource(R.string.tab_comms)
                PageRef.Deck -> stringResource(R.string.tab_deck)
                PageRef.Media -> stringResource(R.string.tab_media)
                is PageRef.OfBoard -> (p.title ?: boardKindName(p.kind ?: BoardKind.Custom)).uppercase()
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = MaiDims.ListRow),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = p.accent?.swatch ?: MaiColors.Text3
                Box(Modifier.size(3.dp, 16.dp).background(color))
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f).clickable(role = Role.Button) { actions.onOpenPage(p.page) }.padding(vertical = 6.dp),
                ) {
                    MaiLabel(name, color = MaiColors.Text)
                    if (p.page is PageRef.OfBoard) {
                        MaiText(pluralStringResource(R.plurals.apps_count, p.appCount, p.appCount), MaiType.Body, color = MaiColors.Text3)
                    } else if (p.page == PageRef.Deck) {
                        MaiText(stringResource(R.string.boards_home), MaiType.Body, color = MaiColors.Text3)
                    }
                }
                HexToken(
                    contentDescription = stringResource(R.string.board_move_page_left, name),
                    size = 28.dp, text = "◂", color = if (i > 0) LocalAccent.current.main else MaiColors.Text3, filled = false,
                    onClick = if (i > 0) ({ actions.onMovePage(p.page, -1) }) else null,
                )
                HexToken(
                    contentDescription = stringResource(R.string.board_move_page_right, name),
                    size = 28.dp, text = "▸", color = if (i < state.pages.lastIndex) LocalAccent.current.main else MaiColors.Text3,
                    filled = false,
                    onClick = if (i < state.pages.lastIndex) ({ actions.onMovePage(p.page, 1) }) else null,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!state.canAddBoard) {
            MaiText(stringResource(R.string.boards_full), MaiType.Body, color = MaiColors.Amber, maxLines = 2)
        } else if (!choosing) {
            EditChip("+ " + stringResource(R.string.boards_new), { choosing = true })
        } else {
            MaiLabel(stringResource(R.string.boards_template), color = MaiColors.Text2)
            Spacer(Modifier.height(6.dp))
            BoardKind.templates.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k -> EditChip(boardKindName(k), { choosing = false; actions.onNewBoard(k) }, BoardTemplates.accent(k).swatch) }
                }
            }
            MaiText(stringResource(R.string.boards_template_body), MaiType.Body, color = MaiColors.Text3, maxLines = 3)
        }
    }
}

@Composable
private fun accentName(c: AccentChoice) = stringResource(
    when (c) {
        AccentChoice.Ion -> R.string.accent_ion
        AccentChoice.Signal -> R.string.accent_signal
        AccentChoice.Amber -> R.string.accent_amber
        AccentChoice.Violet -> R.string.accent_violet
        AccentChoice.White -> R.string.accent_white
    },
)

/** Eine Zeile: Titel, optional Erklärung, rechts der Zustand. Ganze Zeile tippbar. */
@Composable
private fun SettingRow(title: String, body: String?, value: String, onClick: () -> Unit, on: Boolean? = null) {
    val feedback = LocalFeedback.current
    val valueColor: Color = when (on) {
        null, true -> LocalAccent.current.main
        false -> MaiColors.Amber
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = maxOf(52.dp, MaiDims.ListRow))
            .clickable(role = Role.Button) { feedback.tap(); onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MaiText(title, MaiType.Body, color = MaiColors.Text)
            if (body != null) MaiText(body, MaiType.Body, color = MaiColors.Text3, maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        MaiLabel(value, color = valueColor)
    }
}

@Composable
private fun onOff(on: Boolean) = stringResource(if (on) R.string.settings_on else R.string.settings_off)

@Composable
private fun access(granted: Boolean) = stringResource(if (granted) R.string.settings_granted else R.string.settings_allow)

@Composable
private fun hapticLabel(level: HapticLevel) = stringResource(
    when (level) {
        HapticLevel.Off -> R.string.settings_off
        HapticLevel.Light -> R.string.settings_level_light
        HapticLevel.Strong -> R.string.settings_level_strong
    },
)

@Composable
private fun soundLabel(level: SoundLevel) = stringResource(
    when (level) {
        SoundLevel.Off -> R.string.settings_off
        SoundLevel.Quiet -> R.string.settings_level_quiet
        SoundLevel.Medium -> R.string.settings_level_medium
        SoundLevel.Loud -> R.string.settings_level_loud
    },
)
