package space.mai.mai_launcher.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.LocalFeedback
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.rememberTicking
import space.mai.mai_launcher.domain.ForecastLine
import space.mai.mai_launcher.ui.AppTile
import space.mai.mai_launcher.ui.rememberAppIcon

/**
 * Prognose aus den Mai-Apps: Budget (frei je Monat, nächste Fälligkeit),
 * Fitness (Wochenziel, nächstes Training), Haushalt (heute fällig, Müll,
 * Einkauf). Tippen öffnet die jeweilige App.
 */
@Composable
fun ForecastPanel(lines: List<ForecastLine>, apps: Map<String, AppTile>, daysLeftInWeek: Int, onApp: (AppTile) -> Unit) {
    MaiPanel(stringResource(R.string.panel_forecast), Modifier.fillMaxWidth(), meta = stringResource(R.string.forecast_meta)) {
        Spacer(Modifier.height(4.dp))
        lines.forEach { line ->
            val tile = apps[line.packageName]
            when (line) {
                is ForecastLine.Budget -> ForecastRow(
                    tile, onApp,
                    label = stringResource(R.string.forecast_budget),
                    value = line.free ?: stringResource(R.string.forecast_hidden),
                    valueColor = if (line.negative) MaiColors.Alert else MaiColors.Text,
                    detail = line.dueLabel?.let { label ->
                        listOfNotNull(label, line.dueAmount, line.dueInMonths?.let { inMonths(it) }).joinToString(" · ")
                            .let { stringResource(R.string.forecast_next, it) }
                    },
                )
                is ForecastLine.Fitness -> ForecastRow(
                    tile, onApp,
                    label = stringResource(R.string.forecast_fitness),
                    value = if (line.weekGoal > 0) "${line.weekDone} / ${line.weekGoal}" else line.weekDone.toString(),
                    valueColor = MaiColors.Text,
                    detail = listOfNotNull(
                        line.nextName?.let { name ->
                            if (line.active) stringResource(R.string.forecast_running, name)
                            else stringResource(R.string.forecast_next, listOfNotNull(name, line.nextInDays?.let { inDays(it) }).joinToString(" · "))
                        },
                        if (line.streak > 1) pluralStringResource(R.plurals.forecast_streak, line.streak, line.streak) else null,
                    ).joinToString(" · ").ifEmpty { null },
                    warning = if (!line.onTrack(daysLeftInWeek)) stringResource(R.string.forecast_week_at_risk) else null,
                )
                is ForecastLine.Household -> ForecastRow(
                    tile, onApp,
                    label = stringResource(R.string.forecast_household),
                    value = rememberTicking(line.dueToday).toString(),
                    valueColor = MaiColors.Text,
                    detail = listOfNotNull(
                        line.nextChore,
                        line.wasteName?.let { w -> line.wasteInDays?.let { "$w ${inDays(it)}" } },
                        if (line.shopping > 0) pluralStringResource(R.plurals.forecast_shopping, line.shopping, line.shopping) else null,
                    ).joinToString(" · ").ifEmpty { null },
                    warning = if (line.overdue > 0) pluralStringResource(R.plurals.forecast_overdue, line.overdue, line.overdue) else null,
                )
            }
        }
    }
}

@Composable
private fun ForecastRow(
    tile: AppTile?,
    onApp: (AppTile) -> Unit,
    label: String,
    value: String,
    valueColor: Color,
    detail: String?,
    warning: String? = null,
) {
    val feedback = LocalFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (tile != null) Modifier.clickable(role = Role.Button) { feedback.tap(); onApp(tile) } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tile != null) {
            val icon = rememberAppIcon(tile.key)
            HexToken(tile.label, size = 32.dp, text = if (icon == null) tile.monogram else null, icon = icon)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            MaiLabel(label, color = LocalAccent.current.main)
            MaiText(value, MaiType.Title.copy(fontFamily = MaiType.Mono.fontFamily), color = valueColor, maxLines = 1)
            if (detail != null) MaiText(detail, MaiType.Body, color = MaiColors.Text2, maxLines = 2)
            if (warning != null) MaiLabel(warning, color = MaiColors.Amber)
        }
    }
}

@Composable
private fun inDays(days: Int): String = when (days) {
    0 -> stringResource(R.string.forecast_today)
    1 -> stringResource(R.string.forecast_tomorrow)
    else -> pluralStringResource(R.plurals.forecast_in_days, days, days)
}

@Composable
private fun inMonths(months: Int): String = when (months) {
    0 -> stringResource(R.string.forecast_this_month)
    1 -> stringResource(R.string.forecast_next_month)
    else -> pluralStringResource(R.plurals.forecast_in_months, months, months)
}
