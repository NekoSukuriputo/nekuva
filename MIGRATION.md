# Nekuva KMP Migration Plan

## Phase 0: Skeleton (Kerangka Multiplatform)
Fokus: Bangun kerangka KMP/CMP tanpa memigrasikan fitur, memastikan aplikasi Android saat ini tetap bisa di-build.
- Konversi Gradle ke Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`).
- Siapkan `gradle/libs.versions.toml` dengan dependensi KMP (Kotlin Multiplatform, Compose Multiplatform, Room KMP, Koin, Ktor client, Coil 3).
- Buat modul `composeApp` (target Android & Desktop).
- Entrypoint: Android (`androidMain`) dan Desktop (`desktopMain`) menampilkan placeholder Compose Material 3 ("Nekuva KMP — Phase 0 OK").
- Setup dependensi plumbing di `commonMain` (Koin, Ktor, Room KMP, Coil 3).
- Sambungkan `nekuva-exts` dan buat implementasi `MangaLoaderContext` konkret per platform (WebView Android, Desktop JVM engine JS).
- Setup keystore release baru (bukan milik doki).
- **Kriteria Selesai Phase 0**: `./gradlew :composeApp:assembleDebug` hijau, Desktop run jalan, `nekuva-exts` tersambung. Tidak ada fitur lama yang terhapus dan TIDAK ADA KODE/FILE BARU dengan branding "doki".
**(SELESAI)**

## Phase 1: Android + Desktop Parity (Re-implementasi Fitur)
Fokus: Membangun ulang seluruh UI/fitur dari XML Views ke Compose Multiplatform satu per satu, mendukung Android dan Desktop (Windows/macOS).

### Keputusan Source Set Non-Main:
- `jvmSharedMain`: Digunakan untuk membagikan kode JVM (Desktop dan Android) yang masih berkaitan dengan library spesifik Java seperti OkHttp atau `nekuva-exts` (yang membutuhkan arsitektur JVM seperti `java.io.File`, JS evaluation engines). Di Phase 1, ekstensi dan network parsing belum bisa berada di `commonMain` karena dependensi Jsoup dsb. Pemisahan lebih lanjut untuk iOS dilakukan di Phase 2.
- `androidMain`: Digunakan untuk fitur spesifik Android saja, mis. Widget, Service (WorkManager), Android `Context`, dan WebView untuk JS engine.
- `desktopMain`: Digunakan untuk JVM standalone, JCEF/Rhino untuk JS engine, file I/O lokal JVM murni.
- `legacy` / `release` / `debug`: Dihapus sepenuhnya dari arsitektur baru. Proyek ini dibangun *fresh* dengan Gradle Kotlin DSL dan KMP yang bersih. Jika dibutuhkan build flavor, maka diletakkan via Gradle configs.

### Progress Checklist (25 Area Fitur & Core)

- [x] **Core** (Models, Prefs, Network, DB, Util Infrastructure, Theme Dark, i18n ComposeResources + Bulk Bahasa, Navigasi Back)
- [x] `local` (Storage Manager, Local Manga)
- [x] `explore`
- [x] `list`
- [x] `remotelist`
- [x] `details` (Done, resume + favorites integrated)
- [~] `reader` (reader-advanced sedang dikerjakan bertahap. **R1 DONE**: 4 mode baca fungsional
      (Standard LTR / Kanan-ke-kiri / Vertical-paged / Webtoon-continuous) via HorizontalPager/VerticalPager +
      pinch/double-tap zoom + pan + tap-zone navigasi; mode dipilih dari menu & **diingat** (default = WEBTOON).
      **R2 DONE**: terapkan setting reader yang lintas-platform — background reader (DEFAULT/LIGHT/DARK/WHITE/BLACK),
      info bar bawah (nama bab + halaman x/total), nomor halaman; setting "Mode baca default" di Settings kini
      terhubung. **R3 DONE** (run-verified Android+Desktop): baca manga lokal/unduhan **offline** — `LocalImageFetcher`
      (Coil) memuat gambar `zip:`/`file:` (drive-letter Windows aman), dan reader memilih sumber **per-bab**
      (`getPagesIfDownloaded`): bab yang sudah diunduh dibaca dari disk, sisanya online (offline-first ala Doki).
      **R4 sedang berjalan (compile-verified Android+Desktop, pending run-verify):**
      **R4a DONE** — *bottom actions overlay* ala Doki `ReaderActionsView`: slider halaman (seek dalam bab) +
      tombol bab sebelum/sesudah + chip kontrol (chapters/bookmark/save) yang dirender dari preferensi
      `reader_controls` (PREV/NEXT/SLIDER/PAGES_SHEET/BOOKMARK fungsional; SAVE_PAGE kini fungsional;
      SCREEN_ROTATION/TIMER masih placeholder). Tombol FAB tengah lama diganti bottom bar.
      **R4b DONE** — *color filter*: brightness/contrast/grayscale/invert/book diterapkan ke gambar via Compose
      `ColorFilter`/`ColorMatrix` (`ReaderColorFilter.toComposeColorFilter()`), override per-manga
      (`MangaDataRepository.observeColorFilter`) jatuh ke global (`AppSettings.readerColorFilter`, KEY_CF_*);
      panel "Koreksi warna" in-reader (slider+toggle, live) + tombol Reset. Config sheet "Settings" → buka Reader
      Settings; toggle double-page/pull-gesture kini persist ke settings.
      **R4c DONE** — *Page Save & Share*: `PageSaveHelper` (jvmShared) ambil byte halaman (http via image-proxy;
      `zip:`/`file:` baca langsung dari disk) + `PagePersister` expect-pattern via DI: Android → MediaStore
      (Pictures/Nekuva) + share lewat content Uri; Desktop → `~/Pictures/Nekuva` + reveal folder. Wired ke
      menu Save/Share + chip Save di bottom bar; snackbar `page_saved`/`error_occurred`.
      **R4c+ "Final polish reader" (bagian A, lintas-platform) sedang berjalan:**
      **A#1 DONE** — auto-scroll: webtoon scroll kontinu + paged auto-advance, kecepatan diatur via kontrol
      slider in-reader (Doki `ScrollTimer`/`ScrollTimerControlView`), toggle dari chip TIMER + menu; lokasi simpan
      ditampilkan di toast save/share.
      **A#6 DONE** — zoom modes: FIT_CENTER/FIT_HEIGHT/FIT_WIDTH/KEEP_START dipetakan ke Compose `ContentScale`
      (+ align top utk KEEP_START) di paged reader.
      **SISA bagian A:** tap-grid 9-zona configurable (`TapGridSettings`/`TapAction`) + layar konfigurasi, double-page
      renderer (toggle sudah persist) + sensitivity + double-foldable, reader-mode auto-detect (`DetectReaderModeUseCase`),
      info bar jam+baterai (`ReaderInfoBarView`) + transparansi, pinch-zoom+pan koeksis paging, webtoon gaps +
      webtoon zoom-out, page transition animation, pages-crop/32-bit/optimize, image-server/mirror switch, toast
      bookmark/ganti-bab, tombol keyboard tambahan (space/PageUp-Down/R/L/Ctrl+panah).
      **Bagian B DONE (Android platform-actual via expect/actual `ReaderWindowController` + `ReaderKeyEvents`;
      Desktop = no-op):** #14 rotate/orientation lock (chip + menu, toggle portrait↔landscape; disembunyikan di
      Desktop; pref `reader_orientation` diterapkan saat reader dibuka), #15 navigasi tombol volume (MainActivity
      `onKeyDown` → reader: paged ±1 halaman, webtoon ~1 viewport; hormati `reader_volume_buttons` +
      `reader_navigation_inverted`), #16 keep-screen-on (`FLAG_KEEP_SCREEN_ON` saat dibuka, clear saat keluar),
      #17 fullscreen/immersive (sembunyikan system bars via `WindowInsetsControllerCompat`, restore saat keluar).
      **SISA bagian A (lanjut setelah B):** tap-grid config, double-page renderer, auto-detect mode, info-bar
      jam/baterai, pinch-zoom+pan, webtoon gaps/zoom-out, page animation, crop/32-bit/optimize, image-server,
      toast bookmark/ganti-bab, tombol keyboard tambahan.
      **Bagian C sebagian DONE:** #18 **incognito** (run-verifiable) — resolusi dari `AppSettings`
      (`isIncognitoModeEnabled` global / `incognitoModeForNsfw` TriState; NSFW + ASK → dialog prompt ala Doki dgn
      "jangan tanya lagi"); saat incognito history TIDAK ditulis + tidak di-scrobble; indikator "Incognito mode" di
      top bar + toggle di config sheet. #20 **auto-scrobble** (compile/DI-verified; runtime butuh OAuth) —
      `ScrobblerManager.scrobble(manga, chapterId)` dipanggil saat bab berganti (skip saat incognito) via
      `tryScrobble`. #19 **branch/translation selector** (run-verifiable di sumber multi-branch) — VM simpan
      `allChapters` (semua branch) + `selectedBranch` (default = branch bab yg dibuka, ala Doki preferred-branch),
      `chapters` = filter ke selectedBranch (nav/append/sheet semua per-branch); picker **BranchSelector**
      (dropdown Translate + nama branch + jumlah, ✓ pada yg aktif) di header chapters sheet, tampil hanya bila >1
      branch; tap bab di branch lain otomatis pindah branch. **Bagian C SELESAI** (compile-verified Android+Desktop;
      auto-scrobble runtime butuh OAuth).
      **A#7 DONE** — pinch-zoom + pan di paged reader: double-tap zoom in → pinch sesuaikan + drag pan (dibatasi
      tepi); transform gesture HANYA aktif saat zoom (scale>1) DAN pager `userScrollEnabled=false` saat zoom
      (supaya pan tidak dimakan pager); di 1× HorizontalPager/VerticalPager tetap dapat swipe.
      **FIX regresi #19 + boundary loading (port `onCurrentPageChanged` Doki):** navigasi bab pakai daftar PENUH
      (branch selector hanya filter tampilan sheet); boundary loading kini pakai indeks halaman **terakhir terlihat**
      (`visibleItemsInfo.last`) untuk append bab berikutnya — bukan `firstVisibleItemIndex` (yang bikin trigger tak
      jalan); **prepend bab sebelumnya** saat halaman pertama dekat awal (webtoon; key LazyColumn stabil jaga posisi).
      **A#7 (zoom) v3 — PAGED + WEBTOON keduanya:** PAGED (Standard/RTL/Vertical) pakai `Modifier.transformable(
      state, enabled = zoomed)` + double-tap + pager `userScrollEnabled=false` saat zoom (`ZoomablePage`). WEBTOON
      (ala Doki WebtoonImageView): seluruh LazyColumn di-`graphicsLayer(scale, translationX)`; pinch 2-jari zoom +
      double-tap toggle 1×/2× + drag horizontal pan saat zoom, sedang drag vertikal LOLOS ke scroll LazyColumn
      (awaitEachGesture: konsumsi hanya pinch & pan horizontal). maxScale 3× (webtoon) / 5× (paged).
      **A#12 DONE** — toast reader (ala `ReaderToastView`): "Bookmark added/removed" + nama bab saat ganti bab
      (hormati `reader_chapter_toast`), via snackbar.
      **FIX UI reader (sesuai screenshot user):** bug `readerControls` (default `emptySet` → `?: DEFAULT` tak pernah
      jalan + map by-name padahal store by-ordinal) diperbaiki → tombol bawah muncul lagi; bottom bar jadi
      **docked rounded card** ala Doki `toolbar_docket` (chapters/prev/slider/next/save/timer/rotate/bookmark satu
      baris); **info bar default HIDE** (`reader_bar`=false di reader+settings+AppSettings, ala Doki visibility=gone).
      **FIX runtime crash `NoClassDefFoundError: kotlinx/datetime/Instant`** (saat info bar diaktifkan): kotlinx-datetime
      di-resolve ke **0.7.1** (transitive) yang MENGHAPUS `kotlinx.datetime.Instant` (pindah ke `kotlin.time.Instant`),
      tapi versi yg dideklarasi masih 0.6.1 (skew compile vs runtime). FIX: bump kotlinx-datetime → 0.7.1 di
      libs + 2 hardcoded build.gradle, dan pakai `kotlin.time.Clock`/`kotlin.time.Instant` (stdlib) + ekstensi
      `kotlinx.datetime.toLocalDateTime` — JANGAN `kotlinx.datetime.Instant` (§4.6). Sama difix di SyncSettingsScreen.
      **A#5 DONE** info-bar jam+baterai (jam via kotlinx-datetime ticker 15s; baterai `rememberBatteryPercent`
      expect/actual — Android BroadcastReceiver, Desktop null) + halaman + bab. **A#8 DONE (gaps)** webtoon gaps
      (`Arrangement.spacedBy` saat `webtoon_gaps`). **A#9 DONE** page animation (`reader_animation` NONE →
      `scrollToPage` instan; selain itu animasi). **A#13 DONE** keyboard paged: Space/PageDown/R = next, PageUp/L = prev.
      **A#2 DONE** tap-grid 9-zona configurable (`TapGridArea`/`TapAction`/`TapGridSettings` di ObservableSettings,
      default ala Doki) + dispatch di **paged** (PAGE/CHAPTER/TOGGLE_UI/SHOW_MENU; RTL swap saat reversed+!alwaysLTR).
      **WEBTOON: tap apa saja = toggle UI** (long-press = menu) — bukan grid, karena continuous-scroll perlu
      tap-to-toggle (grid bikin kontrol tak bisa di-hide). + **layar konfig** (Settings→Reader→"Reader actions": grid 3×3, toggle Tap/Long-tap,
      Reset/Disable-all). **A#8 webtoon zoom-out DIBATALKAN** (bikin webtoon tak full-width; webtoon kini selalu
      mulai skala 1.0 full-width ala Doki, pinch tetap 1×–3×). **FIX `readerControls`** fallback ke DEFAULT saat
      resolve KOSONG (bukan cuma unset) — set kosong (user uncheck semua) sempat bikin bottom bar tanpa tombol
      (tombol ≡ chapters hilang); kini bar selalu punya kontrol inti termasuk chapters.
      **A#4 DONE** auto-detect mode (`DetectReaderModeUseCase` + `decodeImageBounds` expect/actual — Android
      BitmapFactory bounds, Desktop ImageIO reader; per-manga saved mode menang, else webtoon bila rasio tinggi;
      `setReaderMode` kini simpan per-manga juga).
      **A#3 DONE double-page (v1):** PagedReader kini paging atas "units" — single = 1 halaman/unit (identik,
      tanpa regresi), double = spread ≤2 halaman (cover solo, pair 2-by-2). Aktif hanya saat landscape
      (`BoxWithConstraints`: maxWidth>maxHeight) + mode Standard/RTL + `reader_double_pages` ON. RTL menaruh
      halaman bernomor-lebih-kecil di kanan. Tap zone dipetakan ke seluruh spread. Index mapping page↔unit untuk
      resume/history/append/slider.
      **A#3b DONE — zoom per-spread:** `DoublePageSpread` kini pinch-zoom + pan + double-tap (1×/2.5×) ala
      `ZoomablePage`; saat zoom, pager berhenti swipe (`onZoomChanged`→`pageZoomed`).
      **A#crop DONE — Crop pages (Doki `reader_crop`, per mode):** `CropBordersTransformation` (Coil
      `Transformation`) memangkas margin seragam (putih/hitam) — `trimImageBorders` expect/actual: Android
      `getPixels` bulk + `Bitmap.createBitmap`, Desktop Skia `getColor` scan + `extractSubset` (zero-copy,
      pixelref refcounted). Edge-scan dari 4 sisi ke dalam (stride) → murah utk border tipis. Toggle in-reader
      per-bucket mode (`AppSettings.setPagesCropEnabled`).
      **A#32bit DONE — 32-bit color (`enhanced_colors`):** `ImageRequest.Builder.applyEnhancedColors` expect/actual
      — Android `bitmapConfig` ARGB_8888 (ON) / RGB_565 (OFF, default Doki), Desktop no-op (Skia full-quality).
      Toggle in-reader (`AppSettings.is32BitColorsEnabled`).
      **A#imgserver DONE — Image-server/mirror switch (Doki `ImageServerDelegate`):** ternyata feasible in-repo —
      `ConfigKey.PreferredImageServer` ADA di nekuva-exts. ViewModel baca `ParserMangaRepository.getConfigKeys()`
      → preset (entryValue/label), simpan via `getConfig()[key]=` (prefs sama Doki), `invalidateCache()` + reload
      bab di posisi sekarang. Dialog single-choice in-reader, baris hanya muncul bila source punya key.
      Opsi crop/32-bit/image-server dialirkan ke renderer via `LocalReaderImageOptions` + `rememberReaderPageModel`.
      (Compiled green Android+Desktop; **belum run-verified — perlu screenshot user**.)
      **`reader_optimize` — N/A-by-architecture:** di Doki itu knob internal SSIV (sampleSize halaman
      off-screen di `BasePageHolder`); Coil sudah auto-downsample ke ukuran tampil + kelola memory cache, jadi
      tak ada padanan langsung. Bukan fitur user-facing yang di-drop.
      **A#trim DONE — page trimming memori (Doki `PAGES_TRIM_THRESHOLD`=120):** `trimLoadedPages` membuang bab
      terjauh saat list kontinu >120 halaman & >1 bab (bab yang sedang dibaca tak pernah dibuang). **Webtoon saja**
      — stable item key LazyColumn jaga posisi (mekanisme sama prepend); mode paged (Pager) di-skip karena
      front-removal akan loncat index — itu satu-satunya bagian trim yang sengaja tidak dikerjakan.
      **A#incognito-bookmark DONE:** buka reader dari bookmark (Bookmarks list + sheet Detail) kini paksa incognito
      (`ReaderRoute.incognito=true`) + toast "Incognito mode" (Doki parity) — history/scrobble tak ditulis.
      **MASIH DEFERRED (butuh perangkat/dependency atau area lain, bukan drop):**
      (a) double-page **wide-page→solo** (butuh dimensi SEMUA halaman + reflow spread; pre-decode tiap halaman terlalu mahal),
      (b) **double-foldable** (`reader_double_foldable`; butuh androidx.window fold-state + perangkat foldable utk uji),
      (c) **RegionBitmapDecoder/SSIV subsampling** (Coil sudah downsample; SSIV-spesifik),
      (d) **evaluateJs** (WebView/JS-engine — area `browser`, deferred terpisah).)
- [x] `main` (Shell, adaptive navigasi)
- [ ] `image`
- [x] `search` (run-verified Android+Desktop: global multi-source search streaming (Riwayat/Disukai/Lokal + sumber paralel), saran as-you-type S1 (tag/manga/riwayat-query/sumber+switch/penulis, hormati `searchSuggestionTypes`, tak dicatat saat incognito), footer "Cari sumber nonaktif" + "Buka di browser" pada error. Lihat ledger Area Search & Filter)
- [x] `filter` (run-verified Android+Desktop: sheet filter parity Doki — Urutkan + Bahasa/Bahasa-asli + Penulis + Genre (+ katalog tag lengkap berpencarian) + Kecualikan + Tipe + Status + Content-rating + Demografi + Tahun/Rentang-tahun, capability-gated; chip filter aktif di header; **Saved Filters** (simpan/terapkan/rename/hapus per-source). Lihat ledger Area Search & Filter)
- [x] `favourites`
- [x] `history` (2 bug inti FIXED & run-verified Android+Desktop 2026-06-08: tampil di History + resume halaman. Item parity lanjutan tetap deferred — lihat ledger)
- [x] `bookmarks` (page bookmarks, run-verified Android+Desktop: Doki-style reader overlay (tahan layar → app bar + tombol mengambang → bottom sheet "Opsi") dengan **bookmark fungsional**; layar Bookmarks grouped + selection multi-remove + undo; **markah tampil di bottom sheet Detail manga** (thumbnail halaman → tap buka reader di halaman persis). Fungsi sheet lain (mode baca, save page, dll) deferred ke reader-polish — lihat ledger)
- [x] `download` (run-verified Android+Desktop: engine coroutine KMP (BUKAN WorkManager) dengan **output desain `index.json` Doki** — `MangaIndex`(org.json, `compileOnly(libs.json)`) + `ZipOutput` asli; `LocalMangaZipOutput`=SINGLE_CBZ (satu `.cbz` flat + index.json), `LocalMangaDirOutput`=MULTIPLE_CBZ (per-bab `.cbz` + index.json), `canWriteTo` (cocok manga.id, kalau tidak sufiks `_1`), id bab = id remote asli. Dialog "Save manga" (4 makro + format + tujuan + folder picker Desktop), trigger Detail, layar Downloads manager card-based ala Doki. **Fitur run-verified:** unduh→muncul di Local dgn **cover asli** (`addCover`), buka & **baca offline** manga unduhan, **resume** (bab sudah-unduh otomatis ✓ tak diulang), **retry** (tombol kartu = semua bab gagal + ikon ↻ per-bab), **pause** (ikon pause, bukan spinner), **cancel** (tak ada spinner nyangkut), pembersihan temp (`page*.tmp`/`*.cbz.tmp`), folder kustom persist, lanjut-saat-gagal. Hapus manga lokal (long-press di Local). Notifikasi foreground (Android), metered-network, save-page dll deferred — lihat ledger)
- [x] `tracker` (T1 — run-verified Android+Desktop: tracker internal bab-baru + tab **Feed/Updates**; `TrackingRepository` + `CheckNewChaptersUseCase` + `FeedScreen`; kategori favorit default tracking ON + toggle lonceng di Kelola kategori)
- [~] `scrobbling` (T2 — **fondasi + 1 layanan referensi (Shikimori) + UI login, compile + DI-verified; OAuth BELUM run-verify (butuh client ID dari user)**. DONE: `ScrobblerConfig` (placeholder client ID/secret + `REDIRECT_URI=nekuva://oauth`), model umum, `ScrobblerStorage` (token di ObservableSettings), `ScrobblerRepository`+`Scrobbler` base (adaptasi KMP), **ShikimoriRepository+ShikimoriScrobbler+ScrobblerManager** (referensi penuh OAuth+API), **OAuthScreen** (browser in-app menangkap redirect `code` → authorize) + **Settings→Services** menampilkan scrobbler ter-konfigurasi dgn login/logout. CARA AKTIFKAN: isi `SHIKIMORI_CLIENT_ID/SECRET` di `ScrobblerConfig` + daftarkan app dgn redirect `nekuva://oauth`. **SISA (increment berikut, pola sama):** AniList/MAL/Kitsu + Discord RPC, selector "ikat manga ke tracker" di Detail + tampil ScrobblingInfo, auto-scrobble saat baca, penyempurnaan tangkap-redirect custom-scheme (shouldOverrideUrl/CefRequestHandler))
- [~] `sync` (T3 — server sync Kotatsu; favorit/history lintas perangkat. **Compile-verified Android+Desktop; BELUM run-verify (butuh akun + server sync untuk uji aktual)**. ARSITEKTUR: framework Android SyncAdapter/AccountManager/ContentProvider Doki di-re-arsitektur untuk KMP — `SyncSettings` (kredensial/flag di ObservableSettings), DAO Room langsung (ganti ContentProvider), `SyncManager.syncNow()` manual + sync setelah login (ganti requestSync periodik). DONE: protokol 1:1 (`POST {host}/resource/{favourites,history}` payload `SyncDto`, merge balasan → DB, soft-delete GC 4 hari), `SyncAuthApi` (`POST /auth` {email,password}→token, akun dibuat bila belum ada), `SyncInterceptor` (Bearer + X-App/Db-Version) + `SyncAuthenticator` (refresh token saat 401), DTO 1:1 + mapping entity↔DTO, `SyncHelper` (push/merge via DAO; `HistoryDao.upsertForSync` verbatim agar tombstone tak ter-resurrect; `findAllForSync` baca semua baris termasuk soft-deleted), **SyncSettingsScreen** (login email/password/host ala Doki SyncAuthActivity + toggle favorit/history + "Sync sekarang" + waktu sync terakhir), wired ke Settings→Services→"Synchronization". CARA UJI: Settings→Services→Synchronization→isi server (default `https://sync.kotatsu.app`)+email+password→Login→Sync sekarang. **SISA/DEFERRED:** auto periodic background sync (dulu SyncAdapter periodik) → area background-jobs (WorkManager actual/Desktop scheduler); change-triggered auto-sync (observe InvalidationTracker); **CAVEAT** `X-Db-Version` mengirim versi Room lokal Nekuva (=1, "Fresh V1") sedang skema kanonik Kotatsu jauh lebih tinggi — bentuk JSON tetap cocok, server self-hosted aman, tapi server resmi mungkin berperilaku beda berdasar header ini)
- [~] `settings` (pending run-verify — **SEMUA preference Doki kini ditampilkan & harus sama**, sesuai
      permintaan full-parity: Appearance/Reader/Storage&Network/Downloads/Tracker/Services/Backup/About lengkap.
      Beberapa BEHAVIOR menunggu area konsumennya (reader-advanced, tracker, sync, stats, biometric, proxy/DoH);
      nilainya tetap tersimpan & wired saat area itu jadi. Sub-screen (nav config, proxy, suggestions, login
      tracker, discord) = "Segera hadir". Lihat ledger)
