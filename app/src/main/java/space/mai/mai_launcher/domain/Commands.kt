package space.mai.mai_launcher.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/** Einstellungsseiten, die per Stichwort erreichbar sind. */
enum class SettingPage { Wifi, Bluetooth, Display, Sound, Battery, Apps, Location, Notifications, Storage, Settings }

/** Ergebnis der Befehlszeile (ohne Android-Typen, damit testbar). */
sealed interface Command {
    /** Sofortige Antwort (Rechnung, Einheit, Prozent); Tippen kopiert [value]. */
    data class Answer(val expression: String, val value: String) : Command
    data class Timer(val seconds: Int) : Command
    data class Alarm(val hour: Int, val minute: Int) : Command
    data object Stopwatch : Command
    data class Setting(val page: SettingPage) : Command
    /** Nummer an die Telefon-App übergeben (nur Wählen, kein Anruf). */
    data class Dial(val number: String) : Command
    /** Websuche im Standard-Browser – nur auf Tippen, nie automatisch. */
    data class WebSearch(val query: String) : Command
}

/**
 * Versteht die Eingabe der Befehlszeile. Alles lokal; Reihenfolge = Rang,
 * die Websuche kommt immer zuletzt.
 */
object CommandParser {

    fun parse(input: String): List<Command> {
        val q = input.trim()
        if (q.isEmpty()) return emptyList()
        val lower = q.lowercase(Locale.ROOT)
        val out = mutableListOf<Command>()
        verb(lower)?.let(out::add)
        percent(lower)?.let(out::add)
        units(lower)?.let(out::add)
        if (out.none { it is Command.Answer }) calc(q)?.let(out::add)
        setting(lower)?.let(out::add)
        phone(q)?.let(out::add)
        if (q.length >= 2 && !q.startsWith("=")) out += Command.WebSearch(q)
        return out
    }

    /** true, wenn ein Befehl wichtiger ist als App-Treffer (Enter führt ihn aus). */
    fun isExplicit(cmd: Command): Boolean = cmd !is Command.WebSearch && cmd !is Command.Setting

    // ------------------------------------------------------------ Verben

    private val timerWords = listOf("timer", "countdown")
    private val alarmWords = listOf("alarm", "wecker")
    private val stopwatchWords = setOf("stopwatch", "stoppuhr")

    private fun verb(q: String): Command? {
        if (q in stopwatchWords) return Command.Stopwatch
        timerWords.firstOrNull { q.startsWith("$it ") }?.let { w ->
            return duration(q.removePrefix(w).trim())?.let { Command.Timer(it) }
        }
        alarmWords.firstOrNull { q.startsWith("$it ") }?.let { w ->
            return clock(q.removePrefix(w).trim())?.let { (h, m) -> Command.Alarm(h, m) }
        }
        return null
    }

    private val durationPart = Regex("""(\d+(?:[.,]\d+)?)\s*(stunden?|std|hours?|h|minuten?|minutes?|min|m|sekunden?|seconds?|sek|sec|s)?""")

    /** „10“ = 10 Minuten; „1h 30m“, „90s“, „1,5 h“. */
    fun duration(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        var total = 0.0
        var rest = t
        var any = false
        while (rest.isNotEmpty()) {
            val m = durationPart.matchAt(rest, 0) ?: return null
            if (m.value.isBlank()) return null
            val n = m.groupValues[1].replace(',', '.').toDouble()
            val unit = m.groupValues[2]
            total += when {
                unit.startsWith("h") || unit.startsWith("st") -> n * 3600
                unit.startsWith("s") -> n
                else -> n * 60
            }
            any = true
            rest = rest.substring(m.range.last + 1).trimStart()
        }
        val secs = total.toInt()
        return if (any && secs in 1..86_400) secs else null
    }

    private val clockRe = Regex("""(\d{1,2})(?:[:.h](\d{2}))?\s*(am|pm|uhr)?""")

