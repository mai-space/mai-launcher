package space.mai.mai_launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaiWidgetsTest {
    private fun p(pkg: String, cls: String) = ProviderRef(pkg, "$pkg.$cls")

    private val installed = listOf(
        p("space.mai.mai_household", "widgets.ChoresWidgetProvider"),
        p("space.mai.mai_household", "widgets.ShoppingWidgetProvider"),
        p("space.mai.mai_household", "widgets.MealsWidgetProvider"),
        p("space.mai.mai_fitness", "widgets.NextWorkoutWidgetProvider"),
        p("space.mai.mai_stay_in_touch", "widgets.TodayWidgetProvider"),
        p("com.example.weather", "Weather"),
        p("space.mai.mai_launcher", "Self"),
    )

    @Test fun picksPreferredWidgetPerMaiAppInDeckOrder() {
        val r = MaiWidgets.pending(installed, emptySet(), emptySet())
        assertEquals(
            listOf(
                p("space.mai.mai_stay_in_touch", "widgets.TodayWidgetProvider"),
                p("space.mai.mai_household", "widgets.ShoppingWidgetProvider"),
                p("space.mai.mai_fitness", "widgets.NextWorkoutWidgetProvider"),
            ),
            r,
        )
    }

    @Test fun skipsAppsAlreadyOnDeckOrSeen() {
        val r = MaiWidgets.pending(installed, setOf("space.mai.mai_fitness"), setOf("space.mai.mai_stay_in_touch"))
        assertEquals(listOf(p("space.mai.mai_household", "widgets.ShoppingWidgetProvider")), r)
    }

    @Test fun unknownMaiAppUsesFirstWidgetByClassAndComesLast() {
        val extra = listOf(
            p("space.mai.mai_zeta", "B"),
            p("space.mai.mai_zeta", "A"),
            p("space.mai.mai_alpha", "Only"),
        )
        val r = MaiWidgets.pending(extra + installed, emptySet(), emptySet())
        assertEquals(p("space.mai.mai_alpha", "Only"), r[r.size - 2])
        assertEquals(p("space.mai.mai_zeta", "A"), r.last())
    }

    @Test fun ignoresOtherAppsAndTheLauncherItself() {
        val r = MaiWidgets.pending(installed, emptySet(), emptySet())
        assertTrue(r.none { it.packageName == "com.example.weather" || it.packageName == MaiWidgets.LAUNCHER })
    }
}