- [ ] `alternatives`
- **Isu source/parser (di `nekuva-exts`, BUKAN repo UI ini — §8):**
  - **MagusManga `JSONObject["author"] not found` (Android+Desktop):** parser `MagusToon` di nekuva-exts memanggil `getString("author")` pada entry tanpa field author → harus `getStringOrNull("author")`. Fix di repo nekuva-exts, lalu naikkan tag `exts` di `libs.versions.toml`.
  - **Shinigami "Error code:" hanya di Linux Desktop (Windows+Android aman):** pola khas celah cipher TLS — JVM Linux baku sering kurang cipher yang dipakai CDN, sedang Android (Conscrypt) & JVM Windows punya. **MITIGASI (repo ini):** tambah **Conscrypt** sbg JSSE provider teratas di Desktop (`Main.kt` + `conscrypt-openjdk-uber` di desktopMain). Kandidat-fix; perlu run-verify di Linux. Bila masih gagal, kirim stack trace Linux yang sebenarnya (baris `gcm DEPRECATED_ENDPOINT`/USB di log = noise Chromium).
- [x] `browser` / `webview` / `evaluateJs` (run-verified Android+Desktop — PENUH B1+B2a+B2b+B3): **B1** evaluateJs Android via WebView (`WebViewExecutor`); **B3** evaluateJs Desktop via **KCEF** (embedded Chromium, unduh ~150MB sekali ke `~/.nekuva/kcef`); **B2a** browser in-app (`PlatformWebView` expect/actual: WebView/`AndroidView` + KCEF/`SwingPanel`; `BrowserScreen` toolbar ala Doki; "Buka di browser" dari error pencarian); **B2b** resolusi CloudFlare — cookie bridging (`createCookieJar` expect/actual: Android `AndroidCookieJar` berbagi CookieManager, Desktop `MemoryCookieJar` + `syncBrowserCookies` salin cookie CEF→OkHttp), `CloudFlareScreen` polling `cf_clearance`, error CF di RemoteList → tombol "Selesaikan captcha" → solve → **auto-retry**. Catatan: `evaluateJs` punya timeout 4s; saat KCEF masih mengunduh, eval pertama bisa gagal lalu sukses setelah siap)
- [ ] `picker`
- [ ] `widget`
- [ ] `backups`
- [ ] `stats`
- [ ] `suggestions`

