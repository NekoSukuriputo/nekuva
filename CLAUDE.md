# CLAUDE.md — Nekuva (UI App)

> Context file for **Claude Code**. Read fully at the start of every session.
> When a rule here conflicts with a prompt, **ask before deviating**. Never silently
> ignore a guardrail in this file.
>
> (This project was previously driven by another agent via `GEMINI.md`. If you still use
> that agent, keep `GEMINI.md` and this file in sync — this `CLAUDE.md` is now canonical.)

---

## 0. How you must work (READ FIRST) — non-negotiable

The previous agent repeatedly marked areas **"done"/"verified" based on COMPILE, not RUN**,
and runtime bugs (Desktop crashes, broken history, broken resume) slipped through. Do not
repeat that. Specifically:

- **Compile ≠ done.** `assembleDebug` / `compileKotlinDesktop` only prove it compiles. An area
  is done only when the app **actually runs** (`./gradlew :composeApp:run` on Desktop AND the
  Android app installed/launched) and the feature is **exercised with real data** (see DoD §7).
- **You cannot see the GUI.** You can run `gradlew` yourself (including `:composeApp:run`, which
  launches a Desktop window), and you can read its console output, but you **cannot see the
  rendered window or take a screenshot.** Therefore, before marking any area `[x]`, **stop and
  ask the human to run it and confirm with a screenshot.** Report the exact manual test steps to
  perform.
- **Never claim a verification you did not actually do.** If you only compiled, say "compiled,
  not yet run-verified" — never write "verified working".
- **Desktop is where hidden bugs surface.** Anything touching DB, timestamps, JSON, or the
  network often works on Android (which bundles libraries) but crashes on Desktop JVM at runtime
  (see §4.6). Always run Desktop for those areas.
- **Parity-first (§6.1) and the per-area DoD (§7) apply to every area.** Read the legacy Doki
  code first, produce a parity checklist for human review **before** writing code, and record
  every deferred item in `MIGRATION.md` (§6.2).
- **Treat stale status as suspect.** `task.md` / `walkthrough.md` may contain false "done" marks
  from the previous agent. Verify actual state against the running app; re-sync the ledgers to
  reality before continuing.

---

## 1. What this repo is

- **Nekuva** is the **UI application** of a manga reader (a Kotatsu/Doki-class app).
- It originated as a fork of DokiTeam/Doki, but **this is now an independent project,
  "Nekuva" by NekoSukuriputo.** Canonical package / namespace / applicationId:
  **`org.nekosukuriputo.nekuva`**. The old `org.dokiteam.doki` and the "Doki" brand are
  **fully retired.**
- **BRANDING RULE — no "doki" anywhere.** Never introduce `doki`, `dokiteam`, `DokiApp`,
  the `doki://` URI scheme, or the "Doki" display name in code, resources, manifests,
  configs, or docs. Use `org.nekosukuriputo.nekuva`, the `nekuva://` scheme, and the
  "Nekuva" name. If you find a stray "doki" reference, fix it. (GPL attribution to the
  Kotatsu/Doki lineage in license headers and the README "Credits" section is the only
  permitted exception.)
- The **reader parsers/scrapers live in a SEPARATE repo**: `nekuva-exts`. This repo
  consumes them as a published dependency. **Never copy parser/scraper code into this
  repo.** The boundary is sacred (see §8).

## 2. Goal

Migrate this Android-only app to **Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP)**
so it runs on **Android, Desktop (Windows + macOS), and eventually iOS** — while keeping the
`nekuva` (UI) and `nekuva-exts` (parsers) repos cleanly separated.

This is **not a light refactor — the UI layer is a rewrite** from XML Views to Compose
Multiplatform. Screens are **re-implemented**, not "ported". Android View / Fragment /
RecyclerView-Adapter / ViewBinding infrastructure has **no Compose equivalent and is deleted**,
replaced by Composables + `LazyColumn`/`LazyVerticalGrid`.

## 3. Current status (keep this updated as work progresses)

- **DONE:** Phase 0 skeleton + all `core` sub-sessions (model, prefs, db, util/io/fs/os,
  network/parser/image/github/exceptions, ui foundation).
