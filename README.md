<div align="center">

<img src="logo/logo-splash-screen.png" alt="Nekuva logo" width="128" height="128" />

# Nekuva

**A cross-platform manga reader built with Kotlin Multiplatform & Compose Multiplatform.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20Desktop%20(Windows%20%7C%20macOS%20%7C%20Linux)%20%7C%20iOS%20(planned)-success)
![Status](https://img.shields.io/badge/status-active%20development-orange)

</div>

---

## About

**Nekuva** is a manga reader that lets you browse many online sources, read chapters online,
and keep a local library — on **Android**, **Desktop (Windows, macOS & Linux)**, and (planned)
**iOS** from a single shared codebase.

Manga sources (the parsers/scrapers) live in a separate repository,
**[nekuva-exts](https://github.com/NekoSukuriputo/nekuva-exts)**, which Nekuva consumes as a
published dependency. This keeps the UI app and the source library cleanly separated.

> **Project status:** Nekuva is under active development. It started as a fork of
> [DokiTeam/Doki](https://github.com/DokiTeam/Doki) (an Android app built with XML Views) and is
> being **rewritten into Kotlin Multiplatform + Compose Multiplatform**. The Android and Desktop
> targets build and run today; iOS is planned for a later phase. Expect rapid changes.

## Features

- 📚 Browse and search a large catalog of online manga sources (via `nekuva-exts`)
- 📖 Online reader with a continuous vertical/webtoon view
- 💾 Local library — read your own `.cbz` archives
- ⭐ Favourites with categories, reading history, and bookmarks
- 🌙 Dark-first Material 3 theme (with an AMOLED option)
- 🌐 Multi-language UI (localized via Compose Multiplatform Resources)
- 🖥️ Adaptive layout — bottom navigation on phones, navigation rail on desktop/tablet

*Some features are still being migrated; see the in-repo `MIGRATION.md` for current progress.*

## Tech stack

| Concern | Choice |
|---|---|
| UI | Compose Multiplatform (Material 3) |
| DI | Koin |
| Database | Room KMP (bundled SQLite) |
| Networking | OkHttp (shared JVM layer) |
| Images | Coil 3 |
| Preferences | multiplatform-settings |
| Navigation | Compose Navigation (type-safe routes) |
| Dates / i18n | kotlinx-datetime + Compose Resources |
| Sources | [nekuva-exts](https://github.com/NekoSukuriputo/nekuva-exts) (JitPack) |

### Source-set layout

```
composeApp/src/
├── commonMain/      # platform-agnostic Compose UI + logic
├── jvmSharedMain/   # shared Android + Desktop JVM code (OkHttp + source models)
├── androidMain/     # Android entry point & platform actuals
├── desktopMain/     # Desktop entry point & platform actuals
└── iosMain/         # iOS actuals (planned)
```

## Building & running

Requires **JDK 17** and the Android SDK (for the Android target).

**Desktop (Windows, macOS & Linux):**
```bash
./gradlew :composeApp:run
```

Native installers (`.msi` / `.dmg` / `.deb`):
```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

**Android** (device with USB debugging connected):
```bash
./gradlew :composeApp:installDebug
```

**Android APK only:**
```bash
./gradlew :composeApp:assembleDebug
# output: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

## Contributing

Bug reports and pull requests are welcome.

- **App issues:** <https://github.com/NekoSukuriputo/nekuva/issues>
- **New manga sources / source fixes:** these belong in
  **[nekuva-exts](https://github.com/NekoSukuriputo/nekuva-exts)**, *not* this repo — Nekuva never
  contains site-specific scraping code.

## Credits & lineage

Nekuva is a fork of [DokiTeam/Doki](https://github.com/DokiTeam/Doki), which is itself based on
[KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu). Huge thanks to the Doki and Kotatsu
teams and all upstream contributors. Nekuva is now developed independently by
[NekoSukuriputo](https://github.com/NekoSukuriputo).

## License

Licensed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE).

You may copy, distribute and modify the software as long as you track changes/dates in source
files. Any modifications to, or software including (via compiler) GPL-licensed code must also be
made available under the GPL along with build & install instructions.

## Disclaimer

Nekuva does **not** host any manga. It aggregates publicly available, third-party sources through
`nekuva-exts`. Nekuva is not affiliated with any of those sources. Please support the original
creators and official/licensed releases wherever they are available.