---

## LEDGER Item Tertunda (Deferred Features)

Bagian ini mencatat setiap perilaku atau fitur dari aplikasi Doki lama yang sengaja ditunda dari implementasi area awal. **Phase 1 belum selesai selama ledger ini memiliki item yang belum diselesaikan atau dibatalkan secara sadar.**

### Area: Explore & List
- [ ] Full search functionality (search bar di Explore saat ini adalah placeholder, akan di-handle di area `search`).
- [ ] Source pinning, sorting, and grouping by language.
- [ ] Source active/inactive toggle (filtering).
- [ ] Fungsi klik pintasan (Bookmarks, Random, Downloads) di Explore (saat ini tombolnya nonaktif).

### Area: Search & Filter (SEDANG DIRENCANAKAN — parity-first)

Model Doki: search & filter **disatukan** dalam `MangaListFilter` (query bagian dari filter), diterapkan
per-source via `MangaRepository.getList(offset, order, filter)`; opsi filter dari
`getFilterOptions(): MangaListFilterOptions` + `sortOrders` + `filterCapabilities`. `RemoteListViewModel`
mengamati snapshot (sortOrder+filter, debounce 250ms) → re-query + paging.

**DONE & run-verified (per-source, di layar RemoteList):**
- [x] Search per-source (field toolbar + IME → set query → getList → hasil + paging).
- [x] Filter sheet tersusun ala Doki: **Urutkan (dropdown) → Genre → Kecualikan genre → Tipe →
      Status → Content rating**, dari `getFilterOptions()` + `sortOrders`.
- [x] **Apply LIVE** (tiap toggle di sheet langsung re-query, tanpa staging/Apply — persis Doki).
      Tutup sheet (Selesai / swipe) tidak me-revert (filter tetap). Tombol bawah: **Simpan** + **Selesai**.
- [x] **Label Urutkan** = pasangan bermakna Doki via key Doki (popular/unpopular, by_rating/low_rating,
      newest/order_oldest, updated/updated_long_ago, by_name/by_name_reverse, recently_added/added_long_ago,
      by_relevance, popular_in_*) — bukan "base + ↑/↓".
- [x] **Kecualikan genre** (`tagsExclude`, hanya bila `isTagsExclusionSupported`).
- [x] **Tipe** (content type) dari `availableContentTypes`.
- [x] **Quick-filter chip row** di bawah judul: chip "Genre" (buka sheet), **search-as-chip**
      (`🔍 query ✕`), dan chip genre cepat (toggle include live).
- [x] Empty ("Nothing found" + Reset), loading, error; capability-gating (sembunyikan field
      yang tak disediakan source).

**DEFERRED (masih ditunda):**
- [x] **Global Explore search** (multi-source) — DONE & run-verified. Search bar "Cari" Explore →
      `GlobalSearchRoute(query)` → section per source via `getList(MangaListFilter(query))`, paralel
      (Semaphore 4) + streaming; section **Riwayat/Disukai/Lokal** (DB/lokal, read-only) di atas; error
      per-source tanpa crash (source JS-stub → error state); tap item → detail, "Lebih" (source nyata) →
      RemoteList(source, query). Empty global = `nothing_found` + `text_search_holder_secondary`.
- [x] **Footer "Cari sumber nonaktif" (S2) — DONE & run-verified.** `continueSearch()` +
      `getDisabledSources()`: tombol footer muncul setelah search sumber-aktif selesai → menelusuri
      sumber nonaktif on-demand (Semaphore 4, streaming), lalu footer hilang. Di-unblock oleh perbaikan
      toggle enable/disable sumber (sesi S1).
- [ ] "Lebih" untuk section **Lokal** di global search (Doki punya): ditunda — `RemoteList` hanya
      mendukung `MangaParserSource`, bukan LOCAL; perlu jalur ke daftar lokal dengan query.
- [x] **Search suggestions (S1) — DONE & run-verified (Android + Desktop).**
      Panel as-you-type di bawah search bar utama
      (`SearchSuggestionViewModel` + `SearchSuggestionPanel`, debounce 300ms): chip tag (DB),
      thumbnail manga (DB, urut levenshtein), **riwayat query** (simpan/hapus-satu/hapus-semua,
      disimpan JSON di ObservableSettings — padanan KMP SearchRecentSuggestions; TIDAK dicatat saat
      incognito), sumber (judul cocok ≥3 huruf + top-sources saat query kosong, dengan **switch
      enable/disable**), penulis (DB). Tiap section dihormati `searchSuggestionTypes` (bugfix: getter
      lama selalu balik set kosong → semua tipe mati; kini absen = SEMUA aktif, default Doki).
      DEVIASI/DEFER: (a) hint judul dari tabel suggestions (`QUERIES_SUGGEST`) + top-manga saat query
      kosong → menunggu area `suggestions`; (b) tap tag membuka global-search dgn judul tag sebagai
      query (Doki membuka daftar terfilter tag lintas-source — perlu layar list-by-tag, ditunda).
- [ ] **Parity item grid (lintas-layar, sesi terpisah)**: overlay di cover — ikon hati favorit +
      badge progres baca (%/centang) — seperti Doki (Explore/Favourites/History/RemoteList).
- [x] **Field filter F1 — DONE & run-verified.** Bahasa (locale) + Bahasa asli + Penulis (author
      search) + Demografi + Tahun (slider) + Rentang tahun (range slider), semua capability-gated,
      urutan sheet = Doki. Chip filter aktif (closeable) di header; `takeQueryIfSupported`;
      single-tag source; sort dipersisten per source (`repository.defaultSortOrder`).
- [x] **SavedFilters preset (F2) — DONE & run-verified.** `SavedFiltersRepository` (JSON per-source
      di ObservableSettings via DTO `FilterSnapshot`), tombol **Simpan** aktif → dialog nama (maks 18,
      konfirmasi timpa), section "Filter tersimpan" di sheet + chip di header (klik=terapkan/lepas,
      menu ⋮=rename/hapus), deteksi preset-aktif via perbandingan snapshot.
- [x] **Full tags catalog (F2) — DONE & run-verified.** Chip genre inline dibatasi 24 + chip "Lainnya"
      → `TagsCatalogSheet`: search field live + daftar checkbox SEMUA tag (opsi source + tag cache DB,
      dedup judul, urut alfabet — `buildList` Doki), mode include & exclude.
- [x] **"Open in browser" pada error (S2) — DONE & run-verified.** Section sumber yang error
      menampilkan tombol **Buka di browser** → membuka `https://<domain>` via `LocalUriHandler`
      (Desktop = browser sistem, Android = Custom Tab/browser). Berguna untuk error CloudFlare/JS
      (evaluateJs masih stub).
- [ ] **DEFER:** "Lebih"/see-all untuk section **Lokal** (perlu layar daftar-lokal-ber-query;
      `RemoteList` hanya `MangaParserSource`) + footer "Global search" per-source.

### Area: Details
- [ ] Interactive actions untuk "Favorite this" (sekarang placeholder). Akan dikerjakan di sesi `favourites`.
- [ ] Download action. Akan dikerjakan di sesi `download`.
- [ ] Continue reading action. Memerlukan history & bookmarks.
- [ ] Updating and displaying chapter read status. Memerlukan history tracker.
- [ ] Tracking stats (MyAnimeList, dll) and related manga sections.
- [ ] Context menus: Share, overflow options, and chapter multi-select.

### Area: Reader
> STATUS RINGKAS (per sesi "Final polish reader"): hampir seluruh reader Doki sudah dimigrasi &
> compile-green Android+Desktop. Detail teknis tiap item ada di entri `[~] reader` (A#1..A#imgserver,
> bagian B/C) di atas. Checklist di bawah disinkronkan ke kondisi sebenarnya (sebelumnya banyak `[ ]` basi).

