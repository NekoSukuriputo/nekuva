# Contributing to Nekuva

Thanks for your interest in improving **Nekuva**! This guide covers contributing to the **app**
(the Kotlin Multiplatform / Compose Multiplatform UI). Please read it before opening a pull request.

## Scope: app vs. sources

- This repository is the **UI application** only.
- **New manga sources, or fixes to existing sources, do NOT belong here.** They live in the separate
  parser library, **[nekuva-exts](https://github.com/NekoSukuriputo/nekuva-exts)**. Nekuva never
  contains site-specific scraping code — please send source work there.

## Before you start

- For **bug fixes** that already have an [issue](https://github.com/NekoSukuriputo/nekuva/issues):
  comment on it so others know you're working on it.
- For **new features** or larger changes: open an issue or discussion first, so the approach can be
  agreed on before you invest time.
- Small fixes (typos, obvious bugs) can go straight to a PR.

## Development setup

Requirements: **JDK 17** and, for the Android target, the **Android SDK**.

```bash
git clone https://github.com/NekoSukuriputo/nekuva.git
cd nekuva

./gradlew :composeApp:run            # run the Desktop app
./gradlew :composeApp:installDebug   # build & install on a connected Android device
./gradlew :composeApp:assembleDebug  # build the Android debug APK
```

## Project structure

Nekuva is a single `composeApp` module with hierarchical source sets. **Where code goes depends on
what it touches:**

- `commonMain` — platform-agnostic Compose UI and logic (no platform or source-library APIs).
- `jvmSharedMain` — code shared by Android + Desktop that touches OkHttp or `nekuva-exts` models.
- `androidMain` / `desktopMain` — platform-specific implementations (`expect`/`actual`), entry
  points, file access, etc.

When you add a file, put it in the most general source set it can live in, and only drop to a
platform source set for genuinely platform-specific code.

## Conventions

- **Code style:** Kotlin official style; the repo's `.editorconfig` is authoritative — match it.
- **No hardcoded user-facing strings.** All UI text goes through Compose Multiplatform Resources
  (`composeResources/values*/strings.xml`) and is read via `stringResource(...)`. Reuse existing
  keys where possible.
- **Branding:** the project is "Nekuva" / `org.nekosukuriputo.nekuva`. Do not introduce `doki` /
  `dokiteam` naming (attribution to the Doki/Kotatsu lineage in the README is the only exception).
- **Dependencies:** avoid new dependencies unless they're multiplatform-capable for our targets;
  add versions to `gradle/libs.versions.toml`, never inline them in a module build file.
- **Performance matters.** When choosing between elegance and performance in hot paths, prefer
  performance.

## Building & testing

- A change must build on **both targets**: `:composeApp:assembleDebug` (Android) and the Desktop
  build must be green.
- Run the app and exercise the screen you changed — a green build alone isn't enough.
- Add or update tests where it makes sense (shared tests go in `commonTest`). Don't delete tests
  just to make a build pass.

## Pull requests

- Keep PRs focused — one feature or fix at a time.
- Use clear, [Conventional Commit](https://www.conventionalcommits.org/) messages
  (`feat:`, `fix:`, `refactor:`, `chore:`, `build:`, `test:`).
- Describe what you changed and how you tested it (mention Android and/or Desktop).
- Make sure each commit builds.

## Translations

UI translations are managed as `composeResources/values-<lang>/strings.xml` files. To add or
improve a language, add/update the corresponding `strings.xml` with the same keys as the default
`values/strings.xml`. (Use the ISO-639-1 code, e.g. `values-id` for Indonesian.)

## License

By contributing, you agree that your contributions are licensed under the
**GNU General Public License v3.0**, the same license as the project.