- **DONE & run-verified (Android + Desktop):** `local`, `explore`/`list`/`remotelist`,
  `details` (+ visual parity), `reader` (MINIMAL — advanced reader deferred), `main` shell
  (adaptive nav), back navigation, `NekuvaTheme` (dark + AMOLED), and `favourites` (save to DB,
  category tabs, category management, "Manage/Kelola" in the favourite dialog). The
  `kotlinx.datetime.Clock` Desktop crash in favourites is fixed (now uses `kotlin.time.Clock`).
- **FIXED & run-verified (branch `fix/history-write-and-resume`, PR open — pending merge to main) —
  `history`:** the two human-verified bugs are resolved: (1) read manga now appears in History
  (write path upserts the manga before the history FK row; failures are no longer swallowed
  silently), and (2) "Read"/"Continue" now resumes via `listState.scrollToItem(savedPage)` with a
  guard so the transient page-0 during first layout can't overwrite the saved page. Advanced history
  parity stays deferred (see `MIGRATION.md`).
- **DONE & run-verified (Indonesian Android device) — i18n / multi-language:** Indonesian now renders
  fully (was English). Root cause was the `id`/`in` locale mismatch — fixed by shipping both
  `values-id` + `values-in` (and `values-he`→`values-iw`), enabling `generateLocaleConfig`, and
  sweeping the last hardcoded UI strings to `Res.string`. See §4.6. Keep `values-id`/`values-in`
  identical.
- The **active module is `composeApp`** using Koin, Room KMP, Compose Multiplatform,
  multiplatform-settings, Coil 3, and the source sets in §4.1.
- The old **`app` module is EXCLUDED from the build** and kept **only as migration reference**
  until the end of Phase 1, when it is deleted in a single final cleanup commit.
- The descriptions of the *original* Android stack (Hilt / Room-Android / XML Views / OkHttp /
  WorkManager / AndroidX Preference) below describe the **legacy source being migrated FROM**,
  not the current target.
- **Remaining Phase 1 areas (dependency order):** finish `history` → `bookmarks` →
  `search`/`filter` → `download` → `tracker`/`scrobbling`/`sync` → `settings` (enumerate EVERY
  Doki preference) → the rest (`alternatives`/`browser`/`picker`/`widget`/`backups`/`stats`/
  `suggestions`/`image`). Then the final parity audit (§6.2). Reader-advanced and incognito are
  their own deferred areas in `MIGRATION.md`. Current authoritative checklist: `task.md` /
  `MIGRATION.md` — **but re-verify their "done" marks per §0.**

## 4. Target architecture

```
nekuva/
├── composeApp/
│   └── src/
│       ├── commonMain/        # exts-free, platform-agnostic Compose UI + logic
│       ├── jvmSharedMain/     # intermediate set shared by android+desktop (OkHttp + exts)
│       ├── androidMain/       # Android Activity, manifest, platform actuals
│       ├── desktopMain/       # Desktop main(), platform actuals (JVM)
│       └── iosMain/           # iOS actuals (Phase 2 only)
├── iosApp/                    # Xcode wrapper (Phase 2 only)
├── app/                       # LEGACY Android module — excluded from build, reference only
├── gradle/libs.versions.toml  # single source of truth for versions
├── settings.gradle.kts
└── build.gradle.kts
```

### 4.1 Source-set layout (ACTUAL — Phase 1) — read this before placing any file

The project uses a hierarchical source set. **Where a file goes is decided by what it touches:**

- **`commonMain`** — ONLY exts-free, platform-agnostic code: pure `@Serializable` models,
  navigation routes & graph, theme, generic Compose components, exception classes. This is the
  iOS-ready subset.
- **`jvmSharedMain`** — intermediate source set shared by `androidMain` + `desktopMain`. **ALL
  code that touches `nekuva-exts` models (`Manga`/`MangaSource`/…), OkHttp, or okhttp
  interceptors lives here, written ONCE.** Repositories, parser glue (`AppMangaLoaderContext`
  base + the OkHttpClient Koin provider), and ViewModels/Composables that reference manga
  models go here.
- **`androidMain` / `desktopMain`** — platform `actual`s only: `Context`, WebView/JS engine,
  file paths & storage, connectivity, signing, widgets, background services.