    fun clock(text: String): Pair<Int, Int>? {
        val m = clockRe.matchEntire(text.trim()) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        when (m.groupValues[3]) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    // ------------------------------------------------------------ Prozent

    private val percentRe = Regex("""(-?\d+(?:[.,]\d+)?)\s*%\s*(?:of|von|vom)\s*(-?\d+(?:[.,]\d+)?)""")

    private fun percent(q: String): Command? {
        val m = percentRe.matchEntire(q) ?: return null
        val p = num(m.groupValues[1]) ?: return null
        val base = num(m.groupValues[2]) ?: return null
        return Command.Answer(q, format(p * base / 100.0))
    }

    // ------------------------------------------------------------ Einheiten

    private enum class Dim { Length, Mass, Volume, Speed, Temp }

    /** Faktor zur Basiseinheit (m, kg, l, km/h); Temperatur gesondert. */
    private data class Unit(val dim: Dim, val factor: Double, val symbol: String)

    private val unitTable: Map<String, Unit> = buildMap {
        fun put(dim: Dim, factor: Double, symbol: String, vararg names: String) = names.forEach { put(it, Unit(dim, factor, symbol)) }
        put(Dim.Length, 1.0, "m", "m", "meter", "metre", "meters", "metres")
        put(Dim.Length, 1000.0, "km", "km", "kilometer", "kilometers", "kilometre")
        put(Dim.Length, 0.01, "cm", "cm")
        put(Dim.Length, 0.001, "mm", "mm")
        put(Dim.Length, 1609.344, "mi", "mi", "mile", "miles", "meile", "meilen")
        put(Dim.Length, 0.3048, "ft", "ft", "foot", "feet", "fuß", "fuss")
        put(Dim.Length, 0.9144, "yd", "yd", "yard", "yards")
        put(Dim.Length, 0.0254, "in", "inch", "inches", "zoll")
        put(Dim.Mass, 1.0, "kg", "kg", "kilo", "kilogram", "kilogramm")
        put(Dim.Mass, 0.001, "g", "g", "gram", "gramm")
        put(Dim.Mass, 0.45359237, "lb", "lb", "lbs", "pound", "pounds", "pfund")
        put(Dim.Mass, 0.028349523125, "oz", "oz", "ounce", "ounces", "unze")
        put(Dim.Volume, 1.0, "l", "l", "liter", "litre", "liters")
        put(Dim.Volume, 0.001, "ml", "ml")
        put(Dim.Volume, 3.785411784, "gal", "gal", "gallon", "gallons", "gallone")
        put(Dim.Volume, 0.2365882365, "cup", "cup", "cups", "tasse")
        put(Dim.Speed, 1.0, "km/h", "km/h", "kmh", "kph")
        put(Dim.Speed, 1.609344, "mph", "mph")
        put(Dim.Speed, 3.6, "m/s", "m/s")
        put(Dim.Temp, 0.0, "°C", "c", "°c", "celsius")
        put(Dim.Temp, 1.0, "°F", "f", "°f", "fahrenheit")
    }

    private val unitRe = Regex("""(-?\d+(?:[.,]\d+)?)\s*([a-zä°/ß]+)\s+(?:in|to|nach|zu|->|=)\s+([a-zä°/ß]+)""")

    private fun units(q: String): Command? {
        val m = unitRe.matchEntire(q) ?: return null
        val value = num(m.groupValues[1]) ?: return null
        val from = unitTable[m.groupValues[2]] ?: return null
        val to = unitTable[m.groupValues[3]] ?: return null
        if (from.dim != to.dim) return null
        val result = if (from.dim == Dim.Temp) {
            val c = if (from.factor == 0.0) value else (value - 32) * 5 / 9
            if (to.factor == 0.0) c else c * 9 / 5 + 32
        } else {
            value * from.factor / to.factor
        }
        return Command.Answer(q, "${format(result, 6)} ${to.symbol}")
    }

    // ------------------------------------------------------------ Rechner

    /** Nur Ziffern, Operatoren, Klammern; ohne „=“ mindestens ein Operator zwischen Zahlen. */
    private val calcChars = Regex("""[\d\s+\-*/×÷x^().,%]+""")
    private val hasOperator = Regex("""\d%?\s*[+\-*/×÷x^]\s*[\d(.]""")

    private fun calc(q: String): Command? {
        val explicit = q.startsWith("=")
        val expr = q.removePrefix("=").trim()
        if (expr.isEmpty() || !calcChars.matches(expr)) return null
        if (!explicit && !hasOperator.containsMatchIn(expr)) return null
        val v = runCatching { Calc(expr).parse() }.getOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return Command.Answer(expr, format(v))
    }

    /** Kleiner rekursiver Parser: + − × ÷ ^ %, Klammern, Komma oder Punkt. */
    private class Calc(src: String) {
        private val s = src.replace('×', '*').replace('÷', '/').replace('x', '*').replace(',', '.').replace(" ", "")
        private var i = 0

        fun parse(): Double {
            val v = sum()
            require(i == s.length)
            return v
        }

        private fun sum(): Double {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = product()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun product(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = power()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun power(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') { i++; return Math.pow(b, power()) }
            return b
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            var v = atom()
            while (i < s.length && s[i] == '%') { i++; v /= 100.0 }
            return v
        }

        private fun atom(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = sum()
                require(i < s.length && s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }

    // ------------------------------------------------------------ Einstellungen

    private val settingWords: Map<String, SettingPage> = mapOf(
        "wifi" to SettingPage.Wifi, "wlan" to SettingPage.Wifi, "wi-fi" to SettingPage.Wifi,
        "bluetooth" to SettingPage.Bluetooth, "bt" to SettingPage.Bluetooth,
        "display" to SettingPage.Display, "anzeige" to SettingPage.Display, "helligkeit" to SettingPage.Display, "brightness" to SettingPage.Display,
        "sound" to SettingPage.Sound, "ton" to SettingPage.Sound, "lautstärke" to SettingPage.Sound, "volume" to SettingPage.Sound,
        "battery" to SettingPage.Battery, "akku" to SettingPage.Battery,
        "apps" to SettingPage.Apps,
        "location" to SettingPage.Location, "standort" to SettingPage.Location, "gps" to SettingPage.Location,
        "notifications" to SettingPage.Notifications, "benachrichtigungen" to SettingPage.Notifications,
        "storage" to SettingPage.Storage, "speicher" to SettingPage.Storage,
        "settings" to SettingPage.Settings, "einstellungen" to SettingPage.Settings,
    )

    private fun setting(q: String): Command? {
        if (q.length < 2) return null
        settingWords[q]?.let { return Command.Setting(it) }
        // Anfang eines Stichworts ab 3 Zeichen („blue“, „einst“).
        if (q.length < 3) return null
        return settingWords.entries.firstOrNull { it.key.startsWith(q) }?.let { Command.Setting(it.value) }
    }

    // ------------------------------------------------------------ Telefonnummer

    private val phoneRe = Regex("""\+?[\d\s()/-]{3,}""")

    private fun phone(q: String): Command? {
        if (!phoneRe.matches(q)) return null
        val digits = q.filter { it.isDigit() || it == '+' }
        if (digits.count(Char::isDigit) < 3) return null
        if (hasOperator.containsMatchIn(q) && !q.contains(' ')) return null
        return Command.Dial(digits)
    }

    // ------------------------------------------------------------ Hilfen

    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    /** Bis zu [digits] signifikante Stellen, ohne überflüssige Nullen. */
    fun format(v: Double, digits: Int = 10): String {
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(digits, RoundingMode.HALF_UP)).stripTrailingZeros()
        val plain = if (bd.scale() < 0) bd.setScale(0) else bd
        return plain.toPlainString()
    }
}
