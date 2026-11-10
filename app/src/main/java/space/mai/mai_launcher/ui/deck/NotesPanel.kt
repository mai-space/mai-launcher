package space.mai.mai_launcher.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.LocalFeedback
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.Note
import space.mai.mai_launcher.ui.UndoKind

/** Notizen aus der Befehlszeile („note …“); Haken = erledigt (rückgängig möglich). */
@Composable
fun NotesPanel(notes: List<Note>, onDone: (Note) -> Unit) {
    val shown = notes.takeLast(MAX_SHOWN).reversed()
    MaiPanel(
        title = stringResource(R.string.panel_notes),
        meta = pluralStringResource(R.plurals.notes_count, notes.size, notes.size),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(4.dp))
        shown.forEach { n -> NoteRow(n, onDone) }
        if (notes.size > MAX_SHOWN) {
            MaiLabel(stringResource(R.string.notes_more, notes.size - MAX_SHOWN), color = MaiColors.Text3)
        }
    }
}

@Composable
private fun NoteRow(note: Note, onDone: (Note) -> Unit) {
    val feedback = LocalFeedback.current
    val accent = LocalAccent.current.main
    Row(Modifier.fillMaxWidth().heightIn(min = MaiDims.ListRow), verticalAlignment = Alignment.CenterVertically) {
        MaiText("›", MaiType.Mono, color = accent)
        Spacer(Modifier.width(10.dp))
        MaiText(note.text, MaiType.Body, Modifier.weight(1f), color = MaiColors.Text, maxLines = 3)
        Box(
            Modifier
                .size(MaiDims.MinTouch)
                .clickable(role = Role.Checkbox, onClickLabel = stringResource(R.string.notes_done)) { feedback.tap(); onDone(note) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).glowBorder(ChamferShape(4.dp), accent), contentAlignment = Alignment.Center) {}
        }
    }
}

/** Leiste über der Daumenzone: „Widget entfernt · RÜCKGÄNGIG“ (10 s). */
@Composable
fun UndoBar(kind: UndoKind, onUndo: () -> Unit) {
    val shape = ChamferShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .glowBorder(shape, MaiColors.Line)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaiText(
            stringResource(
                when (kind) {
                    UndoKind.Widget -> R.string.undo_widget
                    UndoKind.Feed -> R.string.undo_feed
                    UndoKind.Note -> R.string.undo_note
                },
            ),
            MaiType.Body, Modifier.weight(1f), color = MaiColors.Text2,
        )
        EditChip(stringResource(R.string.undo), onUndo)
    }
}

private const val MAX_SHOWN = 5