**PLACEMENT RULE:** exts-free → `commonMain`; touches exts/okhttp → `jvmSharedMain`; needs a
platform API → `expect`/`actual` (android/desktop). **NEVER put okhttp or exts-model-touching
code in `commonMain` — it will not compile** (a pure-JVM artifact has no common metadata).
For Phase 1, `commonMain` vs `jvmSharedMain` is **not a functional difference** (both are JVM,
both shared across Android+Desktop, Compose works in both) — it only affects future
iOS-readiness. The clean common/iOS split is **Phase 2** work; do not over-engineer mappers now.

### 4.2 Technology mapping (Android → KMP). Decisions are LOCKED unless §11 approval.

| Legacy (Android) | KMP target | Notes |
|---|---|---|
| XML Views + ViewBinding | **Compose Multiplatform** | full re-implementation; adapters/fragments DELETED |
| Hilt | **Koin** (+ `koin-compose-viewmodel`) | |
| Room (Android) | **Room KMP, Fresh V1** (CHOSEN) | legacy migrations dropped, schema version reset to 1, table/column structure preserved, `exportSchema = true`, `BundledSQLiteDriver`, per-platform builder via expect/actual |
| OkHttp (parser/network) | **Keep OkHttp** in `jvmSharedMain` (CHOSEN) | nekuva-exts is OkHttp-based; keep the OkHttpClient + interceptors in jvmSharedMain. **Do NOT convert the parser/network layer to Ktor in Phase 1.** Ktor only for iOS (Phase 2) or app-only calls |
| Coil 3 | **Coil 3** | already multiplatform — keep |
| WorkManager / services | `expect`/`actual` or defer | Android-only background; defer to the relevant feature area (e.g. download), not core |
| AndroidX Preference | **multiplatform-settings** (CHOSEN) | per-platform `ObservableSettings`: Android wraps the same SharedPreferences (same file/keys/defaults); Desktop uses `java.util.prefs.Preferences` |
| Navigation (fragments/Intent) | **Compose Navigation, type-safe `@Serializable`** (CHOSEN) | routes carry **IDs/primitives, not exts models** (keeps nav in commonMain). `navigation-compose` ≥ 2.9.x |
| Material Components (XML) | **Compose Material 3** | |
| Android strings (`R.string`, `res/values-*`) | **Compose Multiplatform Resources** (CHOSEN) | `composeResources/values*/strings.xml`, accessed via `stringResource(Res.string.x)`. No hardcoded user-facing strings. Migrate Doki's translations ~1:1 (rename `values-in` → `values-id`). See §4.4 |
| ACRA, biometric, Conscrypt, widgets, Discord RPC | platform `actual` or defer | flag each; do not silently drop a user-facing feature |

> **Desktop note:** Compose for Desktop needs `kotlinx-coroutines-swing` for `Dispatchers.Main`.
> **JS eval note:** `MangaLoaderContext.evaluateJs` is currently **stubbed**. Sources that need
> JS / CloudFlare bypass will not work until the WebView (Android) / JS-engine (Desktop) actuals
> are implemented during the reader/webview work. Track this; do not pretend those sources work.

### 4.3 Library versions

**Never hardcode library versions from memory.** Check the CMP/Kotlin version already in
`libs.versions.toml` and align new dependencies to it, and verify the current stable version
before adding. As of this project's stack: Compose Multiplatform 1.10.x, navigation-compose 2.9.x,
Kotlin 2.2.x, Gradle 9.2.1. Avoid stale alpha versions that don't match the stack. Add every
version to `libs.versions.toml`; never inline a version in a module build file.

### 4.4 i18n / strings (LOCKED)

All user-facing text MUST go through **Compose Multiplatform Resources**, never hardcoded in the UI.

- Place strings in `composeApp/src/commonMain/composeResources/values/strings.xml` (default) and
  per-language `values-<code>/strings.xml` (e.g. `values-id`, `values-en`, `values-de`). Access via
  `stringResource(Res.string.x)`. The XML format matches Android's, so Doki's existing translations
  migrate ~1:1.
