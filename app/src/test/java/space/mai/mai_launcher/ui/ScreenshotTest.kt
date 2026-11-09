package space.mai.mai_launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import space.mai.mai_launcher.design.Accents
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiBackground
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.system.SystemState
import space.mai.mai_launcher.ui.deck.DeckActions
import space.mai.mai_launcher.ui.deck.DeckScreen
import space.mai.mai_launcher.ui.deck.DeckState
import space.mai.mai_launcher.ui.index.IndexActions
import space.mai.mai_launcher.ui.index.IndexScreen
import space.mai.mai_launcher.ui.index.IndexState
import java.time.LocalDateTime
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
}
