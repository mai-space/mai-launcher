package space.mai.mai_launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import space.mai.mai_launcher.domain.AppShortcut
import space.mai.mai_launcher.domain.IndexTab
import space.mai.mai_launcher.design.Comfort
import space.mai.mai_launcher.design.ComfortState
import space.mai.mai_launcher.design.toAccent
import space.mai.mai_launcher.domain.AccentChoice
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import space.mai.mai_launcher.design.Accents
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiBackground
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.CalendarEvent
import space.mai.mai_launcher.domain.CommandParser
import space.mai.mai_launcher.domain.Contact
import space.mai.mai_launcher.domain.SignalGroup
import space.mai.mai_launcher.ui.media.MediaScreen
import space.mai.mai_launcher.ui.media.MediaScreenActions
import space.mai.mai_launcher.ui.media.MediaState
import space.mai.mai_launcher.ui.comms.CommsActions
import space.mai.mai_launcher.ui.comms.CommsScreen
import space.mai.mai_launcher.ui.comms.CommsState
import space.mai.mai_launcher.domain.FeedItem
import space.mai.mai_launcher.feeds.FeedState
import space.mai.mai_launcher.domain.NavInfo
import space.mai.mai_launcher.domain.NowPlaying
import space.mai.mai_launcher.domain.SoundMode
import space.mai.mai_launcher.system.SystemState
import space.mai.mai_launcher.ui.deck.DeckActions
import space.mai.mai_launcher.ui.deck.DeckScreen
import space.mai.mai_launcher.ui.deck.DeckState
import space.mai.mai_launcher.ui.index.IndexActions
import space.mai.mai_launcher.ui.index.IndexScreen
import space.mai.mai_launcher.ui.index.IndexState
import java.time.LocalDateTime
import space.mai.mai_launcher.domain.HapticLevel
import space.mai.mai_launcher.domain.ForecastLine
import space.mai.mai_launcher.domain.MaiSummaryApps
import space.mai.mai_launcher.domain.SoundLevel
import space.mai.mai_launcher.ui.settings.BackupStatus
import space.mai.mai_launcher.ui.settings.DockSlot
import space.mai.mai_launcher.ui.settings.SettingsActions
import space.mai.mai_launcher.ui.settings.SettingsScreen
import space.mai.mai_launcher.ui.settings.SettingsState
import java.time.ZoneId