- **Migrate Doki's translation catalog** from `res/values-*/strings.xml` into
  `composeResources/values-*/`. **For Indonesian, ship BOTH `values-id` AND `values-in` with
  identical content** (do NOT just rename one). Reason: Indonesian has two language codes — modern
  `id` (iOS, Android 15+, and some Desktop JVM setups) and legacy `in` (Android < 15 and the JVM
  `Locale`, which maps `id` → `in`). Compose Resources matches the folder qualifier against the
  platform's *resolved* code, so if only `values-id` exists, a device that resolves to `in` finds no
  match and silently falls back to English. Keeping both folders covers every platform/version. The
  same legacy/modern split affects Hebrew (`iw`/`he`) and Yiddish (`ji`/`yi`) — duplicate those too if
  present in the catalog. Verify by RUNNING on a device actually set to the language.
- For **relative text like dates** ("Today"/"Yesterday" ↔ "Hari ini"/"Kemarin"), use per-language
  string resources + `kotlinx-datetime` for the date math — do not hardcode either the format or the
  words. Read Doki's date util (`app/src/main/.../core/util/ext/Date.kt`) for parity behaviour.
- Existing hardcoded labels introduced during early migration (e.g. "Manga Details", "Chapters",
  "Synopsis", "Local List") are temporary debt and must be replaced with string resources.
- The full string/translation migration is its own sizable task; introduce the `composeResources`
  setup now for strings already in use (incl. relative-date strings), and migrate the complete
  catalog as a dedicated step. Do not block other areas on the full migration, but do not add new
  hardcoded strings either.
- **Reuse Doki's string KEY NAMES.** When a screen needs a string, use the same key name Doki uses in
  its `res/values/strings.xml` (the legacy layouts reference `@string/<key>`). This keeps the bulk
  translation import aligned and gives all languages for free. Do not invent new keys for concepts
  Doki already has a key for.

### 4.5 UI parity & theme (LOCKED)

- **Theme:** Port Doki's Material 3 theme into a Compose `NekuvaTheme` in `commonMain`
  (`darkColorScheme` + `lightColorScheme` mirroring Doki's color tokens, plus an AMOLED/true-black
  option as Doki has). **Default to DARK** (reader app — avoid glaring white); may follow system with a
  dark fallback. Apply `NekuvaTheme` at the root so every screen uses it; **no hardcoded white/colors —
  use `MaterialTheme.colorScheme.*`**. Mirror Doki's tokens; do not invent a new palette. Read Doki's
  `res/values/colors.xml`, `themes.xml`, `styles.xml` (and any theme code) for the values.
- **Visual parity is part of §6.1:** parity covers LAYOUT / visual structure, not just behaviour.
  Reproduce Doki's screen structure (info cards, bottom-sheets / slide-up dialogs, genre chips,
  list-item layouts, tabs) by reading the legacy `res/layout/*.xml`. Never ship plain default-styled
  screens. Pixel-perfect is not required (Compose ≠ XML Views), but the result must clearly look and
  feel like Doki.

### 4.6 Known Desktop runtime gotchas (learned the hard way — check these)

Android bundles many libraries in the platform; **Desktop JVM does not**, so code that compiles
and runs on Android can throw `NoClassDefFoundError` (or behave differently) at runtime on Desktop.
Always `:composeApp:run` Desktop for any area touching DB / time / JSON / network.

- **`org.json`** — excluded from the exts dependency (Android bundles it). Desktop JVM does NOT;
  `org.json:json` is added to **`desktopMain` only**. Removing it crashes JSON-based sources at
  runtime on Desktop. (See §8.)
- **`kotlinx.datetime.Clock`** — in kotlinx-datetime **0.7.0+ the `Clock` class moved to the Kotlin
  stdlib `kotlin.time`**. Use **`kotlin.time.Clock.System.now()`** (with
  `@OptIn(kotlin.time.ExperimentalTime::class)` where needed), NOT `kotlinx.datetime.Clock`, or you
  get `NoClassDefFoundError: kotlinx/datetime/Clock$System` on Desktop. `LocalDate`/`LocalDateTime`
  formatting still lives in kotlinx-datetime and is fine. If you add time logic, grep the codebase for
  any remaining `kotlinx.datetime.Clock` usages.
