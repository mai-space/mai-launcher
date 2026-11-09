package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastTest {
    private val today = LocalDate.of(2026, 11, 9) // Montag

    @Test fun budgetNextDueWrapsAroundTheYear() {
        val s = BudgetSummary(
            hasData = true, free = "340 €", freeNegative = false,
            upcoming = listOf(
                BudgetSummary.Due("Kfz-Versicherung", "420 €", listOf(1)),
                BudgetSummary.Due("Rundfunk", "55 €", listOf(2, 5, 8, 11)),
                BudgetSummary.Due("Kaputt", null, listOf(13)),
            ),
        )
        val line = Forecast.budget(s, today)!!
        assertEquals("340 €", line.free)
        assertEquals("Rundfunk", line.dueLabel)
        assertEquals(0, line.dueInMonths)
        assertEquals(
            "Kfz-Versicherung" to 2,
            Forecast.nextDue(s.upcoming.take(1), today)!!.let { it.first.label to it.second },
        )
        assertNull(Forecast.budget(s.copy(hasData = false), today))
    }

    @Test fun budgetHiddenAmountsStayHidden() {
        val line = Forecast.budget(BudgetSummary(true, null, true, emptyList()), today)!!
        assertNull(line.free)
        assertTrue(line.negative)
        assertNull(line.dueLabel)
    }

    @Test fun fitnessNextDayAndWeekGoal() {
        val s = FitnessSummary("Push", "2026-11-10", null, weekDone = 0, weekGoal = 3, streak = 4)
        val line = Forecast.fitness(s, today)!!
        assertEquals(1, line.nextInDays)
        assertEquals("Push", line.nextName)
        assertTrue(line.onTrack(Forecast.daysLeftInWeek(today)))
        // Sonntag: noch 3 offen, nur 1 Tag übrig.
        assertFalse(line.onTrack(Forecast.daysLeftInWeek(LocalDate.of(2026, 11, 15))))
        val running = Forecast.fitness(s.copy(activeName = "Pull"), today)!!
        assertTrue(running.active)
        assertEquals("Pull", running.nextName)
        assertNull(Forecast.fitness(FitnessSummary(null, null, null, 0, 0, 0), today))
    }

    @Test fun householdCountsDueAndOverdue() {
        val s = HouseholdSummary(
            chores = listOf(
                HouseholdSummary.Chore("Bad putzen", "2026-11-07", mine = false),
                HouseholdSummary.Chore("Pflanzen gießen", "2026-11-09", mine = true),
                HouseholdSummary.Chore("Fenster", "2026-11-12", mine = true),
                HouseholdSummary.Chore("Kaputt", "irgendwann", mine = true),
            ),
            waste = listOf(
                HouseholdSummary.Pickup("2026-11-08", "Restmüll"),
                HouseholdSummary.Pickup("2026-11-10", "Papier"),
            ),
            shoppingCount = 7,
        )
        val line = Forecast.household(s, today)!!
        assertEquals(2, line.dueToday)
        assertEquals(1, line.overdue)
        assertEquals("Pflanzen gießen", line.nextChore)
        assertEquals("Papier", line.wasteName)
        assertEquals(1, line.wasteInDays)
        assertEquals(7, line.shopping)
        assertNull(Forecast.household(HouseholdSummary(emptyList(), emptyList(), 0), today))
    }

    @Test fun authoritiesAreStable() {
        assertEquals("space.mai.mai_fitness.summary", MaiSummaryApps.authority(MaiSummaryApps.FITNESS))
        assertEquals(3, MaiSummaryApps.all.size)
    }
}
