package space.mai.mai_launcher.summary

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import space.mai.mai_launcher.domain.BudgetSummary
import space.mai.mai_launcher.domain.FitnessSummary
import space.mai.mai_launcher.domain.HouseholdSummary
import space.mai.mai_launcher.domain.MaiSummaryApps

/** Gelesene Stände; null = App fehlt, kein Zugriff (andere Signatur) oder noch kein Stand. */
data class MaiSummaries(
    val budget: BudgetSummary? = null,
    val fitness: FitnessSummary? = null,
    val household: HouseholdSummary? = null,
)

/**
 * Liest die Widget-Stände der anderen Mai-Apps über deren `SummaryProvider`.
 * Die Apps geben nur an Mai-Apps mit gleicher Signatur heraus. Nur lesen,
 * nur im Speicher – nichts davon wird gespeichert oder verschickt.
 */
class MaiSummaryReader(private val context: Context) {

    suspend fun read(): MaiSummaries = withContext(Dispatchers.IO) {
        MaiSummaries(
            budget = json(MaiSummaryApps.BUDGET)?.let(::budget),
            fitness = json(MaiSummaryApps.FITNESS)?.let(::fitness),
            household = json(MaiSummaryApps.HOUSEHOLD)?.let(::household),
        )
    }

    private fun json(pkg: String): JSONObject? = runCatching {
        val uri = Uri.parse("content://${MaiSummaryApps.authority(pkg)}/snapshot")
        context.contentResolver.query(uri, arrayOf(COLUMN), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            c.getString(c.getColumnIndexOrThrow(COLUMN))?.let(::JSONObject)
        }
    }.getOrNull()

    private fun budget(o: JSONObject) = BudgetSummary(
        hasData = o.optBoolean("hasData", false),
        free = o.str("free"),
        freeNegative = o.optBoolean("freeNegative", false),
        upcoming = o.optJSONArray("upcoming").objects().mapNotNull { u ->
            val label = u.str("label") ?: return@mapNotNull null
            val months = u.optJSONArray("months") ?: return@mapNotNull null
            BudgetSummary.Due(label, u.str("amount"), (0 until months.length()).map { months.optInt(it) })
        },
    )

    private fun fitness(o: JSONObject): FitnessSummary {
        val next = o.optJSONObject("next")
        val week = o.optJSONObject("week")
        return FitnessSummary(
            nextName = next?.str("name"),
            nextDay = next?.str("day"),
            activeName = o.optJSONObject("active")?.str("name"),
            weekDone = week?.optInt("done") ?: 0,
            weekGoal = week?.optInt("goal") ?: 0,
            streak = o.optInt("streak", 0),
        )
    }

    private fun household(o: JSONObject) = HouseholdSummary(
        chores = o.optJSONArray("chores").objects().mapNotNull { c ->
            HouseholdSummary.Chore(c.str("name") ?: return@mapNotNull null, c.str("due") ?: return@mapNotNull null, c.optBoolean("mine"))
        },
        waste = o.optJSONArray("waste").objects().mapNotNull { p ->
            HouseholdSummary.Pickup(p.str("day") ?: return@mapNotNull null, p.str("name") ?: return@mapNotNull null)
        },
        shoppingCount = o.optInt("shoppingCount", 0),
    )

    /** optString liefert für null den Text „null“ – darum selbst prüfen. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val COLUMN = "json"
    }
}