- **History / favourites writes need the manga in the DB first.** History (and category) rows
  reference the manga via a foreign key, so the **`Manga` must be upserted into the Room `manga`
  table before** writing the history/favourite row — otherwise the insert fails or the
  `history JOIN manga` query returns empty (symptom: read manga never shows in History). Favourites
  already upsert the manga; the reader's history-write path must do the same. Read Doki's
  `history/HistoryRepository.addOrUpdate` / `HistoryUpdateUseCase` for the exact order.
- **FIXED (2026-06-08, run-verified on an Indonesian Android device) — i18n / multi-language:** the
  `id`/`in` Indonesian mismatch is resolved by shipping BOTH `composeResources/values-id` AND
  `values-in` with identical content (and `values-he`→`values-iw`). Added
  `androidResources { generateLocaleConfig = true }` + `src/androidMain/res/resources.properties`
  (`unqualifiedResLocale=en`). Remaining hardcoded nav/category/empty-state strings were swept to
  `Res.string` (reusing Doki keys; added `cancel`/`loading`, restored `default_category` id). Also
  fixed an `app_name` branding slip ("Doki"→"Nekuva"). Verified: all UI text shows Indonesian.
  **Maintenance:** `values-id` and `values-in` MUST be kept identical when editing the catalog.
- **General rule:** when a write "succeeds" but nothing shows in the UI, suspect a silently-swallowed
  exception or a missing FK row — log it and run on Desktop to see the stack trace.
- **Localization silently falls back to English (esp. Indonesian).** Compose Resources matches the
  folder qualifier against the platform's *resolved* language code. Indonesian resolves to legacy
  `in` on Android < 15 / the JVM but modern `id` on Android 15+ / iOS, so you must ship BOTH
  `values-id` and `values-in` (see §4.4) or strings silently fall back to English. Also confirm the
  string actually goes through `Res.string`/`stringResource` — hardcoded strings never localize. On
  Android, `androidResources { generateLocaleConfig = true }` declares supported locales. Always
  run-verify localization on a device set to that language; do not trust that "the catalog exists".

## 5. Phasing — work in this order. Do NOT jump ahead.

The repo **builds and runs on every commit.** Never end a task in a non-compiling state.

- **Phase 0 — Skeleton.** ✅ Done.
- **Phase 1 — Android + Desktop parity.** In progress. Re-implement screens in Compose, one
  feature area at a time (§6). Both **Android and Desktop must build AND run**. Uses the
  existing JVM parser engine (nekuva-exts) unchanged.
- **Phase 2 — iOS.** Only after Phase 1 is stable. iOS is blocked by OkHttp/jsoup being
  JVM-only inside `nekuva-exts`; the exts-side change is designed/coordinated first.

> If a task would force you to skip a phase, break the Android build, or leave the Desktop app
> unable to launch, **stop and ask.**

## 6. Migration order (Phase 1 feature areas)

`task.md` / `MIGRATION.md` hold the authoritative checklist and **must list every real feature
folder** found under the legacy `app/src/main/.../` tree. Do not drop real areas or invent
replacements. The real areas are: `local`, `explore`, `list`, `remotelist`, `details`, `reader`,
`image`, `search`, `filter`, `favourites`, `history`, `bookmarks`, `download`, `tracker`,
`scrobbling`, `sync`, `settings`, `main`, `alternatives`, `browser`, `picker`, `widget`,
`backups`, `stats`, `suggestions`.

Rough dependency order: `local` → `explore`/`list` → `details` → `reader` → `search`/`filter` →
`favourites`/`history`/`bookmarks` → `download` → `tracker`/`scrobbling`/`sync` → `settings` →
`main` shell → the rest.

**Vertical-slice priority:** get the path **"browse one source → details → read one chapter"**
(`explore`/`list` → `details` → `reader`) working end-to-end on Android + Desktop as the
architecture-validation gate before grinding through the remaining areas. This is where the
nekuva-exts / OkHttp / evaluateJs integration is truly tested. (This gate is already passed.)

Per area: move domain + data first (jvmShared/common per §4.1), build the Compose screen, wire DI
+ navigation, confirm parity, then (only at the end of Phase 1) delete the legacy `app` source.
**Do not delete a feature's legacy code before that feature is migrated** — it is the reference.

### 6.1 Feature parity & reference-first (MANDATORY for every feature area)

