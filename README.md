# Mai Launcher

Android home screen replacement with a calm, minimal sci-fi hand-terminal
look: one glowing frame, one subject, one thumb. Local and private: the only
network access is the RSS/Atom feeds you add yourself.

| Deck | Deck (live) | News | Setup / edit | Index |
|---|---|---|---|---|
| ![Deck](project-docs/screenshots/01_deck.png) | ![Live](project-docs/screenshots/05_deck_live.png) | ![News](project-docs/screenshots/07_deck_news.png) | ![Edit](project-docs/screenshots/06_deck_setup_edit.png) | ![Index](project-docs/screenshots/03_index.png) |

Concept: [`project-docs/KONZEPT.md`](project-docs/KONZEPT.md)

## Status

Deck (home) with now playing, navigation (next turn from the maps app),
upcoming events, quick destinations, hosted Android widgets, a news panel
for your own RSS/Atom feeds (HTTPS only, text only) and an edit mode;
Index (all apps, search, A–Z rail, "likely now"); side rail for Wi-Fi, BT,
sound mode (ring/vibrate/mute), DND, torch and rotation. Every permission is
optional and asked for from a "Set up" panel.
Next: command line actions, Comms.

Privacy: the only network access is fetching the feeds you add yourself.

## Build

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Requires JDK 17+ and the Android SDK (compileSdk 36). CI builds
`mai-launcher.apk` on every push. Install it, open it and tap
"Set as home screen" in the Set up panel.

Fonts: Barlow, Barlow Condensed, JetBrains Mono (SIL OFL 1.1, see `licenses/`).