**DONE (compile-green; sebagian sudah run-verified — lihat catatan tiap entri di atas):**
- [x] Paged Mode (LTR / Standard) dengan Pager.
- [x] Reversed Mode (RTL) — via flip LayoutDirection.
- [x] Vertical Mode (VerticalPager satu-halaman; webtoon = LazyColumn kontinu).
- [x] Double-page Mode (landscape, cover solo, RTL) + **zoom per-spread** (A#3 / A#3b).
- [x] Page navigation controls — tap-grid 9-zona configurable + tombol next/prev chapter.
- [x] Top App Bar Overlay (auto-hide; tap toggle).
- [x] Bottom Actions Overlay (slider halaman + next/prev chapter, `reader_controls`).
- [x] Reader Info Bar (jam + baterai + nomor halaman) + transparansi.
- [x] Tap Grid Overlay (9-zona, configurable, + layar konfig di Settings→Reader).
- [x] Reader Settings overlay (config sheet: mode, koreksi warna, crop, 32-bit, image-server, dll).
- [x] Zoom / Pan / Pinch-to-zoom (paged + webtoon + double-page spread).
- [x] Navigasi antar-chapter (run-verified): forward-append + backward-prepend (webtoon) + tombol Next/Prev + update history + boundary aman.
- [x] Pemilihan branch/translasi (selector branch; navigasi pakai list penuh).
- [x] Page trimming memori (Doki >120 halaman) — **webtoon** (`trimLoadedPages`); paged di-skip (Pager index jump).
- [x] Keep Screen On (Android `FLAG_KEEP_SCREEN_ON`, expect/actual).
- [x] Page Save & Share (+ toast lokasi simpan).
- [x] Bookmarks per halaman (toggle + tab bookmarks di sheet).
- [x] Scroll Timer / Auto-scroll (slider kecepatan, chip TIMER).
- [x] Crop pages · 32-bit color · Image-server/mirror switch (A#crop/A#32bit/A#imgserver).
- [x] Auto-detect mode · navigasi tombol volume · fullscreen/immersive · rotate/orientation lock (Android).
- [x] Incognito (global/NSFW + **paksa saat buka dari bookmark** + toast) · auto-scrobble saat baca.
- [x] Update read progress/history setelah membaca (jalur history + scrobble).

**MASIH DEFERRED untuk Reader (BUKAN drop — butuh perangkat/dependency atau ini area lain):**
- [ ] **Double-page wide-page→solo** (deteksi halaman lebar lalu tampil solo + `reader_double_pages_sensitivity`)
      — butuh dimensi SEMUA halaman + reflow spread; pre-decode tiap halaman terlalu mahal. (refinement)
- [ ] **Double-foldable** (`reader_double_foldable`) — butuh `androidx.window` fold-state + perangkat foldable.
- [ ] **RegionBitmapDecoder / SSIV subsampling** (gambar sangat panjang) — Coil sudah auto-downsample; SSIV-spesifik, tak ada padanan langsung.
- [ ] **CloudFlare/JS evaluation (`evaluateJs`)** di Desktop & Android WebView (masih stub) — ini **area `browser`**, defer terpisah.
- [ ] **Vertical Mode "margin/gaps spesifik"** ala Doki (kosmetik kecil; webtoon gaps sudah ada).
- N/A: **`reader_optimize`** (Coil sudah kelola memory/downsample).

### Area: Main Shell
- [ ] Global Search Entry (SearchView) dengan integrasi Suggestions & Incognito.
- [ ] FAB "Resume Reading" di atas Bottom Navigation.
- [ ] Expandable NavigationRail (animasi *drawer* buka-tutup pada Desktop).
- [ ] Dynamic Tab Visibility (menyembunyikan tab Feed/Suggestions bergantung dari AppSettings).
- [ ] Badge Counter untuk tab Feed/Updates.

### Area: History (RE-OPENED — was wrongly marked done on compile only)

**Status sinkron ke kondisi NYATA (re-verifikasi 2026-06-08).**

Dua bug yang diverifikasi manusia — **SUDAH DIPERBAIKI & run-verified (2026-06-08)**:
- [x] **BUG 1 — Manga yang dibaca tidak muncul di tab History.** FIXED. Tulis history sudah andal;
      kegagalan tidak lagi ditelan diam-diam (`HistoryUpdateUseCase` log error + scope tunggal).
      Diverifikasi: DB berisi baris history valid yang ter-join ke manga; tampil di tab History.
- [x] **BUG 2 — Resume balik ke ATAS, bukan ke posisi halaman terakhir.** FIXED. `ReaderContent`
      memakai `listState.scrollToItem(savedPage)` + guard `restored` agar `page=0` sesaat saat
      layout awal tidak menimpa posisi tersimpan. Continue membuka chapter terakhir + halaman terakhir.

Temuan investigasi DB (inspeksi langsung `%TEMP%\nekuva-db.db`, bukan asumsi):
- Tabel `history` SUDAH berisi 1 baris VALID hari ini: "Eleceed" (SHINIGAMI),
  `page=54`, `percent=0.75`, `chapter_id` & FK→`manga` valid, `deleted_at=0`. Query observe
  (`LEFT JOIN manga … GROUP BY`) mengembalikan baris itu saat dijalankan manual di SQLite.
- Artinya jalur TULIS BISA bekerja (tidak selalu gagal) dan jalur OBSERVE SQL-nya benar.
- Hanya 1 manga padahal 253 manga & 3 favourites tersimpan → tulis tampak TIDAK ANDAL untuk
  sebagian manga. Hipotesis kuat: banyak sumber butuh `evaluateJs` (masih stub, §4.2) → halaman
  gagal load → `onPageChanged` tak pernah terpanggil → tak ada history. Sumber yang load (mis.
  SHINIGAMI) tetap tercatat. Maka "History kosong" yang dilaporkan manusia kemungkinan (a) stale
  (sebelum tulis berhasil), atau (b) diuji dengan sumber yang halamannya tak pernah load.

Item parity history yang DITUNDA (dari legacy `HistoryListViewModel`/menu, §6.1):
- [ ] Sort order lengkap (LAST_READ/LONG_AGO_READ/NEWEST/OLDEST/PROGRESS/UNREAD/ALPHABETIC/…).
      Saat ini hardcoded LAST_READ.
- [ ] Quick filters (HistoryListQuickFilter) + chip filter.
- [ ] Toggle grouping (KEY_HISTORY_GROUPING) + header berdasarkan progress saat sort = PROGRESS.
- [ ] List mode (grid/list/detailed) — saat ini list saja.
- [ ] Pagination (PAGE_SIZE 16, requestMoreItems) — saat ini load semua (Int.MAX_VALUE).
- [ ] Menu "Clear history" dengan opsi (2 jam terakhir / hari ini / bukan favorit / semua) —
      saat ini hanya "clear all".
- [ ] Multi-select (long-press) + Mark as read (MarkAsReadUseCase) + Share.
- [ ] Banner InfoModel "Incognito mode" saat incognito aktif.
- [ ] Empty state ikon + teks primer/sekunder (sesuai Doki) — saat ini teks polos.
- [ ] Indikator progress baca (ReadingProgressView) per item.
- [ ] Hardcoded strings di HistoryScreen ("Riwayat Baca", "Tidak ada riwayat baca", "Lanjut bab",
      "Hapus Riwayat") harus pindah ke Compose Resources (§4.4).

### Area: Bookmarks (Markah)

**DONE (run-verified Android+Desktop) — parity perilaku layar Doki:**
- [x] Data: `BookmarksRepository` (addBookmark **upsert manga dulu** → insert; removeBookmark;
      removeBookmarks+undo; observeBookmark/observeBookmarks(manga)/observeBookmarks grouped). `createdAt` = `kotlin.time.Clock`.
- [x] Reader: **overlay ala Doki** — tahan/tap layar → app bar + **tombol mengambang tengah-bawah** muncul →
      bottom sheet "Opsi" (mirror `sheet_reader_config`) dengan **toggle bookmark FUNGSIONAL** (add/remove
      halaman aktif; ikon reaktif via `observeBookmark`). Menyimpan pageId/imageUrl(preview?:url)/page/percent.
- [x] Layar Bookmarks: **grid grouped per manga** (header judul + thumbnail = **gambar halaman yang
      di-bookmark** (`imageUrl`, di-load via Coil seperti Doki) + progress%); tap thumbnail → reader di
      **chapter + page** persis (ReaderRoute `page` WAJIB/path-arg; `scrollToItem(page)` jalur resume yang
      sama dgn history); **tap header → detail manga**; **long-press → mode seleksi** (multi-remove) + **undo**.
      Catatan: `page` index DISIMPAN saat add (DB-verified) dari `listState` yang sama dgn history;
      open run-verified mendarat di page yang benar.
- [x] **Markah di bottom sheet Detail manga**: ikon bookmark di toolbar sheet bab toggle view CHAPTERS↔BOOKMARKS;
      view bookmark = grid thumbnail halaman manga ini → tap → reader di halaman yang di-bookmark.
- [x] Shortcut **"Markah"** Explore → layar Bookmarks. Empty = `no_bookmarks_yet`/`no_bookmarks_summary`.

**DEFERRED (terblokir area lain / di luar scope) → masuk sesi reader-polish:**
- [ ] **Save pages** dari mode seleksi (action_save Doki) — butuh `PageSaveHelper` / area **image-save/download**
      (belum ada). Gating: dibangun bersama area download/save.
- [x] **Incognito saat buka dari bookmark** (Doki paksa incognito + toast) — DONE (`ReaderRoute.incognito=true`
      dari Bookmarks list + sheet Detail; toast "Incognito mode"). Lihat A#incognito-bookmark.
      Sementara buka reader normal (history tetap ter-update).
- [ ] **Fungsi lain bottom-sheet reader** (UI sudah dibuat, masih non-fungsional/redup): Save page, Mode baca
      (standard/RTL/vertical/webtoon), 2 halaman landscape, pull gesture, rotate, auto-scroll, koreksi warna,
      Settings. Reader-polish.

### Area: Download (Unduhan)

**Keputusan arsitektur:** Doki memakai **WorkManager** (foreground service, broadcast pause/resume,
notifikasi sistem, constraint jaringan, tahan app-kill) — Android-only, tak bisa di Desktop. Nekuva
memakai **engine coroutine `DownloadManager`** (jvmShared, in-process, jalan di Android + Desktop).

**UPDATE 2026-06-11 — output di-port ke desain `index.json` Doki (sebelumnya "berbasis struktur tanpa
index.json", yang menimbulkan bug nyata).** Pendekatan tanpa-index lama bikin: id bab sintetis → reader
"Chapter with ID … not found"; SINGLE_CBZ malah jadi folder gambar lepasan; pilihan format diabaikan
saat folder sudah ada. Sekarang verbatim dari Doki: `MangaIndex` (org.json, `compileOnly(libs.json)` di
jvmSharedMain), `ZipOutput` asli, `LocalMangaOutput.getImpl` dgn `canWriteTo` (cocokkan `index.json`
manga.id, kalau tidak coba sufiks `_1`), nama halaman flat `FILENAME_PATTERN="%08d_%04d%04d"`, dan **id
bab = id remote asli** (disimpan di index.json) sehingga reader/history/resume konsisten. `LocalMangaZipOutput`
= SINGLE_CBZ (satu `.cbz` flat + index.json), `LocalMangaDirOutput` = MULTIPLE_CBZ (satu `.cbz` per bab di
folder `<judul>/` + index.json). Parser baca index.json (id asli), **fallback ke struktur (id sintetis)
hanya bila tak ada index.json** (download lama tetap terbaca). Plus 2 bug Windows §4.6 diperbaiki:
`URI.toFile()` strip `/` sebelum huruf drive; `MimeTypes.getMimeTypeFromExtension` terima nama file lengkap.

**DONE (pending run-verify Android+Desktop):**
- [x] Penulis output: `LocalMangaZipOutput` (SINGLE_CBZ: satu `.cbz` berisi folder-per-bab) +
      `LocalMangaDirOutput` (MULTIPLE_CBZ: folder `<judul>/` berisi folder-per-bab) + `getOrCreate/get`.
      Merge dengan arsip lama (zip) agar download bertahap tidak menimpa bab lama.
- [x] Engine `DownloadManager`: antrean 1-per-waktu (Semaphore), halaman paralel terbatas (4),
      pause/resume/cancel/removeCompleted, retry sederhana, ETA kasar, `StateFlow<List<DownloadState>>`.
      Fetch via OkHttp + `ImageProxyInterceptor` (+ header parser). `Clock` = `kotlin.time` (Desktop-safe).
- [x] Dialog "Save manga" (mirror `dialog_download.xml`): ringkasan, 4 makro (whole manga / all-branch /
      first N / next-unread N) dgn picker jumlah & cabang, "More options" → format + tujuan, switch
      "Start download", Cancel/Save. Hosted in-place di Detail (snackbar "Details" → Downloads).
- [x] Trigger Detail: **ikon download di app bar** + **popup split-button** di tombol Lanjut/Baca
      (Download fungsional; Remove-from-history fungsional; Incognito redup/deferred).
- [x] Layar Downloads manager: section Queued/In-progress/by-date, item (cover, judul, status, progress,
      %, ETA/error, jumlah bab), aksi per-item (pause/resume/cancel/remove) + menu (pause-all/resume-all/
      cancel-all/remove-completed). Shortcut "Downloads" di Explore.

**DEFERRED (UI dibuat tapi non-fungsional / N/A, masuk ledger):**
- [ ] **Notifikasi foreground Android** (progress + aksi pause/cancel) — Android `actual`, follow-up.
      Desktop = N/A (tak ada konsep). Engine jalan in-process tanpa notifikasi.
- [ ] **Tahan app-kill** (gratis dari WorkManager) — engine in-process kehilangan antrean saat app dimatikan.
- [ ] **Constraint jaringan metered + prompt "unduh via data seluler"** — butuh connectivity `actual`.
- [ ] **Layar Settings Download** (format default, folder simpan, throttle/slowdown, allow-metered) →
      area `settings`. (Dialog sudah pakai `preferredDownloadFormat` + spinner format/tujuan.)
- [ ] **Save page** (reader sheet) — fitur TERPISAH (ekspor 1 gambar via SAF, `PageSaveHelper`), bukan
      download → area image/picker. Tombol di reader tetap redup.
- [ ] **Mode seleksi multi-item** di Downloads manager (pause/cancel/remove banyak sekaligus) — defer;
      aksi per-item + menu all sudah ada.
- [ ] **MULTIPLE_CBZ presisi** (Doki: satu `.cbz` PER bab) — sekarang folder-per-bab; refinement `local`.
- [ ] **Skip bab yg sudah terunduh + dedupe by remote id** (butuh `getMangaInfo`/index.json di parser) → `local`.
- [ ] **Pilih folder kustom di Android (SAF)** — Desktop sudah bisa pilih + **persist** folder (JFileChooser via
      expect/actual `pickMangaDirectory`, disimpan ke `userSpecifiedMangaDirectories`). Android: SAF mengembalikan
      `content://` tree yang tak bisa ditulis engine berbasis `File`; sementara hanya menampilkan dir writeable
      dari `getWriteableDirs`. Butuh output berbasis DocumentFile → deferred.

**DITAMBAHKAN setelah feedback run-1 (2026-06-11):**
- [x] **Muncul di Local setelah unduh** — `localStorageChanges` (`MutableSharedFlow`) kini single Koin
      bersama; engine emit `LocalManga` saat selesai, `LocalListViewModel` observe → reload otomatis.
- [x] **Folder unduhan kustom persist** — folder yg dipilih disimpan ke `userSpecifiedMangaDirectories`;
      `DesktopLocalStorageManager`/`AndroidLocalStorageManager` memasukkannya ke daftar dir (muncul lagi di
      dropdown + manga di sana muncul di Local).
- [x] **Daftar bab expandable + ✓ + lanjut-saat-gagal** (parity Doki): item Downloads bisa di-expand,
      tiap bab tampil badge nomor + status (✓ selesai / spinner mengunduh / ⚠ gagal + pesan error). Bab yg
      gagal TIDAK menghentikan unduhan — engine lanjut ke bab berikutnya & catat errornya (overall COMPLETED
      selama ada ≥1 bab sukses, else FAILED).
- [x] **Buka & baca manga lokal/unduhan offline** (reader R3, sebelumnya deferred): (a) manga lokal kini
      DISIMPAN ke DB saat tab Local dimuat (`LocalListViewModel` → `storeManga`) sehingga `findMangaById`
      menemukannya; (b) `DetailsViewModel` cabang khusus `isLocal` → baca bab via `LocalMangaRepository.getDetails`;
      (c) **`LocalImageFetcher`** (Coil) memuat gambar `zip:` (entry .cbz) & `file:` (folder) untuk cover + halaman.
      Reader sudah pakai `LocalMangaRepository.getPages`. Manga unduhan kini bisa dibuka & dibaca offline.
- [x] **Jeda/Lanjut** (per-card + app bar) FIXED: callback progres halaman in-flight tak lagi menimpa status
      `PAUSED` jadi `RUNNING`, jadi tombol berubah ke "Lanjut" dan resume berfungsi.

**DEFERRED → area `local`/`details` (BELUM, dari feedback run-2 2026-06-11):**
- [ ] **Buka/ tampilkan manga lokal hasil unduh** — manga unduhan SUDAH muncul di tab Penyimpanan lokal,
      TAPI (a) **cover gagal load** (tampil "Kesalahan") dan (b) **tap detail gagal**: "Manga with ID … not
      found in local cache". Sebab: `DetailsViewModel.loadDetails` cari via `mangaDataRepository.findMangaById`
      (DB Room), padahal manga lokal berbasis FILE (id = hash path, tak ada di DB). Perlu jalur khusus
      `LocalMangaSource` di Details (baca via `LocalMangaRepository`/`LocalMangaParser`) + render cover dari
      file. Ini pekerjaan area **local/details**, BUKAN bug engine download. Tanpa ini, manga terunduh belum
      bisa dibaca offline. **Prioritas berikutnya setelah settings.**

### Area: Settings (Pengaturan) — bertahap

`AppSettings` sudah punya **174 key** (semua preference Doki). Yang dibangun = UI Compose + logika aksi.
Akses: **top bar bersama di SETIAP tab** (`MainTopBar`: kotak pencarian "Cari manga" + menu titik-tiga)
→ item **"Pengaturan"** → `SettingsRootScreen` (9 kategori). Pencarian → global search (sudah jalan).

---

## RENCANA FULL MIGRASI SETTINGS — 9 FASE (per kategori, urut root Doki)

> Tujuan sesi: migrasikan **SEMUA** fitur settings Doki, **termasuk efek/impact-nya ke screen lain**
> (bukan sekadar toggle yang tersimpan). Tiru UI & perilaku Doki. Dikerjakan bertahap, commit per
> sub-fase, run-verify tiap selesai. Keputusan terkunci: **color-scheme = palet STATIS di semua platform**
> (MONET tidak benar-benar dinamis; jadi palet statis juga).

| Fase | Kategori | Status ringkas |
|---|---|---|
| **1** | Appearance (Tampilan) | 🟡 sebagian; color-scheme tak diterapkan + bug widget list — **SEDANG DIKERJAKAN** |
| **2** | Remote sources (+ Catalog) | 🔴 belum (top-level "Segera hadir") |
| **3** | Reader | 🟢 ~lengkap (audit sisa kecil) |
| **4** | Storage & network | 🟡 sebagian (proxy/DoH/ssl/adblock/meter/cache pending) |
| **5** | Downloads | 🟡 sebagian (dir/format/metered; battery-opt + page-dir pending) |
| **6** | Tracker (cek bab baru) | 🟡 sebagian (categories + notifications pending) |
| **7** | Services | 🟡 sebagian (suggestions/discord/anilist/mal/kitsu/stats pending) |
| **8** | Backup & restore | 🟡 sebagian (periodic + Telegram pending) |
| **9** | About | 🟢 ~lengkap |

### FASE 1 — Appearance (Tampilan) — parity checklist + impact

Sumber Doki: `pref_appearance.xml`, `AppearanceSettingsFragment`, `ColorScheme.kt` (11 tema =
ThemeOverlay + `colors_themed.xml` 423 warna light + 423 dark), `ThemeChooserPreference`, `nav/NavConfigFragment`,
`protect/ProtectSetupActivity`.

**Sub-fase 1A — Color scheme + perbaikan widget (PRIORITAS, ini yang rusak) — ✅ DONE & RUN-VERIFIED:**
- [x] Port **9 palet statis** Doki (Totoro=DEFAULT, Miku, Asuka=RENA, Mion=FROG, Rikka=BLUEBERRY, Sakura, Mamimi,
      Kanade, Itsuka) sebagai `ColorScheme` Compose light+dark di `commonMain/.../theme/ColorSchemes.kt`
      (auto-generate dari `colors_themed.xml` + `values-night` via `scripts/gen_color_schemes.py`). MONET/EXPRESSIVE
      = dynamic-only → dikecualikan dari daftar (fallback Totoro) sesuai keputusan palet-statis.
- [x] `Theme.kt`: `appColorScheme(name, dark)` pilih palet dari `settings.colorScheme` × theme × AMOLED
      (`toAmoled()` override hitam). **Live** via `observeColorScheme()`. (Color.kt palet lama dihapus.)
- [x] Label tema **terlokalisasi** (`theme_name_*`) di Appearance (bukan nama enum mentah).
- [x] **FIX bug widget list-option** (`SettingsSingleChoice` + multi-dialog): opsi kini **scrollable + height-capped**
      (sebelumnya daftar panjang spt 9 tema meluber & opsi bawah tak terjangkau).
- IMPACT: seluruh app (NekuvaTheme di root). **Sisa 1A (opsional):** swatch warna ala Doki `item_color_scheme`.

#### Perbandingan PER-SETTING (nama setting · Doki · Nekuva sekarang · status)
> Audit fungsional 2026-06-14 (cek konsumen di kode, bukan cuma "toggle tersimpan"). **Temuan: hampir
> semua setting Appearance TERSIMPAN tapi BELUM DITERAPKAN** — hanya tema warna/terang-gelap/AMOLED +
> saran-pencarian yang benar-benar berfungsi. Status: ✅ sesuai Doki · 🟡 setengah (tersimpan, tak
> diterapkan) · 🔴 belum ada.

**Tema:**
| Setting (key) | Doki | Nekuva sekarang | Status |
|---|---|---|---|
| Tema warna (`color_theme`) | 11 palet (Monet dinamis) | 9 palet statis, live | ✅ (1A; tanpa Monet dinamis — sesuai keputusan) |
| Tema (`theme`) | sistem/terang/gelap, live | sama | ✅ |
| Hitam pekat/AMOLED (`amoled_theme`) | switch live | sama | ✅ |
| **Bahasa (`app_locale`)** | semua bahasa terjemahan; ganti runtime (`AppCompatDelegate.setApplicationLocales`) | **hanya 3 opsi (sistem/en/id); tersimpan tapi TAK diterapkan** | 🟡 **(kamu keluhkan)** |

**Daftar manga:**
| Setting (key) | Doki | Nekuva sekarang | Status |
|---|---|---|---|
| Mode tampilan (`list_mode_2`) | LIST/DETAILED/GRID dipakai semua daftar (+override per-layar) | enum + per-layar tersimpan, TAPI semua layar **hardcode grid** (Explore Adaptive 100dp, Favourites 120dp) | 🟡 |
| Ukuran grid (`grid_size`) | persen ukuran sel | tersimpan, grid pakai Adaptive tetap | 🟡 |
| Saringan cepat (`quick_filter`) | chip filter cepat di header daftar | `isQuickFilterEnabled` ada, tanpa konsumen | 🟡 |
| Indikator progres (`progress_indicators`) | badge progres di item (off/percent/chapters…) | `progressIndicatorMode` ada, item tak menampilkan | 🟡 |
| Badge daftar (`manga_list_badges`) | badge favorit/tersimpan di cover | tersimpan, tanpa konsumen | 🟡 |

**Detail:**
| Setting (key) | Doki | Nekuva sekarang | Status |
|---|---|---|---|
| Ciutkan deskripsi (`description_collapse`) | sinopsis panjang diciutkan | tersimpan, Details tak baca | 🟡 |
| Thumbnail halaman (`pages_tab`) | section/tab halaman di Details | tersimpan, Details tak baca | 🟡 |
| Tab default (`details_tab`) | tab awal Details (terakhir/bab/halaman/markah) | tersimpan, Details tak baca | 🟡 |

**Layar utama:**
| Setting (key) | Doki | Nekuva sekarang | Status |
|---|---|---|---|
| Saran pencarian (`search_suggest_types`) | tipe saran yg tampil | `SearchSuggestionViewModel` baca `searchSuggestionTypes` | ✅ |
| Bagian layar utama (`nav_main`) | reorder/aktif tab (drag, `NavConfigFragment`) | "Segera hadir" | 🔴 |
| FAB lanjut baca (`main_fab`) | tombol mengambang "Lanjut baca" | `isMainFabEnabled` ada, tak ada FAB | 🟡 |
| Label navbar (`nav_labels`) | tampil/sembunyi label tab | tersimpan, tanpa konsumen | 🟡 |
| Sematkan nav (`nav_pinned`) | rail nav tersemat (tablet/desktop) | tersimpan, tanpa konsumen | 🟡 |
| Konfirmasi keluar (`exit_confirm`) | dialog saat back keluar | tersimpan, tanpa konsumen | 🟡 |
| Pintasan riwayat (`dynamic_shortcuts`) | app-shortcuts dinamis (Android) | tersimpan, tanpa konsumen | 🟡 (Android) |

**Privasi:**
| Setting (key) | Doki | Nekuva sekarang | Status |
|---|---|---|---|
| Kunci aplikasi (`protect_app`) | app-lock PIN/biometrik (`ProtectSetupActivity`) | ✅ setup + lock gate + biometrik (1F) | ✅ |
| Kebijakan tangkapan layar (`screenshots_policy`) | `FLAG_SECURE` per kondisi | tersimpan, tak diterapkan | 🟡 (Android) |

#### Sub-fase Fase 1 (revisi — fokus "menerapkan" yang setengah jalan)
- **1A ✅ DONE** — Tema warna (9 palet) + perbaikan widget list-option.
- **1B ✅ DONE & RUN-VERIFIED** — Bahasa (`app_locale`): katalog **±66 bahasa** dari folder
  `values-*` (`core/i18n/Languages.kt`), nama bahasa self-localized (`localeDisplayName` via `java.util.Locale`),
  diurutkan; opsi "Ikuti sistem" di atas. **Diterapkan runtime** via `applyAppLocale`/`recreateForLocale`
  expect/actual: **Android** = `MainActivity.attachBaseContext` membungkus Configuration dgn locale (baca
  `nekuva_prefs/app_locale`, semua API, tanpa AppCompat) + `Activity.recreate()` saat ganti; **Desktop** =
  `Locale.setDefault` + `App()` re-`key(localeTag)` agar Compose Resources resolve ulang. Impact: seluruh teks UI.
- **1C ✅ DONE & RUN-VERIFIED — Daftar manga**: komponen item bersama `core/ui/components/MangaListItems.kt` ala Doki
  (`MangaGridItem` grid + `MangaListRow` LIST/DETAILED + `MangaListContent` + `mangaGridCells`), badge
  favorit/saved (`MangaBadges`) + bar progres opsional.
  - [x] `list_mode_2` (GRID/LIST/DETAILED) + `grid_size` → **Local** (global), **Favourites** (per-layar
    `list_mode_favorites`), **RemoteList** (global, grid+list, load-more di kedua mode), **Search** (carousel
    horizontal ala Doki — list-mode N/A, pakai item bersama). Live via `observeListModeOrNull`/`observeGridSize`.
  - [x] `manga_list_badges` → badge **saved** di Local, **favourite** di Favourites (`getMangaListBadges()` bitmask).
  - [x] `quick_filter` → RemoteList: baris chip quick-filter digate `isQuickFilterEnabled` (filter tetap di toolbar).
  - [x] **1C bagian 2:** `historyListMode` → History kini hormati mode (GRID = grid + header tanggal +
    bar progres + long-press menu Resume/Hapus; LIST/DETAILED = baris lama dgn aksi resume/hapus) + grid_size;
    teks/bar progres digate `progress_indicators` (NONE = sembunyi).
  - [x] **`progress_indicators` lintas-daftar + badge favorit:** provider bersama `list/ui/MangaListDecorations`
    (observe `HistoryRepository.observeProgressMap` + `FavouritesRepository.observeFavouriteIds` via DAO baru
    `observeProgress`/`observeFavouriteIds`) → bar progres + badge hati di **Local/Favourites/RemoteList/Search**
    (gated setting). Favourites pakai badge favorit konstan (semua item favorit).
  - [x] **Override list-mode per-layar (UI "Opsi daftar" ala Doki `ListConfigSheet`/`MangaListMenuProvider`):**
    overflow **"Opsi daftar"** di tab History/Favourites/Local kini aktif → `ListConfigSheet` (segmented
    Grid/List/Detailed + slider ukuran grid, live) tulis ke key per-section (`KEY_LIST_MODE_HISTORY`/
    `_FAVORITES`/global). `AppSettings.getListMode/setListMode(key)`.
  - [x] **Badge "saved" lintas-daftar (DONE):** `LocalMangaRepository.observeSavedIds()` (StateFlow, scan
    sekali + refresh saat storage berubah) jadi sumber id manga terunduh; `MangaListDecorations` kini tambah
    badge **saved** → muncul di RemoteList/Search/Local (gated `manga_list_badges` bit 2). (Counter "bab baru"
    BadgeView Doki = area tracker, terpisah.)
- **1D ✅ DONE (compile-green, belum run-verify) — Details**:
  - [x] `description_collapse` → sinopsis di DetailsScreen kini hormati setting: bila "ciutkan" OFF
    (`isDescriptionExpanded`), deskripsi tampil penuh default (tanpa tombol "Selengkapnya"); bila ON, terpotong 3 baris + "Selengkapnya".
  - [x] `details_tab` → sheet Detail (`ChaptersSheetContent`) buka di section default: `defaultDetailsTab==2` → Bookmarks,
    selain itu Chapters (index Pages jatuh ke Chapters karena sheet Nekuva tak punya view Pages terpisah).
  - [x] `pages_tab` (tab "Pages"/thumbnail halaman) **DONE** (impact ke Details + Reader): sheet Detail kini
    punya section **Pages** (ikon di toggle, hanya bila `isPagesTabEnabled`) → grid thumbnail halaman bab
    sekarang/terakhir-dibaca (`DetailsViewModel.loadPagesPreview` muat via local/parser, lazy), tap → buka
    Reader di halaman itu (`onPageClick` → `ReaderRoute(page)`). `details_tab==1` kini buka section Pages.
    Doki ref: `details/ui/pager/pages/PagesFragment`.
- **1E ✅ DONE & RUN-VERIFIED — Main shell**:
  - [x] `nav_main` → `NavItem` diselaraskan ke 5 tab nyata (History/Favorites/Explore/Feed/Local); bottom-nav
    + rail kini dibangun dari `settings.mainNavItems` (live via `observeNavItems`). **Editor** `NavConfigScreen`
    (Appearance→"Bagian layar utama"): reorder (panah atas/bawah) + tambah/hapus section, min 1 (ala Doki `NavConfigFragment`).
  - [x] `nav_labels` → `alwaysShowLabel`/label null di NavigationBar/Rail item (Doki LABELED/UNLABELED).
  - [x] `main_fab` → FAB "Lanjut baca" (ExtendedFAB, ikon play) di shell saat ada history; tap → resume manga
    terakhir (`observeLast` + `getOne` → ReaderRoute page -1). Muncul di kedua layout (Scaffold FAB + rail Box).
  - [x] `exit_confirm` → `PlatformBackHandler` expect/actual (Android `androidx.activity.compose.BackHandler`,
    Desktop no-op) saat di root (`previousBackStackEntry==null`): tekan back → snackbar "Press Back again";
    back ke-2 dlm 2s → `exitApp()` (Android finish / Desktop exitProcess).
  - [x] `nav_pinned` → di shell Compose bottom-bar/rail **selalu tampil** (tak auto-hide) = perilaku "pinned"
    Doki sudah default; tak perlu kode tambahan (dicatat).
  - [x] `dynamic_shortcuts` **DONE** → launcher shortcuts Android (`ShortcutManagerCompat`): `updateDynamicShortcuts`
    expect/actual (Android set/clear, Desktop no-op); App() observe 4 history terbaru → set shortcut (kalau setting ON,
    else clear). Tap shortcut → intent ke MainActivity (`EXTRA_MANGA_ID`) → `DeepLinkBus` → AppNavigation buka
    `MangaDetailsRoute`. Ikon = launcher app. **FASE 1E SELESAI.**
- **1F ✅ DONE & RUN-VERIFIED — Privasi:**
  - [x] `screenshots_policy` → `SecureScreenEffect` expect/actual (Android `FLAG_SECURE` ref-counted; Desktop N/A).
    Diterapkan: App-level (BLOCK_ALL; BLOCK_INCOGNITO saat incognito global), Reader (BLOCK_NSFW manga NSFW;
    BLOCK_INCOGNITO sesi incognito). Doki ref: `ScreenshotPolicyHelper`.
  - [x] `protect_app` (app-lock) **DONE — tidak ditunda ke Fase 7** (impact penuh): `AppLockController` (state
    locked) + `ProtectSetupScreen` (enter→ulangi→confirm, simpan `appPassword=md5`, `isAppPasswordNumeric`,
    toggle biometrik) + `LockScreen` overlay (password + biometrik) di App() saat terkunci; toggle "Kunci aplikasi"
    di Tampilan (ON→setup, OFF→hapus password, live via `observeAppPasswordSet`); re-lock saat background
    (`MainActivity.onStop`, skip saat config-change). **Biometrik** = framework `BiometricPrompt` API 28+
    (expect/actual, TANPA dep androidx.biometric / FragmentActivity; Desktop password-only). Doki ref:
    `ProtectSetupActivity` + `ProtectActivity`. md5/isNumeric helper `core/util/Hashing.kt`.

### FASE 2 — Remote sources (+ Catalog) — parity checklist + impact
> Data layer SUDAH lengkap (`MangaSourcesRepository`: enabled/pinned/order/NSFW/`queryParserSources`/
> `allMangaSources`/`setSourcesEnabled`/`setPositions`/`setPinned` + DAO). Fase 2 = UI + impact. Ref Doki:
> `settings/sources/*` (SourcesSettings/Manage/Catalog/SourceSettings) + `explore/`.
- **2A ✅ DONE (compile-green) — Sources settings screen** (`SourcesSettingsScreen`): sort order, grid, enable-all,
  no_nsfw, incognito_nsfw (TriState), tags_warnings, mirror_switching, handle_links + entri Manage & Catalog.
  Root "Remote sources" diaktifkan (`SourcesSettingsRoute`).
- **2B ✅ DONE (compile-green) — Manage sources** (`SourcesManageScreen`/VM): **pin/unpin** (`setSourcesPinned`
  baru), **reorder** (up/down → `setPositions`, auto-MANUAL), disable (Switch off → balik ke Catalog).
- **2C ✅ DONE (compile-green) — Sources Catalog** (`SourcesCatalogScreen`/VM): jelajah disabled-only via
  `queryParserSources`, **cari**, **filter** bahasa (dropdown locale) + content-type (chips) + new-only,
  **tambah** (+ → enable, refresh). Empty-state.
- **2D ✅ DONE (compile-green) — Impact Explore:** overflow ⋮ Explore aktif → **Manage** + **Catalog**;
  **pin/unpin dari Explore** (long-press → `togglePin`, indikator pin); sumber off hilang dari Explore.
- **2E ✅ DONE (compile-green) — Per-source settings** (`SourceSettingsScreen`/VM): **domain/mirror** picker
  (ConfigKey.Domain via `getAvailableMirrors`/`domain`+`invalidateCache`), **Sign in** (bila
  `MangaParserAuthProvider` ada → buka `authUrl` di in-app Browser + tampil username), **Clear cookies**
  (konfirmasi → `MutableCookieJar.clear`). Dibuka dari Manage (tap nama sumber → `SourceSettingsRoute(name)`).
- **2F ✅ DONE — Manage app-bar (catatan user):** **search** + aksi **catalog** (+) + overflow ⋮
  **Disable NSFW** (checkable) + **Disable all** (`disableAll`→`disableAllSources`). Ref Doki `opt_sources.xml`.
- **2G ✅ DONE — Multi-select bulk di Manage** (Doki `mode_source`): **long-press** sumber → masuk mode pilih
  (highlight `secondaryContainer`), contextual app-bar (jumlah + Close + **Pin**/**Unpin**/**Disable** terpilih
  via `setPinnedBulk`/`setEnabledBulk`), tap di mode pilih → toggle. `combinedClickable` (onClick/onLongClick).
- **2H ✅ DONE — Favicon (gambar 1) + placeholder (gambar 2):** baris Doki-style (ikon 40dp + nama + tipe + bahasa);
  favicon **di-fetch SEKALI** lalu di-cache file (`FaviconCache` app-scope + `FaviconFetcher` Coil + disk-cache),
  dipakai di Manage/Catalog/**Explore**; placeholder = kotak membulat + huruf awal berwarna (`SourceIconPlaceholder`).
  Bug fetch (ForgottenCoroutineScope) & bug Coil3 `painter.state` (StateFlow → selalu placeholder) sudah **FIXED & run-verified**.
- **Sign-in sumber — SUDAH berfungsi (bukan sisa Fase 2):** login berbasis **cookie** jalan di Android karena
  `AndroidCookieJar` berbagi `CookieManager` sistem dgn WebView (in-app Browser → cookie tersimpan → sumber login).
  Yang BELUM = auto-capture **token/localStorage** (butuh `evaluateJs`) → dipindahkan ke **area browser/WebView**
  (lihat "DEFERRED — evaluateJs/WebView"), bukan gap Sources.
- **FASE 2 SELESAI** (compile-green Android+Desktop; 2H run-verified; sisanya belum run-verify).

### FASE 3 — Reader settings (`pref_reader.xml`) — parity checklist + impact
> Ref Doki: `app/src/main/res/xml/pref_reader.xml` + `settings/ReaderSettingsFragment.kt`. Layar
> `ReaderSettingsScreen` SUDAH memuat **ke-27 preference** Doki (dibangun di sesi reader sebelumnya).
> Fase 3 = paritas **UI screen** (dependency/divider/summary spt Doki) + verifikasi **impact** tiap setting
> ke reader. Reader Nekuva sudah cukup lengkap (mode, zoom_mode, controls, crop, enhanced colors,
> fullscreen, orientation, keep-screen-on, volume keys, nav-inverted, autoscroll, pull-gesture, tap-grid,
> background, page-numbers, info-bar, chapter-toast).
- **3A ✅ DONE — Paritas UI screen (Doki ReaderSettingsFragment):**
  - **Dependency disable** spt Doki: `reader_mode_detect` non-aktif saat default mode = **WEBTOON**;
    `webtoon_zoom_out` (slider) tergantung `webtoon_zoom`; `reader_bar_transparent` tergantung `reader_bar`.
    (state controlling di-hoist ke screen; `BoolPref`/`IndexListPref`/`SettingsSlider` dapat param `enabled`.)
  - **Divider antar grup** (Doki `allowDividerAbove`): sebelum zoom_mode, reader_controls, enhanced_colors,
    reader_fullscreen, reader_bar, reader_background.
  - **Multi-select empty summary** (Doki MultiSummaryProvider): `reader_controls`→"None", `reader_crop`→"Disabled".
  - `reader_actions` kini tampil summary (Doki `reader_actions_summary`).
- **3B ✅ DONE — Impact baru di-wire ke reader (tidak ditunda):**
  - **`reader_bar_transparent`** → `ReaderInfoBar(transparent=…)`: bar transparan (overlay) vs solid (surface).
  - **`webtoon_zoom`** → gating pinch-zoom strip webtoon (off = fixed 1×, tak bisa pinch/double-tap).
  - **`webtoon_zoom_out`** (0–50%) → strip webtoon rest/min di `1 − persen` (zoom-out default), pinch s/d 3×.
  - (`reader_chapter_toast` SUDAH ter-wire sebelumnya: `ReaderViewModel` skip toast bab bila non-aktif.)
- **3C ✅ DONE — Sisa impact "deferred" dituntaskan (cek kode Doki dulu):**
  - **`reader_zoom_buttons`** (Doki `ZoomControl` / `BasePageHolder.onZoomIn/Out` = scaleBy 1.2×/0.8×): tombol
    +/− mengambang **bottom-end** (`ZoomButtons`, `FilledTonalIconButton`), tampil saat kontrol terlihat,
    `SharedFlow<Float>` ke halaman AKTIF saja (paged/double) atau ke strip (webtoon, clamp minScale..maxScale).
  - **`reader_optimize`** (Doki `applyDownSampling`: foreground full, off-screen ÷4): halaman NON-foreground
    decode di `size(720)` lewat `ReaderImageOptions.optimize` + `rememberReaderPageModel(foreground=…)`; halaman
    aktif tetap full-res (reload saat jadi foreground — trade-off sama dgn Doki).
  - **`pages_preload`** (Doki `PageLoader.isPrefetchApplicable` + `NetworkPolicy`): `ReaderPagePreloader`
    enqueue `PRELOAD_AHEAD=3` halaman berikut ke cache Coil (request identik via `buildReaderPageRequest`),
    di-gate policy **always / wifi(non-metered) / never** pakai `NetworkState.isMetered()` (didaftarkan di
    `platformModule`). **Default diperbaiki** ke index 1 (Wi-Fi) sesuai Doki (`NetworkPolicy` default `2`=NON_METERED),
    fix `NetworkPolicy.isNetworkAllowed(isMetered)` (NON_METERED → `!isMetered`).
- **3D ✅ DONE — `reader_multitask` (restrukturisasi, atas keputusan user):** reader bisa dibuka di
  **task/window terpisah** (Doki `AppRouter.openReader` + `FLAG_ACTIVITY_NEW_DOCUMENT`).
  - **Desain anti-regресi:** destinasi reader in-nav lama **tidak diubah** (multitask OFF = perilaku lama persis).
    Semua titik buka-reader (History resume, Bookmarks, Details chapter/bookmark/page, FAB Resume) lewat
    helper tunggal **`rememberOpenReader`** → multitask ON: buka task/window terpisah; else: nav in-app.
  - **`ReaderWindowHost`** (jvmShared): host mandiri = `InstallNekuvaImageLoader` + `NekuvaTheme` + **mini NavHost**
    (Reader + Reader settings + TapGrid) sehingga `ReaderViewModel.SavedStateHandle.toRoute<ReaderRoute>` tetap jalan.
  - **Android:** `ReaderActivity` (`documentLaunchMode=always`, `autoRemoveFromRecents=false`, exported=false) di
    manifest; launcher kirim Intent + `FLAG_ACTIVITY_NEW_DOCUMENT|MULTIPLE_TASK`; volume-key + locale spt MainActivity.
  - **Desktop:** `DesktopReaderWindows` (state list) → `Main.kt` render satu `Window` per entri, bisa ditutup sendiri.
  - `ReaderWindowLauncher` = `expect`(jvmShared) + actual Android/Desktop. `InstallNekuvaImageLoader` di-extract dari `App()`.
- **FASE 3 SELESAI** (3A/3B/3C/3D compile-green Android+Desktop; Desktop smoke-run OK; **belum run-verify GUI** —
  minta user uji: Reader settings (dependency/divider/summary), tombol zoom, info-bar transparan, webtoon zoom-out,
  preload, dan **reader_multitask** (Android: aktifkan "Buka reader di jendela terpisah" → buka 2 manga → 2 entri
  Recents; Desktop: 2 window reader)).
- **BUGFIX (lapor user) — tap/long-press mati setelah ganti mode webtoon→paged:** ganti mode hanya bisa dari
  config sheet (`ModalBottomSheet`); `onSelectMode` dulu TIDAK menutup sheet → reader paged baru tersusun di
  bawah scrim modal → intermiten kehilangan tap (app bar/tombol chapter tak toggle) + long-press, sampai
  restart. **Fix:** `onSelectMode` kini menutup sheet (`showConfigSheet = false`) sehingga reader pindah mode
  tanpa modal di atasnya. (Perlu run-verify user.)

### FASE 4–9 — ringkas (detail dirinci saat fase-nya tiba)
### FASE 4 — Storage & Network (`pref_network_storage.xml` + `pref_proxy.xml`) — checklist + impact
> Ref Doki: `settings/StorageAndNetworkSettingsFragment` + `ProxySettingsFragment` + `core/network/NetworkModule`
> (OkHttp builder), `DoHManager`, `SSLUtils`, `ProxyProvider`, `userdata/storage/*` (storage meter + cleanup).
> **Temuan awal:** Nekuva sudah punya `DoHManager`/`SSLUtils` (androidMain, TAK terpakai) + accessor proxy/doh/ssl,
> tapi OkHttp (`networkModule`) TIDAK memakai proxy/DoH/ssl, dan `dnsOverHttps` di-hardcode `NONE` → semua
> setting itu "tersimpan tapi mati". Fase 4 = hidupkan impact-nya + lengkapi UI (proxy subscreen, data-cleanup, meter).
- **4A ✅ DONE — Impact network di-wire ke OkHttp (inti "jangan cuma setting"):**
  - `dnsOverHttps` kini baca `KEY_DOH` (index → `DoHProvider`); `DoHManager` dipindah ke **jvmShared** (cache opsional)
    dan dipasang `.dns(DoHManager(settings))` → DoH live (None/Google/CloudFlare/AdGuard/0ms), fallback system DNS.
  - **`ProxyProvider`** baru (jvmShared, JVM `ProxySelector`+`Authenticator`, baca settings live + set default JVM) →
    `.proxySelector`/`.proxyAuthenticator` di OkHttp; config tak lengkap → degrade ke DIRECT (tak bikin semua request gagal).
  - **`ssl_bypass`** → `.disableCertificateVerification()` (dipindah ke jvmShared, pure JVM) saat ON (build-time → efektif
    setelah restart, sama spt Doki). `installExtraCertificates` tetap Android (`ExtraCertificates.kt`).
- **4B ✅ DONE — Proxy subscreen** (`ProxySettingsScreen`, route `ProxySettingsRoute`, dibuka dari Storage&Network,
  stub "coming soon" diganti): type (Disabled/HTTP/SOCKS), address, port (number), auth (username/password mask) —
  address/port/auth redup saat Disabled (Doki dependency); **Test connection** = GET `neverssl.com` lewat OkHttp →
  dialog "Connection is OK"/pesan error. Komponen baru **`SettingsEditText`** (≈ Doki EditTextPreference).
- **4C ✅ DONE — Data removal subscreen + storage-usage meter** (Doki `DataCleanupSettingsFragment` +
  `StorageUsagePreference`):
  - **HTTP cache** ditambahkan (Doki `createHttpCache`): `LocalStorageManager.createHttpCache()` (android `cacheDir/http_cache`,
    desktop `~/.nekuva/http_cache`, 64 MB) → dipasang `.cache(cache)` di OkHttp + diumpankan ke `DoHManager` bootstrap.
  - `LocalStorageManager` dapat `computeCacheSize(CacheDir)`/`clearCache(CacheDir)` (impl android+desktop, `CacheUtils`).
  - **`DataCleanupScreen`** (route `DataCleanupRoute`, dibuka dari Storage&Network) + **`DataCleanupViewModel`**: hapus
    **riwayat pencarian / umpan diperbarui / thumbnail (Coil disk+memory) / pages cache / network(http) cache / source-icons
    (favicon) / database (cleanupLocalManga+cleanupDatabase) / cookies** — ukuran cache live, dihitung ulang tiap clear.
  - **Storage-usage meter (bar segmen Doki StorageUsagePreference)** — REVISI (feedback user gbr 1): bar berwarna
    bersegmen + legenda **Saved manga (biru) / Pages cache (merah) / Other cache (hijau) / Available (track)**.
    `LocalStorageManager` dapat `computeCacheSize()` (total), `computeStorageSize()` (manga tersimpan), `computeAvailableSize()`
    (free space); breakdown dihitung di `StorageNetworkViewModel` (otherCache = total − pages), digambar `StorageUsageBar`.
  - **De-deferred — masuk Data removal (feedback user gbr 2, sesuai Doki):**
    - **Live count** riwayat pencarian + umpan diperbarui ("N items") via `getSearchHistoryCount`/`getLogsCount`.
    - **Clear browser data** → `expect/actual clearBrowserData()`: Android `WebStorage.deleteAllData` + `CookieManager.removeAllCookies`;
      Desktop KCEF `CefCookieManager.getGlobalManager().deleteCookies` (best-effort).
    - **Delete read chapters** + **auto on-start** → `DeleteReadChaptersUseCase` di-port (jvmShared, pakai
      `LocalMangaRepository`/`HistoryRepository`/`MangaRepository.Factory`); switch `chapters_clear_auto` + trigger di `App()`.
- **4D ✅ DONE — adblock (WebView, atas keputusan user "kerjakan penuh"):** Doki `core/network/webview/adblock/*`.
  - **Engine EasyList di-port** ke jvmShared: `Rule` (Domain/ExactUrl/Path/WithModifiers) + `RulesList` (parser block/allow
    `@@`, modifiers script/third-party) + `AdBlock` (`shouldLoadUrl(url, baseUrl)` lazy-parse + `suspend updateList()`
    unduh EasyList `If-Modified-Since`). CSSRuleBuilder dilewati (cosmetic, tak dipakai jalur match Doki).
  - **File list:** `LocalStorageManager.adblockListFile()` (android `cacheDir/adblock/easylist.txt`, desktop `~/.nekuva/adblock/…`).
    Refresh saat app start bila adblock ON (App() LaunchedEffect, Doki `AdListUpdateService`).
  - **Intercept request di KEDUA webview:** Android `WebViewClient.shouldInterceptRequest` → blok = `WebResourceResponse`
    kosong; Desktop **KCEF** `CefRequestHandler.getResourceRequestHandler` → `onBeforeResourceLoad` return true (cancel).
    Base URL pakai `state.currentUrl` (hindari akses WebView.getUrl off-thread). `AdBlock` single di Koin.
  - Toggle `adblock` di Storage&Network kini benar-benar memblokir iklan di in-app browser. (Run-verify GUI: buka browser,
    aktifkan adblock, banding halaman beriklan.)
- **Sudah ada sebelumnya:** `images_proxy` (RealImageProxyInterceptor), `no_offline` (NetworkState.isOfflineCheckDisabled),
  `prefetch_content`/`pages_preload` (default Wi-Fi disamakan di Fase 3).
- **FASE 4 SELESAI** (4A/4B/4C/4D compile + assembleDebug hijau; belum run-verify GUI). Deferred tercatat:
  delete-read-chapters (download), webview-data clear (browser), live count riwayat/umpan.
  (Catatan: delete-read-chapters + webview clear + live count akhirnya DIKERJAKAN saat revisi Fase 4C; lihat di atas.)

### FASE 5 — Downloads (`pref_downloads.xml`) — checklist + impact
> Ref Doki: `settings/DownloadsSettingsFragment` + `pref_downloads.xml`. Nekuva sudah punya kelola direktori
> (radio set-default + add custom via picker Desktop) + format unduhan dari Fase 1. Fase 5 = lengkapi + impact.
- **5A ✅ DONE — Metered network (fix bug + impact):** UI dulu `IndexListPref` (simpan index "0/1/2") tapi
  `allowDownloadOnMeteredNetwork` baca `getEnum` (NAMA enum) → selalu jatuh ke ASK. Diganti `SettingsSingleChoice<TriStateOption>`
  (Allow always→ENABLED / Ask→ASK / Don't allow→DISABLED) supaya tersimpan benar. **Impact:** `DownloadManager` kini
  inject `NetworkState`; `awaitMeteredAllowed()` menahan unduhan saat **DISABLED + jaringan metered** (tunggu sampai
  Wi-Fi/Ethernet) sebelum ambil slot antrian. ENABLED/ASK lanjut. (Prompt ASK di dialog download = catatan, opsional.)
- **5B ✅ DONE — Battery optimization + info + page-save dir:**
  - **Battery-opt (Doki ignore_dose):** `rememberBatteryOptimizationRequest()` expect/actual — Android buka intent
    `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; Desktop null → baris **disembunyikan** (N/A). Stub "coming soon" diganti.
  - **Info hint** (Doki downloads_settings_info) ditampilkan sebagai teks.
  - **Default page-save dir (Doki pages_dir):** VM `pageSaveDir`/`setPageSaveDir`; **DesktopPagePersister** kini simpan ke
    dir terkonfigurasi (fallback ~/Pictures/Nekuva) + hormati **"ask every time"** (`pages_dir_ask`) lewat AWT Save dialog.
- **5C ✅ DONE — Custom download location di Android (SAF) + fix duplikat (feedback user):**
  - **`pickMangaDirectory()` Android** kini lewat SAF `OpenDocumentTree` (via `activityResultRegistry`), tree URI di-resolve
    ke **path file** (`treeUriToPath`: primary→`getExternalStorageDirectory`, lainnya→`/storage/<vol>`) seperti Doki →
    engine unduh berbasis `File` bisa tulis. `supportsDirectoryPicker = true` di Android → baris **"Specify directory"** di
    settings DAN **"Destination directory → Specify directory"** di dialog unduh (halaman detail) kini aktif di Android.
    Nudge **All-files access** (API 30+ `MANAGE_APP_ALL_FILES_ACCESS`) saat path di shared-storage & belum diizinkan.
  - **Fix duplikat "manga":** baris direktori kini tampil **path sebagai summary** (Android + Desktop) → dua dir app
    (internal/eksternal) tak lagi terlihat kembar; Android `getDirectoryDisplayName` pakai **nama volume** (`StorageManager.getDescription`).
- **5D ✅ DONE — Page-save dir Android (SAF, tutup catatan 5B):** `pickPageSaveDir()` expect/actual (Android: SAF
  `OpenDocumentTree` → simpan **tree URI** `content://`; Desktop: folder path). **AndroidPagePersister** ditulis ulang
  (Doki parity): **ask-every-time** → SAF `CreateDocument` prompt per simpan; **dir terkonfigurasi** (tree URI) → tulis
  ke tree via `DocumentsContract.createDocument` (tanpa dep androidx.documentfile); **fallback** → MediaStore (Pictures/Nekuva).
  Settings page-save-dir row pakai `pickPageSaveDir()` + label ramah (`pageSaveDirLabel`). Catatan kecil: prompt "ask" dari
  **reader di task terpisah (multitask)** pakai Activity foreground via `LocaleActivityHolder` (kasus umum = reader di MainActivity OK).
- **BUGFIX Android download crash/stuck (ROOT CAUSE, dari logcat):** `MangaIndex` menulis `manga.rating`
  (Float) + `chapter.number` (Float) via `JSONObject.put(String, Float)`. **org.json platform Android tak punya
  overload `put(String, Float)`** (hanya Maven org.json yang dipakai compile/Desktop) → `NoSuchMethodError` (Error,
  bukan Exception → lolos crash/stuck). Fix: `.toDouble()` pada kedua put (CLAUDE.md §4.6). + `runDownload` kini
  catch `Throwable` (Error tampil sbg "Gagal" + pesan, bukan crash/hang). **Aturan:** JANGAN `put(String, Float)` ke org.json.
- **BUGFIX (feedback user):** (1) **Android radio default tak respons** → `AndroidLocalStorageManager.getDefaultWriteableDir()`
  dulu hardcode `null` (abaikan `mangaStorageDir`); kini baca `mangaStorageDir` spt Desktop. (2) **Force-close saat unduh ke
  dir custom Android** → `DownloadManager.scope` dapat `CoroutineExceptionHandler` (uncaught di engine dicatat, bukan crash) +
  manifest `requestLegacyExternalStorage=true`. (3) **Local masih tampil manga lama setelah ganti default** = **perilaku benar
  (sama Doki)**: Local memindai SEMUA dir terkonfigurasi (app internal/eksternal + tiap folder custom), default hanya
  menentukan tujuan unduhan BARU; manga lama tetap di dir-nya → tetap tampil. Bukan bug.
- **FASE 5 SELESAI** (5A/5B/5C/5D compile + assembleDebug hijau; belum run-verify GUI). Catatan struktur: Doki memisah
  "Downloads folder" (dialog) + "Local manga directories" (layar); Nekuva memakai satu daftar radio + path (fungsional setara).
### FASE 6 — Tracker (`pref_tracker.xml`) — checklist + impact
> Ref Doki: `settings/tracker/TrackerSettingsFragment` + `pref_tracker.xml`. Model Nekuva: tracker **manual** via
> tab Feed (`FeedViewModel.refresh`, pull-to-refresh) — TIDAK ada WorkManager/scheduler latar belakang (KMP/Desktop).
- **6A ✅ DONE — Paritas UI + dependency:** `tracker_enabled` (master switch) men-disable semua sub-pref (Doki dependency).
  Layar punya: wifi-only, frekuensi, track_sources, kategori, notifikasi, no_nsfw, download, + kategori **Debug** (battery + warning).
- **6B ✅ DONE — track_categories:** entri "Kategori favorit" kini **navigate ke CategoriesRoute** (manajer kategori yang
  SUDAH punya toggle "track" per kategori). Impact sudah jalan: `updateTracks` pakai `findIdsWithTrack` (favourites di
  kategori `track=1`). Stub "coming soon" diganti.
- **6C ✅ DONE — notifications_settings:** `rememberNotificationSettingsRequest()` expect/actual — Android buka
  `ACTION_APP_NOTIFICATION_SETTINGS` (kanal sound/vibrate/light); Desktop null → baris disembunyikan. Battery = `ignore_dose`
  (reuse `rememberBatteryOptimizationRequest`). Warning text (`track_warning`) ditampilkan.
- **6D ✅ DONE — tracker_download impact + fix enum:** UI dulu `IndexListPref` (index) tapi `trackerDownloadStrategy` baca
  `getEnum` (NAMA) → selalu DISABLED. Diganti `SettingsSingleChoice<TrackerDownloadStrategy>`. **Impact:** `FeedViewModel.refresh`
  kini auto-unduh bab baru saat strategi=DOWNLOADED & manga sudah tersimpan lokal (`observeSavedIds`) → `DownloadManager.schedule`.
- **Sudah ada:** `track_sources` (favourites/history) ter-wire di `updateTracks`.
- **6E ✅ DONE — Background tracker Android (atas keputusan user "kerjakan penuh"):** `scheduleTracker()` expect/actual —
  **Android** pakai **WorkManager** (`androidx.work.runtime` ditambah ke androidMain): `PeriodicWorkRequest` interval dari
  `tracker_freq` (Manual=batal, Less=48j, Default=24j, More=8j) + constraint `NetworkType.UNMETERED` saat `tracker_wifi`;
  **Desktop** = no-op (tetap manual). **`TrackerWorker`** (CoroutineWorker, deps via Koin): `updateTracks` → cek tiap track →
  `saveUpdates` → auto-download (`tracker_download`=DOWNLOADED) → **notifikasi per-manga** (skip NSFW saat `tracker_no_nsfw`,
  tap → buka manga via `EXTRA_MANGA_ID`). Dijadwalkan saat app start (`App()`) + di-reschedule saat keluar layar Tracker
  (`DisposableEffect.onDispose`). `tracker_enabled`/`freq`/`wifi`/`no_nsfw`/`download` kini BENAR-BENAR berpengaruh di Android.
- **DEFERRED kecil:** notifikasi grup + foreground-service progress (Doki) disederhanakan (notif per-manga saja); `tracker_debug`
  (log worker) tak dibuat; Desktop tetap manual (N/A WorkManager). 
- **FASE 6 SELESAI** (6A–6E compile + assembleDebug hijau; belum run-verify GUI).

### FASE 7 — Services + Privacy — checklist + impact
> Ref Doki: `scrobbling/*` (Shikimori/AniList/MAL/Kitsu + Discord) + `settings/.../ServicesSettingsFragment`.
> Keputusan user: "Migrasikan semua kode, pakai client id dari doki dulu, lalu beritahu dimana akan saya ganti nanti".
- **7-Scrobblers ✅ DONE (compile + assembleDebug hijau; belum run-verify, butuh OAuth nyata):** migrasi penuh
  **AniList** (OAuth2 + GraphQL), **MAL** (OAuth2 PKCE plain + REST), **Kitsu** (OAuth2 password-grant + JSON:API),
  melengkapi **Shikimori** yang sudah ada. Tiap service: `*Repository` (port dari Doki, JSON via org.json inline
  helper, klien id dari `ScrobblerConfig`), `*Interceptor` (header + Bearer token + 401→re-login), `*Scrobbler`
  (status map). Semua didaftarkan di Koin `scrobblingModule` (OkHttp + `ScrobblerStorage` per-service) dan masuk
  `ScrobblerManager(listOf(...))` → muncul di **Settings ▸ Services ▸ Tracking** lewat `scrobblerItems` (status
  login per-service: sign in / logout).
- **Login UI:** AniList/MAL/Shikimori pakai webview OAuth (`OAuthScreen`, tangkap `code=` dari redirect
  `nekuva://oauth`). **Kitsu** pakai *password grant* → **dialog username/password** baru di `ServicesSettingsScreen`
  (`KitsuLoginDialog`), kirim `"username;password"` ke `completeAuth` (port `KitsuAuthActivity`).
- **⚠️ KREDENSIAL (di mana diganti):** semua client id/secret ada di
  `composeApp/src/jvmSharedMain/.../scrobbling/common/ScrobblerConfig.kt` (cari `TODO(credentials)`). Saat ini diisi
  **client id publik Doki** sebagai placeholder — TERIKAT ke redirect URI Doki, jadi login OAuth nyata belum jalan
  sampai user daftar app sendiri (AniList/MAL/Kitsu/Discord/Shikimori) dgn redirect = `nekuva://oauth` lalu ganti
  nilainya di file itu.
- **DEFERRED Fase 7 (increment berikut):** **Discord RPC** (gateway WebSocket + token webview, Android-only),
  **Suggestions** (engine + layar), **reading stats** (layar), **reading_time** estimasi (saat ini hanya BoolPref),
  **related_manga** section di Details. Catatan kompat OAuth-redirect untuk webview (`OAuthScreen`) bila skema
  `nekuva://` ditelan engine tanpa URL-change → butuh navigation-intercept (uji saat client id nyata tersedia).
- **Fase 8 Backup:** periodic backup (enable/dir/freq/trim/count) + Telegram (Android WorkManager actual).
- **Fase 9 About:** changelog + app-update checker + sisa link + implementasi icon aplikasi(slpash screen dan icon app di dekstop dan android) sekarang ada image png 1024x1024 di logo\logo.png instruksikan apa yang perlu disiapkan untuk logo aplikasi dan taruh dimana untuk desktop(windows/linux/mac os) dan android, tambahan refactor README.md keterangan Desktop tambah linux dan tambahkan logo diatas judul Nekuva.

**Top bar per-tab (Doki parity):** search + overflow di History/Favourites/Explore/Feed/Local. Hanya
**Settings** yang fungsional; item overflow lain (Hapus riwayat, Opsi daftar, Statistik, Kategori disukai,
Saring, Direktori, Perbarui, Tampilkan yang diperbarui, Bersihkan umpan, Kelola sumber, Mode penyamaran)
**ditampilkan disabled → deferred ke sesi polish** (sesuai permintaan). Search box internal Explore lama dihapus.

**Phase S1 DONE (pending run-verify Android+Desktop):**
- [x] **Menu root** 9 kategori; 3 aktif (Appearance/Downloads/About), 6 lain tampil "Segera hadir" (disabled).
- [x] **Appearance**: Tema (ikuti sistem / terang / gelap) + **AMOLED** — keduanya **live re-theme** (App() observe
      `observeTheme`/`observeAmoled` via `ObservableSettings.toFlowSettings`). `theme`/`isAmoledTheme` jadi `var`.
- [x] **Downloads**: kelola **direktori unduhan** (daftar dir writeable + set-default via radio + hapus dir kustom +
      **tambah folder** via picker Desktop) + **format unduhan** default. Memakai `userSpecifiedMangaDirectories`/
      `mangaStorageDir`/`preferredDownloadFormat` (jadi `var`).
- [x] **About**: versi + link **Source code (GitHub)** via `LocalUriHandler` (lintas-platform).

**Phase S2:**
- [x] **Backup & Restore** (pending run-verify): ekspor/impor **favorit + kategori + riwayat + bookmark**
      (+ manga/tags) ke file **.zip berisi JSON per-section** (entry name = `index/history/categories/favourites/
      bookmarks`, **format sama dgn Doki** → berpotensi cross-compatible). `BackupRepository` (kotlinx.serialization
      + java.util.zip). File picker lintas-platform: **Desktop JFileChooser**, **Android SAF** (Create/Open
      Document via `rememberLauncherForActivityResult` + CompletableDeferred bridge). Restore = upsert (manga dulu,
      lalu row), lanjut-saat-gagal per item.
      DEFERRED: backup **settings** (multiplatform-settings tak punya generic put), sources/scrobbling/stats/
      saved-filters (belum dimigrasi), reader-grid, periodic/Telegram backup, pilih-section saat restore.
- [x] **Storage & Network** (pending run-verify): **Image proxy** (none/wsrv.nl/0ms.dev) — kini **live**
      karena `observeAsStateFlow` yg sebelumnya STUB diperbaiki pakai `ObservableSettings.toFlowSettings`
      (`keyChangeFlow`); `imagesProxy` jadi `var`. **Data removal**: Clear thumbnails cache (Coil memory+disk
      via `SingletonImageLoader`/`LocalPlatformContext`) + Clear cookies (`MutableCookieJar.clear`, dgn konfirmasi).
      DEFERRED (butuh wiring network-layer yg belum ada): proxy (type/addr/port/auth/test), DoH, SSL bypass,
      connectivity check, adblock, prefetch/preload, clear pages-cache (LocalStorageCache belum di-DI/Context-coupled),
      clear HTTP/DB/webview, storage-usage meter.

**Phase S3 — DEFERRED (UI bisa dibuat, fungsi tergantung area lain):**
- [ ] **Reader settings** (~30 pref) → butuh reader-advanced.
- [ ] **Remote sources** (enable/urut/katalog/auth) → area `sources`.
- [ ] **Tracker / Services / Sync** (AniList/Kitsu/MAL/Shikimori, Discord RPC, stats, sync) → area tracker/scrobbling/sync/stats.
- [ ] **Appearance lanjutan**: bahasa (in-app locale override — kompleks lintas-platform), list mode, grid size,
      badge, nav config, app-lock/biometric, screenshots policy → sebagian butuh wiring layar konsumen / area lain.
- [ ] **Downloads lanjutan**: download-over-metered (butuh connectivity check), pages-saving dir (area image/save),
      battery optimization (Android).

### Cross-cutting
- [ ] Security (Biometric Lock / App Lock).
- [ ] Tema / UI Lanjutan (Mis. Material You dynamic color).
- [ ] Crashlytics / ACRA (Platform specific).