/**
 * Rendert Deck und Index mit erfundenen Daten. Bilder werden nur mit
 * `-Pscreenshots` geschrieben (project-docs/screenshots/); sonst prüft der
 * Test nur, dass alles ohne Absturz zeichnet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-port-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private val labels = listOf(
        "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Files", "Fitness", "Gallery",
        "Maps", "Messages", "Music", "Notes", "Phone", "Browser", "Ruf durch", "Settings", "Weather", "Ärzte",
    )
    private val apps = labels.map { AppInfo(AppKey("app.${it.lowercase().replace(" ", "")}", "Main"), it) }
    private fun tile(label: String) = apps.first { it.label == label }.let { AppTile(it.key, it.label) }

    private val system = SystemState(
        nowMillis = at(8, 21, 44),
        nextAlarmMillis = at(9, 6, 30),
        batteryPercent = 82,
        charging = false,
        storageFreeBytes = 41_300_000_000,
        storageTotalBytes = 128_000_000_000,
        wifiConnected = true,
        bluetoothOn = true,
        dndOn = false,
        torchAvailable = true,
        torchOn = false,
        autoRotate = false,
    )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAccent provides Accents.Ion) {
            MaiBackground { content() }
        }
    }

    @Test fun deck() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system,
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/01_deck.png")
    }

    @Test fun deckLive() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system.copy(soundMode = SoundMode.Vibrate),
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                        nowPlaying = NowPlaying("Low Orbit Lullaby", "The Kepler Set", "Music", true, 102_000, 243_000),
                        navigation = NavInfo("200 m", "Rechts abbiegen auf Hafenstraße", "Ankunft 22:05", "Maps"),
                        events = listOf(
                            CalendarEvent(1, "Zahnarzt", at(9, 9, 15), at(9, 10, 0), false),
                            CalendarEvent(2, "Training mit Sam", at(10, 18, 30), at(10, 20, 0), false),
                            CalendarEvent(3, "Geburtstag Alex", utc(11), utc(12), true),
                        ),
                        news = FeedState(
                            urls = listOf("https://news.example/feed"),
                            items = listOf(
                                FeedItem("Beispiel News", "Neue Fähre zwischen Hafen und Insel startet im Frühjahr", "https://news.example/1", at(8, 21, 2)),
                                FeedItem("Tech-Blog", "Warum dunkle Oberflächen Akku sparen – und wann nicht", "https://blog.example/2", at(8, 18, 40)),
                            ),
                        ),
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/05_deck_live.png")
    }

    @Test fun deckNews() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system,
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                        news = FeedState(
                            urls = listOf("https://news.example/feed", "https://blog.example/atom"),
                            items = listOf(
                                FeedItem("Beispiel News", "Neue Fähre zwischen Hafen und Insel startet im Frühjahr", "https://news.example/1", at(8, 21, 2)),
                                FeedItem("Tech-Blog", "Warum dunkle Oberflächen Akku sparen – und wann nicht", "https://blog.example/2", at(8, 18, 40)),
                                FeedItem("Beispiel News", "Stadtbibliothek verlängert Öffnungszeiten am Wochenende", "https://news.example/3", at(8, 9, 5)),
                                FeedItem("Tech-Blog", "Kleine Helfer: fünf Tastenkürzel, die kaum jemand kennt", "https://blog.example/4", at(7, 16, 30)),
                            ),
                        ),
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/07_deck_news.png")
    }

    @Test fun deckSetupAndEdit() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system,
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                        setup = listOf(SetupItem.Home, SetupItem.MaiWidgets, SetupItem.Signals, SetupItem.Calendar),
                        destinations = listOf("Hafenstraße 1, Hamburg", ""),
                        editMode = true,
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/06_deck_setup_edit.png")
    }

    private fun utc(day: Int) = LocalDateTime.of(2026, 11, day, 0, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

    @Test fun deckFirstRun() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system.copy(nextAlarmMillis = null, batteryPercent = 12, wifiConnected = false),
                        dock = listOf(tile("Phone"), tile("Messages"), null, tile("Browser")),
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/02_deck_first_run.png")
    }

    @Test fun index() {
        compose.setContent {
            Frame {
                IndexScreen(
                    IndexState(
                        apps = apps,
                        results = emptyList(),
                        query = "",
                        likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                        likelyWindow = 20 to 22,
                    ),
                    IndexActions(),
                    focusSignal = 0,
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/03_index.png")
    }

    @Test fun indexSearch() {
        compose.setContent {
            Frame {
                IndexScreen(
                    IndexState(
                        apps = apps,
                        results = listOf(tile("Calendar"), tile("Calculator")),
                        query = "cal",
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                    ),
                    IndexActions(),
                    focusSignal = 0,
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/04_index_search.png")
    }

    @Test fun indexCommands() {
        compose.setContent {
            Frame {
                IndexScreen(
                    IndexState(
                        apps = apps,
                        results = emptyList(),
                        query = "5 km in mi",
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                        commands = CommandParser.parse("5 km in mi"),
                    ),
                    IndexActions(),
                    focusSignal = 0,
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/08_index_commands.png")
    }

    @Test fun indexContact() {
        compose.setContent {
            Frame {
                IndexScreen(
                    IndexState(
                        apps = apps,
                        results = emptyList(),
                        query = "call sam",
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                        commands = CommandParser.parse("call sam"),
                        contacts = listOf(contacts[1]),
                    ),
                    IndexActions(),
                    focusSignal = 0,
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/11_index_contact.png")
    }

    private val contacts = listOf(
        Contact("k1", "Alex Berg", true, listOf("+49 151 0000001")),
        Contact("k2", "Sam Ober", true, listOf("+49 40 0000002", "+49 151 0000003")),
        Contact("k3", "Mama", true, listOf("+49 171 0000004")),
        Contact("k4", "Kim Ödegaard", false, listOf("+49 30 0000005")),
        Contact("k5", "Jo Brandt", true, listOf("+49 89 0000006")),
        Contact("k6", "Robin Lind", false, listOf("+49 221 0000007")),
        Contact("k7", "Uli Weber", false, listOf("+49 711 0000008")),
    )

    @Composable
    private fun Comms(state: CommsState) {
        CompositionLocalProvider(LocalAccent provides Accents.Signal) {
            MaiBackground { CommsScreen(state, CommsActions()) }
        }
    }

    @Test fun comms() {
        compose.setContent {
            Comms(
                CommsState(
                    access = true,
                    contacts = contacts,
                    favourites = contacts.filter { it.starred },
                    frequent = listOf(contacts[3], contacts[5]),
                    total = contacts.size,
                    rufDurch = true,
                ),
            )
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/09_comms.png")
    }

    @Test fun commsKeypad() {
        compose.setContent {
            Comms(
                CommsState(
                    access = false,
                    contacts = emptyList(),
                    favourites = emptyList(),
                    frequent = emptyList(),
                    total = 0,
                    keypadOpen = true,
                    dialed = "+4940000000",
                ),
            )
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/10_comms_keypad.png")
    }

    @Test fun deckSignals() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system,
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                        signals = listOf(
                            SignalGroup("app.messages", "Messages", 3, "Sam", "Kommst du nachher noch vorbei?", at(8, 21, 30), "k1"),
                            SignalGroup("app.mail", "Mail", 1, "Stadtwerke", "Ihre Abrechnung ist da", at(8, 19, 5), "k2"),
                        ),
                        signalsShowText = true,
                        badges = mapOf(tile("Messages").key.packageName to 3),
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/12_deck_signals.png")
    }

    @Composable
    private fun Media(state: MediaState) {
        CompositionLocalProvider(LocalAccent provides Accents.Amber) {
            MaiBackground { MediaScreen(state, MediaScreenActions(elapsedNow = { 0L })) }
        }
    }

    @Test fun media() {
        compose.setContent {
            Media(
                MediaState(
                    nowPlaying = NowPlaying("Low Orbit Lullaby", "The Kepler Set", "Music", true, 102_000, 243_000),
                    queue = listOf("Drift Burn", "Belt Static", "Ceres Morning"),
                ),
            )
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/13_media.png")
    }

    @Test fun mediaIdle() {
        compose.setContent {
            Media(MediaState(nowPlaying = null, musicApps = listOf(tile("Music"), tile("Ruf durch"))))
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/14_media_idle.png")
    }

    @Test fun settings() {
        compose.setContent {
            Frame {
                SettingsScreen(
                    SettingsState(
                        dock = listOf(
                            DockSlot(tile("Phone"), false), DockSlot(tile("Messages"), false),
                            DockSlot(tile("Music"), true), DockSlot(tile("Browser"), false),
                        ),
                        calendar = false,
                        haptics = HapticLevel.Light,
                        sounds = SoundLevel.Quiet,
                        widgetCount = 2,
                        version = "0.1.0",
                        backup = BackupStatus.Imported(dock = 4, feeds = 2, widgets = 3),
                    ),
                    SettingsActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/15_settings.png")
    }

    @Test fun deckForecast() {
        val fit = AppInfo(AppKey(MaiSummaryApps.FITNESS, "Main"), "Fitness")
        val bud = AppInfo(AppKey(MaiSummaryApps.BUDGET, "Main"), "Budget")
        val hh = AppInfo(AppKey(MaiSummaryApps.HOUSEHOLD, "Main"), "Haushalt")
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system,
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                        forecast = listOf(
                            ForecastLine.Budget("340 €", false, "Car insurance", "420 €", 2),
                            ForecastLine.Fitness(2, 3, 4, "Push day", 1, false),
                            ForecastLine.Household(2, 1, "Water plants", "Paper", 1, 7),
                        ),
                        forecastApps = listOf(bud, fit, hh).associate { it.key.packageName to AppTile(it.key, it.label) },
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("../project-docs/screenshots/16_deck_forecast.png")
    }

    @Test fun deckComfort() {
        // Hoher Kontrast + große Ansicht, Grundfarbe Violett.
        ComfortState.current = Comfort(highContrast = true, large = true)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAccent provides AccentChoice.Violet.toAccent()) {
                    MaiBackground {
                        DeckScreen(
                            DeckState(
                                system = system,
                                dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                                likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                                likelyWindow = 20 to 22,
                                appCount = apps.size,
                                zone = zone,
                                forecast = listOf(ForecastLine.Household(3, 0, "Water plants", "Paper", 1, 2)),
                            ),
                            DeckActions(),
                        )
                    }
                }
            }
            compose.onRoot().captureRoboImage("../project-docs/screenshots/17_deck_comfort.png")
        } finally {
            ComfortState.current = Comfort()
        }
    }

    @Test fun indexMenu() {
        // Reiter, Alias und geöffnetes Langdruck-Menü mit Verknüpfungen.
        compose.setContent {
            Frame {
                IndexScreen(
                    IndexState(
                        apps = apps,
                        results = emptyList(),
                        query = "",
                        likely = emptyList(),
                        likelyWindow = 20 to 22,
                        tabs = listOf(IndexTab.All, IndexTab.Work, IndexTab.Hidden),
                        aliases = mapOf(tile("Ärzte").key to "doc"),
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                    ),
                    IndexActions(
                        loadShortcuts = {
                            listOf(AppShortcut("new", "app.calendar", "New event", 0), AppShortcut("today", "app.calendar", "Today", 0))
                        },
                    ),
                    focusSignal = 0,
                )
            }
        }
        compose.onNodeWithContentDescription("Calendar").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../project-docs/screenshots/18_index_menu.png")
    }

    @Test fun deckLikelyWhy() {
        compose.setContent {
            Frame {
                DeckScreen(
                    DeckState(
                        system = system,
                        dock = listOf(tile("Phone"), tile("Messages"), tile("Camera"), tile("Browser")),
                        likely = listOf(tile("Music"), tile("Calendar"), tile("Ruf durch"), tile("Notes")),
                        likelyWindow = 20 to 22,
                        appCount = apps.size,
                        zone = zone,
                        likelyReasons = mapOf(tile("Music").key to 9),
                    ),
                    DeckActions(),
                )
            }
        }
        compose.onNodeWithContentDescription("Music").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../project-docs/screenshots/19_deck_likely_why.png")
    }
}
