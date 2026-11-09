package space.mai.mai_launcher.domain

/** Ein Widget-Anbieter, nur Paket und Klasse (ohne Android-Typen, damit testbar). */
data class ProviderRef(val packageName: String, val className: String)

/**
 * Widgets der eigenen Mai-Apps landen automatisch auf dem Deck: je App ein
 * Standard-Widget, genau einmal. Was der Nutzer danach entfernt, bleibt weg
 * („gesehen“-Liste, Schlüssel `mai_widgets_seen`).
 */
object MaiWidgets {
    const val PREFIX = "space.mai."
    const val LAUNCHER = "space.mai.mai_launcher"

    /** Bevorzugtes Widget je App, in Deck-Reihenfolge. Klassen nie umbenennen (in den Apps). */
    val PREFERRED: List<ProviderRef> = listOf(
        ProviderRef("space.mai.mai_stay_in_touch", "space.mai.mai_stay_in_touch.widgets.TodayWidgetProvider"),
        ProviderRef("space.mai.mai_doctor_hub", "space.mai.mai_doctor_hub.widgets.AppointmentWidget"),
        ProviderRef("space.mai.mai_household", "space.mai.mai_household.widgets.ShoppingWidgetProvider"),
        ProviderRef("space.mai.mai_fitness", "space.mai.mai_fitness.widgets.NextWorkoutWidgetProvider"),
        ProviderRef("space.mai.mai_budget_planer", "space.mai.mai_budget_planer.widget.BudgetWidgetProvider"),
    )

    fun isMai(packageName: String) = packageName.startsWith(PREFIX) && packageName != LAUNCHER

    /**
     * Welche Widgets noch automatisch hinzukommen: je installierter Mai-App, die
     * weder schon ein Widget auf dem Deck hat noch bereits „gesehen“ ist, das
     * bevorzugte Widget (sonst das erste nach Klassenname). Bekannte Apps in
     * [PREFERRED]-Reihenfolge, neue Mai-Apps danach alphabetisch.
     */
    fun pending(installed: List<ProviderRef>, placedPackages: Set<String>, seen: Set<String>): List<ProviderRef> {
        val byPackage = installed.filter { isMai(it.packageName) }.groupBy { it.packageName }
        val order = PREFERRED.map { it.packageName }
        return byPackage.keys
            .filter { it !in placedPackages && it !in seen }
            .sortedWith(compareBy({ order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
            .map { pkg ->
                val options = byPackage.getValue(pkg)
                options.firstOrNull { it in PREFERRED } ?: options.minBy { it.className }
            }
    }
}
