# Mai Launcher — Hinweise für Änderungen

Startbildschirm-Ersatz für Android (Kotlin + Jetpack Compose, kein Flutter –
Begründung in `project-docs/KONZEPT.md`, Abschnitt 3). Ruhiges Sci-Fi-Design:
ein leuchtender Rahmen, ein Thema, ein Daumen. UI Deutsch/Englisch nach
Systemsprache. Paket `space.mai.mai_launcher`, CI-Artefakt `mai-launcher.apk`.

## Datenschutz (Pflicht)

- **INTERNET nur für Feeds.** Der Launcher sieht alle Apps,
  Benachrichtigungen und Termine – nichts davon darf das Gerät verlassen.
  Netzwerk nutzt ausschließlich `feeds/FeedRepository`: nur vom Nutzer
  eingetragene HTTPS-Feeds, keine Bilder, keine Cookies/Kennungen, keine
  Analytics. Keine weiteren Netzwerkzugriffe ohne Rücksprache.
- Kein Android-Backup (`allowBackup=false`, `data_extraction_rules.xml`),
  keine Analytics, kein Konto.
- Jede Berechtigung optional, mit Erklärung; der Launcher muss ohne laufen.
- **Comms:** Kontakte nur lesen (`READ_CONTACTS`). Keine Telefon-Berechtigung –
  Anrufen nur über die Telefon-App (`ACTION_DIAL`), SMS über `smsto:`. Eigener
  Verlauf (`comms_log`, `CommsLogCodec` Kopf `v1`) speichert nur Kontakt-Schlüssel,
  Anzahl und Zeit, keine Nummern oder Namen.
- **Befehlszeile:** alles lokal; Websuche nur auf Tippen, nie per Enter.
- Keine echten Nutzerdaten ins Repo (Screenshots nur mit erfundenen Daten).

## Rückwärtskompatibilität (ab dem ersten verteilten Build Pflicht)

- **`AppKey`-Format** `paket/klasse#profil` nie ändern (Dock, Startverlauf).
- **Startverlauf** (`LaunchLogCodec`, Kopf `v1`) nur erweitern; unbekannte
  Zeilen werden übersprungen.
- **`SecureFile`** (Format „MAIL“ v1, AES-GCM, Keystore): Format, Alias
  `mai_launcher_log_key` und Dateiname `launch_log.bin` nie ändern; neue
  Formate nur mit neuer Versionsnummer und Lesbarkeit der alten.
- **Einstellungen** (`LauncherPrefs`, Schlüssel `dock_0..3`, `dest_home`, `dest_work`, `setup_dismissed`,
  `widgets`, `feeds`, `mai_widgets_seen`, `comms_log`) tolerant lesen,
  Schlüssel nie umbenennen.

- **Mai-Widgets** (`domain/MaiWidgets.kt`): Paket-Präfix `space.mai.` und die
  bevorzugten Widget-Klassen der anderen Mai-Apps. Benennt eine App ihre
  Widget-Klasse um, hier mitziehen (sonst greift nur der Rückfall).

## Design-System (`design/`)

- Farben/Maße nur aus `Tokens.kt`, Schriften aus `Type.kt` (OFL, gebündelt).
- Eine Kontextfarbe je Bildschirm (`LocalAccent`), Rot nur für Warnungen.
- Leucht-Budget: höchstens ein `GlowLevel.Frame` pro Bildschirm; Leuchten
  trägt nie allein Bedeutung.
- Sechseck = tippbar. Mindest-Tippfläche 48 dp.
- Keine Dauer-Animationen; Bildschirme bekommen Zustand als reine Daten
  (`DeckState`, `IndexState`), damit sie ohne Gerät testbar sind.

## Arbeitsweise

- Reine Logik in `domain/` (ohne Android) mit Unit-Tests in `app/src/test`.
- Strings in `res/values/strings.xml` (Englisch) **und**
  `res/values-de/strings.xml` (Deutsch) anlegen.
- Prüfen vor jedem Commit: `./gradlew :app:testDebugUnitTest :app:lintDebug`
  (Lint ohne Fehler) und `:app:assembleRelease` (R8).
- Screenshots der Oberfläche (Robolectric + Roborazzi, erfundene Daten):
  `./gradlew :app:testDebugUnitTest -Pscreenshots` → `project-docs/screenshots/`.
  Drosselt Maven Central, hilft `ROBOLECTRIC_REPO_URL` (Spiegel-URL).
- Neue native Bibliotheken ggf. in `app/proguard-rules.pro` absichern.
- Kommentare im Code auf Deutsch.
