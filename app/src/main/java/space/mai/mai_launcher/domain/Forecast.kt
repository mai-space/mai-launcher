package space.mai.mai_launcher.domain

import java.time.LocalDate

/**
 * Prognose-Übersicht aus den Widget-Ständen der anderen Mai-Apps (Budget,
 * Fitness, Haushalt). Reine Logik: die Android-Seite liest die Stände
 * (`MaiSummaries`) und übergibt sie hier als einfache Daten. Nichts davon
 * wird im Launcher gespeichert.
 */
object MaiSummaryApps {
    const val BUDGET = "space.mai.mai_budget_planer"
    const val FITNESS = "space.mai.mai_fitness"
    const val HOUSEHOLD = "space.mai.mai_household"
    val all = listOf(BUDGET, FITNESS, HOUSEHOLD)

    /** Authority des SummaryProvider einer App (nie ändern). */
    fun authority(pkg: String) = "$pkg.summary"
}

/** Budget: freier Rest je Monat (fertig formatiert) und unregelmäßige Fälligkeiten. */
data class BudgetSummary(
    val hasData: Boolean,
    /** null = Beträge ausgeblendet. */
    val free: String?,
    val freeNegative: Boolean,
    val upcoming: List<Due>,
) {
    data class Due(val label: String, val amount: String?, val months: List<Int>)
}

/** Fitness: nächstes Training, Wochenziel, Serie. */
data class FitnessSummary(
    val nextName: String?,
    /** „yyyy-MM-dd“ oder null (beliebiger Tag). */
    val nextDay: String?,
    val activeName: String?,
    val weekDone: Int,
    val weekGoal: Int,
    val streak: Int,
)

/** Haushalt: Aufgaben mit Fälligkeit, Müllabfuhr, offene Einkäufe. */
data class HouseholdSummary(
    val chores: List<Chore>,
    val waste: List<Pickup>,
    val shoppingCount: Int,
) {
    data class Chore(val name: String, val due: String, val mine: Boolean)
    data class Pickup(val day: String, val name: String)
}

/** Eine Zeile im Prognose-Panel. Texte kommen als Schlüssel + Werte, die UI übersetzt. */
sealed interface ForecastLine {
    val packageName: String

    /** Freier Rest je Monat; nächste Fälligkeit in [dueInMonths] Monaten (0 = diesen Monat). */
    data class Budget(
        val free: String?,
        val negative: Boolean,
        val dueLabel: String?,
        val dueAmount: String?,
        val dueInMonths: Int?,
    ) : ForecastLine { override val packageName = MaiSummaryApps.BUDGET }

    /** Wochenziel und nächstes Training ([nextInDays]: 0 = heute, null = beliebig). */
    data class Fitness(
        val weekDone: Int,
        val weekGoal: Int,
        val streak: Int,
        val nextName: String?,
        val nextInDays: Int?,
        val active: Boolean,
    ) : ForecastLine {
        override val packageName = MaiSummaryApps.FITNESS
        /** Wochenziel erreichbar? Verbleibende Trainings passen in die restlichen Tage. */
        fun onTrack(daysLeftInWeek: Int): Boolean = weekGoal <= 0 || weekGoal - weekDone <= daysLeftInWeek
    }

    /** Heute fällige (inkl. überfälliger) Aufgaben, nächste Müllabfuhr, offene Einkäufe. */
    data class Household(
        val dueToday: Int,
        val overdue: Int,
        val nextChore: String?,
        val wasteName: String?,
        val wasteInDays: Int?,
        val shopping: Int,
    ) : ForecastLine { override val packageName = MaiSummaryApps.HOUSEHOLD }
}

object Forecast {
    /** Nächste unregelmäßige Fälligkeit ab [today] (Monate Abstand, 0 = dieser Monat). */
    fun nextDue(upcoming: List<BudgetSummary.Due>, today: LocalDate): Pair<BudgetSummary.Due, Int>? =
        upcoming.mapNotNull { d ->
            d.months.filter { it in 1..12 }.minOfOrNull { (it - today.monthValue + 12) % 12 }?.let { d to it }
        }.minByOrNull { it.second }

    fun budget(s: BudgetSummary, today: LocalDate): ForecastLine.Budget? {
        if (!s.hasData) return null
        val next = nextDue(s.upcoming, today)
        return ForecastLine.Budget(s.free, s.freeNegative, next?.first?.label, next?.first?.amount, next?.second)
    }

    fun fitness(s: FitnessSummary, today: LocalDate): ForecastLine.Fitness? {
        if (s.nextName == null && s.weekGoal <= 0 && s.activeName == null) return null
        val days = s.nextDay?.let(::parseDay)?.let { (it.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0) }
        return ForecastLine.Fitness(s.weekDone, s.weekGoal, s.streak, s.activeName ?: s.nextName, days, s.activeName != null)
    }

    fun household(s: HouseholdSummary, today: LocalDate): ForecastLine.Household? {
        val dated = s.chores.mapNotNull { c -> parseDay(c.due)?.let { c to it } }
        val due = dated.filter { !it.second.isAfter(today) }
        val overdue = due.count { it.second.isBefore(today) }
        // Meine zuerst, dann die älteste Fälligkeit.
        val next = due.sortedWith(compareBy({ !it.first.mine }, { it.second })).firstOrNull()?.first?.name
        val waste = s.waste.mapNotNull { p -> parseDay(p.day)?.let { p to it } }
            .filter { !it.second.isBefore(today) }
            .minByOrNull { it.second }
        if (s.chores.isEmpty() && waste == null && s.shoppingCount == 0) return null
        return ForecastLine.Household(
            dueToday = due.size,
            overdue = overdue,
            nextChore = next,
            wasteName = waste?.first?.name,
            wasteInDays = waste?.let { (it.second.toEpochDay() - today.toEpochDay()).toInt() },
            shopping = s.shoppingCount,
        )
    }

    /** Verbleibende Tage dieser Woche inkl. heute (Mo–So). */
    fun daysLeftInWeek(today: LocalDate): Int = 8 - today.dayOfWeek.value

    private fun parseDay(s: String): LocalDate? = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}
