package space.mai.mai_launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.mai.mai_launcher.domain.AccentChoice
import space.mai.mai_launcher.domain.AliasCodec
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.Appearance
import space.mai.mai_launcher.domain.HapticLevel
import space.mai.mai_launcher.domain.SoundLevel

/**
 * Einfache Einstellungen. Schlüssel nie umbenennen; Werte tolerant lesen.
 * Dock: 4 Plätze, leer = Standard-App des Systems.
 */
class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _dock = MutableStateFlow(readDock())
    val dock: StateFlow<List<AppKey?>> = _dock.asStateFlow()

    private fun readDock(): List<AppKey?> = (0 until DOCK_SLOTS).map { AppKey.decode(prefs.getString("dock_$it", null)) }

    fun setDockSlot(slot: Int, key: AppKey?) {
        if (slot !in 0 until DOCK_SLOTS) return
        prefs.edit { putString("dock_$slot", key?.encode()) }
        _dock.value = readDock()
    }

    private val _destinations = MutableStateFlow(readDestinations())
    /** Schnellziele für die Navigation: [0] = Zuhause, [1] = Arbeit (Adresse als Text). */
    val destinations: StateFlow<List<String>> = _destinations.asStateFlow()

    private fun readDestinations() = listOf(prefs.getString("dest_home", "").orEmpty(), prefs.getString("dest_work", "").orEmpty())

    fun setDestination(index: Int, address: String) {
        val key = when (index) { 0 -> "dest_home"; 1 -> "dest_work"; else -> return }
        prefs.edit { putString(key, address.trim()) }
        _destinations.value = readDestinations()
    }

    private val _dismissed = MutableStateFlow(prefs.getStringSet("setup_dismissed", emptySet()).orEmpty())
    /** Einrichtungs-Hinweise, die der Nutzer weggeklickt hat. */
    val dismissedSetup: StateFlow<Set<String>> = _dismissed.asStateFlow()

    fun dismissSetup(id: String) {
        val next = _dismissed.value + id
        prefs.edit { putStringSet("setup_dismissed", next) }
        _dismissed.value = next
    }

    private val _maiSeen = MutableStateFlow(prefs.getStringSet("mai_widgets_seen", emptySet()).orEmpty())
    /** Mai-Apps, deren Widget schon einmal automatisch angeboten wurde (Paketnamen). */
    val maiWidgetsSeen: StateFlow<Set<String>> = _maiSeen.asStateFlow()

    fun markMaiWidgetsSeen(packages: Collection<String>) {
        if (packages.isEmpty()) return
        val next = _maiSeen.value + packages
        prefs.edit { putStringSet("mai_widgets_seen", next) }
        _maiSeen.value = next
    }

    private val _signalsText = MutableStateFlow(prefs.getBoolean("signals_show_text", false))
    /** Benachrichtigungstext auf dem Startbildschirm zeigen (Standard: aus, nur App und Anzahl). */
    val signalsShowText: StateFlow<Boolean> = _signalsText.asStateFlow()

    fun setSignalsShowText(on: Boolean) {
        prefs.edit { putBoolean("signals_show_text", on) }
        _signalsText.value = on
    }

    private val _haptics = MutableStateFlow(HapticLevel.of(prefs.getInt("feedback_haptics", HapticLevel.Light.ordinal)))
    /** Vibration bei Interaktionen (Standard: leicht). */
    val haptics: StateFlow<HapticLevel> = _haptics.asStateFlow()

    fun setHaptics(level: HapticLevel) {
        prefs.edit { putInt("feedback_haptics", level.ordinal) }
        _haptics.value = level
    }

    private val _sounds = MutableStateFlow(SoundLevel.of(prefs.getInt("feedback_sounds", SoundLevel.Off.ordinal)))
    /** Töne bei Interaktionen (Standard: aus). */
    val sounds: StateFlow<SoundLevel> = _sounds.asStateFlow()

    fun setSounds(level: SoundLevel) {
        prefs.edit { putInt("feedback_sounds", level.ordinal) }
        _sounds.value = level
    }

    private val _forecast = MutableStateFlow(prefs.getBoolean("mai_forecast", true))
    /** Prognose aus den Mai-Apps auf dem Deck (Standard: an, erscheint nur mit Daten). */
    val forecast: StateFlow<Boolean> = _forecast.asStateFlow()

    fun setForecast(on: Boolean) {
        prefs.edit { putBoolean("mai_forecast", on) }
        _forecast.value = on
    }

    private val _appearance = MutableStateFlow(readAppearance())
    /** Grundfarbe und Komfort-Modi. */
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private fun readAppearance() = Appearance(
        accent = AccentChoice.of(prefs.getInt("accent", 0)),
        highContrast = prefs.getBoolean("comfort_contrast", false),
        reduceMotion = prefs.getBoolean("comfort_motion", false),
        large = prefs.getBoolean("comfort_large", false),
    )

    fun setAppearance(a: Appearance) {
        prefs.edit {
            putInt("accent", a.accent.ordinal)
            putBoolean("comfort_contrast", a.highContrast)
            putBoolean("comfort_motion", a.reduceMotion)
            putBoolean("comfort_large", a.large)
        }
        _appearance.value = a
    }

    private fun readKeys(name: String): Set<AppKey> =
        prefs.getStringSet(name, emptySet()).orEmpty().mapNotNullTo(HashSet()) { AppKey.decode(it) }

    private fun writeKeys(name: String, keys: Set<AppKey>) {
        prefs.edit { putStringSet(name, keys.mapTo(HashSet()) { it.encode() }) }
    }

    private val _hidden = MutableStateFlow(readKeys("hidden_apps"))
    /** Ausgeblendete Apps (nur im Reiter „Ausgeblendet“ sichtbar). */
    val hiddenApps: StateFlow<Set<AppKey>> = _hidden.asStateFlow()

    fun setHidden(key: AppKey, hidden: Boolean) = setHiddenApps(if (hidden) _hidden.value + key else _hidden.value - key)

    fun setHiddenApps(keys: Set<AppKey>) {
        writeKeys("hidden_apps", keys)
        _hidden.value = keys
    }

    private val _likelyHidden = MutableStateFlow(readKeys("likely_hidden"))
    /** Apps, die nie unter „Wahrscheinlich jetzt“ erscheinen sollen. */
    val likelyHidden: StateFlow<Set<AppKey>> = _likelyHidden.asStateFlow()

    fun setLikelyHidden(keys: Set<AppKey>) {
        writeKeys("likely_hidden", keys)
        _likelyHidden.value = keys
    }

    private val _aliases = MutableStateFlow(AliasCodec.decode(prefs.getStringSet("aliases", emptySet()).orEmpty()))
    /** Eigene Suchnamen je App. */
    val aliases: StateFlow<Map<AppKey, String>> = _aliases.asStateFlow()

    fun setAlias(key: AppKey, alias: String?) {
        val clean = alias?.let(AliasCodec::clean)
        setAliases(if (clean == null) _aliases.value - key else _aliases.value + (key to clean))
    }

    fun setAliases(map: Map<AppKey, String>) {
        prefs.edit { putStringSet("aliases", AliasCodec.encode(map)) }
        _aliases.value = AliasCodec.decode(AliasCodec.encode(map))
    }

    companion object {
        const val DOCK_SLOTS = 4
    }
}