The goal is **full feature parity with Doki, not a minimal clone.** Before migrating any feature area:

1. **Read the legacy implementation first** in `app/src/main/kotlin/org/dokiteam/doki/<area>/`
   (Fragment + ViewModel + Adapter/dialogs) together with its `res/layout/*.xml` and related
   strings. The `app` module is excluded from the build, but its source stays on disk as the
   **canonical reference — read it.**
2. **Produce a PARITY CHECKLIST** of every behaviour the old screen has: states, actions, sorting,
   pagination/load-more, empty/loading/error states, menus, formatting (e.g. relative dates),
   placeholders, **and the visual layout/structure (cards, bottom-sheets, chips, item layouts — see
   §4.5)**. Present it for review before implementing.
3. **Reproduce every item**, or explicitly **defer** an item to its own area and record it in
   `MIGRATION.md`. **Never silently drop a behaviour** to ship a simpler "fresh" version.
4. The Definition of Done (§7) checks the new screen against this checklist.

### 6.2 Completeness ledger & final parity audit (so NO Doki feature is missed)

- **`MIGRATION.md` is the AUTHORITATIVE LEDGER of every deferred item.** Whenever §6.1 defers a
  behaviour, write it to MIGRATION.md against the area it belongs to. **Phase 1 is NOT complete while
  the ledger has open items** (an item may be consciously dropped only if recorded with a reason).
- **Features are NOT only in the 25 folders.** Many live in `settings/` (dozens of preferences:
  reader, theme/AMOLED, network/proxy/DoH, downloads, tracking, backup/restore, incognito, etc.), in
  `core/`, or are cross-cutting: incognito mode, app shortcuts, share targets, quick search, reading
  **stats**, "open random", app-lock/**biometric**, update checker, in-app browser, image saving.
  Each area's parity checklist MUST cover these — in particular the **`settings` area must enumerate
  EVERY Doki preference**, one by one.
- **Final parity audit (before Phase 1 is declared done):** walk the legacy Doki app
  screen-by-screen and menu-by-menu — every settings toggle, every overflow/context menu, every
  long-press action, every gesture, every empty/error state — and confirm each exists in Nekuva or is
  consciously dropped (recorded). A green build with "all areas marked [x]" is **NOT** sufficient;
  the audit is the gate. Produce the audit as a checklist for human review.

## 7. Definition of Done (per feature area) — a green build is NOT enough

An area is DONE only when **all** hold:

1. **Built from the legacy reference (§6.1):** a parity checklist was produced and every item is
   reproduced or explicitly deferred (recorded in `MIGRATION.md`). No behaviour silently dropped.
2. **Real data, no mock.** The screen shows real data from its real source (storage, DB, or
   nekuva-exts) — or a correct empty/loading/error state. **Hardcoded/mock data NEVER counts as
   done** (mock is allowed only in unit tests, never in the production UI path).
3. **No hardcoded user-facing strings** — text comes from Compose Resources (§4.4).
4. Core interactions work (list scroll, tap → navigate, etc.), matching the legacy screen.
5. `./gradlew :composeApp:assembleDebug` (Android) **and** the Desktop build are green.
6. **The app actually RUNS** (`./gradlew :composeApp:run`) and the screen is exercised manually
   with real data — **not just compiled.** Because you cannot see the GUI, **ask the human to run
   it and confirm with a screenshot** (give the exact test steps). Do not self-certify a run you
   could not observe.
7. Only then mark the area `[x]`.

If something genuinely breaks (e.g. stubbed `evaluateJs`, OkHttp on Desktop), **report it** —
never paper over it with a stub/mock to fake a green/"done" state.

## 8. The nekuva ⇄ nekuva-exts contract — NEVER break casually

- nekuva-exts exposes: `MangaParser`, `MangaLoaderContext` (**abstract class the host
  implements**), `Bitmap`/`Rect` (interfaces), the data models, and `@MangaSourceParser` + KSP.
- This app's only job toward parsers is to provide a concrete `MangaLoaderContext`: HTTP client
  (OkHttp in jvmShared), JS evaluation (expect/actual — Android WebView / Desktop JS engine),
  base64, locales, bitmap creation, image descrambling.
- **Rules:**
  - Do not add scraping/site-specific logic here. New sources go to `nekuva-exts`. If asked to
    add a site parser here, refuse and redirect.
  - Consume exts as a **versioned published dependency**:
    `com.github.NekoSukuriputo:nekuva-exts:<tag>` with `exclude(group = "org.json", module = "json")`.
    Import from `org.nekosukuriputo.nekuva.parsers.*` — **never** `org.dokiteam.doki.parsers.*`
    (that means the old doki-exts is being pulled and must be fixed). Update the version in
    `libs.versions.toml`; do not vendor the source.
  - **`org.json` caveat:** Android bundles `org.json` in the platform, so it is excluded above.
    **Desktop JVM does NOT bundle it** — add `implementation("org.json:json:<stable>")` to
    `desktopMain` only, or JSON-based sources crash at runtime with `NoClassDefFoundError`. (§4.6)
  - If the parser API must change for KMP/iOS, **propose the change in the exts repo first**,
    then adapt here. Never fork the contract locally.

## 9. Code quality

- Kotlin official style; `.editorconfig` is authoritative (tabs etc.) — match it.
- Respect the §4.1 placement rule. Justify any platform-source-set file with a one-line comment.
- Coroutines + Flow for async; no blocking calls on the main dispatcher. ViewModels expose
  `StateFlow` for state and a `SharedFlow`/`Channel` for one-shot events; centralize coroutine
  exception handling (carried over from the legacy BaseViewModel, modernized).
- No new dependency unless multiplatform-capable for our targets; add it to `libs.versions.toml`
  (see §4.3). No `localStorage`/web-storage patterns.
- No hardcoded user-facing strings — use Compose Resources (§4.4).
- Keep files focused; split a composable past ~400 lines. Public API of shared modules: explicit
  return types.

## 10. Testing

- Keep/port JVM unit tests; shared tests in `commonTest`. Each migrated area needs at least
  ViewModel/state tests and a smoke check that the screen renders.
- Do not delete a test to make a build pass; explain obsolete tests and ask.
- Before declaring a phase done: Android `assembleDebug` and the Desktop run task both succeed,
  and the Definition of Done (§7) is met for every area in that phase.

## 11. Safety guardrails — confirm with the human before:

- Marking any area "done" with mock/placeholder data instead of real data (§7), or marking it
  "done" on a compile you have not run-verified (§0).
- Deleting legacy `app` code for a feature **before** that feature is migrated, or deleting the
  `app` module before the end of Phase 1.
- Changing the `nekuva-exts` dependency contract, coordinate, or version strategy.
- Swapping a LOCKED technology in §4.2 (Room/Koin/multiplatform-settings/Compose Navigation/OkHttp).
- Dropping or skipping ANY Doki user-facing feature. **The default is to migrate EVERY Doki
  feature (§6.1 / §6.2 = full parity) — nothing is excluded.** The hard, platform-specific ones
  (ACRA crash reporting, Telegram backup, biometric lock, Discord RPC, home-screen widgets, sync)
  are still migrated — implemented via `expect`/`actual` on the platform(s) where the concept
  exists (e.g. an Android home-screen widget stays an Android `actual`; Desktop/iOS have no such
  concept, which is platform-appropriate N/A, **not** a drop; biometric uses each platform's API).
  Only if a feature genuinely **cannot** be migrated as-is do you list exactly what would be lost
  and ask first. **Never drop or quietly omit a feature to ship faster.**
- Reintroducing any "doki" naming, or mass-renaming `org.nekosukuriputo.nekuva` / the app id.
- Any `git push --force`, history rewrite, or branch deletion.

## 12. Git & workflow

- One feature area (or one sub-session) per branch + per commit at the end of the session.
  Conventional Commits: `feat:`, `fix:`, `refactor:`, `chore:`, `build:`, `test:`.
- Keep commits small and reviewable; each must build.
- **Commit only after the area is run-verified (§0/§7), not just compiled.**
- Update this `CLAUDE.md` (and `GEMINI.md` if still used), `task.md`, `MIGRATION.md`, and the
  README when an architectural decision changes.

## 13. License

GPL-3.0. Preserve license headers and Kotatsu/Doki attribution in the README "Credits" section.
Do not introduce dependencies incompatible with GPL-3.0.
