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
      **R4** (save-page, koreksi warna, auto-scroll, double-page, keep-screen-on/fullscreen/orientasi/volume —
      platform Android) = berikutnya.)
- [x] `main` (Shell, adaptive navigasi)
- [ ] `image`
- [x] `search` (run-verified Android+Desktop: global multi-source search streaming (Riwayat/Disukai/Lokal + sumber paralel), saran as-you-type S1 (tag/manga/riwayat-query/sumber+switch/penulis, hormati `searchSuggestionTypes`, tak dicatat saat incognito), footer "Cari sumber nonaktif" + "Buka di browser" pada error. Lihat ledger Area Search & Filter)
- [x] `filter` (run-verified Android+Desktop: sheet filter parity Doki — Urutkan + Bahasa/Bahasa-asli + Penulis + Genre (+ katalog tag lengkap berpencarian) + Kecualikan + Tipe + Status + Content-rating + Demografi + Tahun/Rentang-tahun, capability-gated; chip filter aktif di header; **Saved Filters** (simpan/terapkan/rename/hapus per-source). Lihat ledger Area Search & Filter)
- [x] `favourites`
- [x] `history` (2 bug inti FIXED & run-verified Android+Desktop 2026-06-08: tampil di History + resume halaman. Item parity lanjutan tetap deferred — lihat ledger)
- [x] `bookmarks` (page bookmarks, run-verified Android+Desktop: Doki-style reader overlay (tahan layar → app bar + tombol mengambang → bottom sheet "Opsi") dengan **bookmark fungsional**; layar Bookmarks grouped + selection multi-remove + undo; **markah tampil di bottom sheet Detail manga** (thumbnail halaman → tap buka reader di halaman persis). Fungsi sheet lain (mode baca, save page, dll) deferred ke reader-polish — lihat ledger)
- [x] `download` (run-verified Android+Desktop: engine coroutine KMP (BUKAN WorkManager) dengan **output desain `index.json` Doki** — `MangaIndex`(org.json, `compileOnly(libs.json)`) + `ZipOutput` asli; `LocalMangaZipOutput`=SINGLE_CBZ (satu `.cbz` flat + index.json), `LocalMangaDirOutput`=MULTIPLE_CBZ (per-bab `.cbz` + index.json), `canWriteTo` (cocok manga.id, kalau tidak sufiks `_1`), id bab = id remote asli. Dialog "Save manga" (4 makro + format + tujuan + folder picker Desktop), trigger Detail, layar Downloads manager card-based ala Doki. **Fitur run-verified:** unduh→muncul di Local dgn **cover asli** (`addCover`), buka & **baca offline** manga unduhan, **resume** (bab sudah-unduh otomatis ✓ tak diulang), **retry** (tombol kartu = semua bab gagal + ikon ↻ per-bab), **pause** (ikon pause, bukan spinner), **cancel** (tak ada spinner nyangkut), pembersihan temp (`page*.tmp`/`*.cbz.tmp`), folder kustom persist, lanjut-saat-gagal. Hapus manga lokal (long-press di Local). Notifikasi foreground (Android), metered-network, save-page dll deferred — lihat ledger)
- [ ] `tracker`
- [ ] `scrobbling`
- [ ] `sync`
- [~] `settings` (pending run-verify — **SEMUA preference Doki kini ditampilkan & harus sama**, sesuai
      permintaan full-parity: Appearance/Reader/Storage&Network/Downloads/Tracker/Services/Backup/About lengkap.
      Beberapa BEHAVIOR menunggu area konsumennya (reader-advanced, tracker, sync, stats, biometric, proxy/DoH);
      nilainya tetap tersimpan & wired saat area itu jadi. Sub-screen (nav config, proxy, suggestions, login
      tracker, discord) = "Segera hadir". Lihat ledger)
- [ ] `alternatives`
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
- [ ] Paged Mode (LTR / Standard) dengan Pager.
- [ ] Reversed Mode (RTL).
- [ ] Vertical Mode dengan margin/gaps spesifik (saat ini vertical biasa menggunakan LazyColumn).
- [ ] Double-page Mode untuk Tablet/Foldable.
- [ ] Page navigation controls (tap zones, next/prev chapter buttons).
- [ ] Top App Bar Overlay (Auto-hide).
- [ ] Bottom Actions Overlay (Slider halaman, Next/Prev Chapter).
- [ ] Reader Info Bar (Jam, Baterai, Nomor Halaman di Header).
- [ ] Tap Grid Overlay (Ketuk kiri/kanan untuk navigasi, tengah untuk UI).
- [ ] Reader Settings overlay (Brightness, keep screen on, color filter).
- [ ] Zoom / Pan / Pinch-to-zoom Controls.
- [x] Navigasi antar-chapter (DONE & run-verified Android + Desktop — subset reader-advanced):
      - Forward continuous-append (muat chapter berikutnya saat mendekati ujung) untuk mode vertikal.
      - Tombol Next/Prev chapter eksplisit di toolbar (Prev = ganti konten ke chapter sebelumnya).
      - Update history saat pindah chapter (chapter baru + reset halaman) via jalur history yang ada.
      - Boundary aman: chapter terakhir tanpa Next, pertama tanpa Prev (tidak crash).
      DEFERRED dari sub-fitur ini:
      - [ ] Backward continuous-prepend (scroll ke atas masuk chapter sebelumnya di list yang sama;
            ditunda karena masalah "prepend jump" pada LazyColumn — sementara pakai tombol Prev).
      - [ ] Pemilihan branch/translasi (`manga.chapters[branch]`); sementara pakai urutan
            `manga.chapters` apa adanya.
      - [ ] Page trimming memori (Doki trim >120 halaman); sementara semua chapter termuat tetap di list.
- [ ] Keep Screen On.
- [ ] RegionBitmapDecoder (untuk gambar sangat panjang/subsampling).
- [ ] Page Save & Share.
- [ ] Bookmarks per halaman.
- [ ] Scroll Timer / Auto-scroll.
- [ ] CloudFlare/JS evaluation (evaluateJs) on Desktop and Android WebView (Currently stubbed).
- [ ] Update read progress/history setelah membaca (Discord RPC / Trackers Update & Reading History).

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
- [ ] **Incognito saat buka dari bookmark** (Doki paksa incognito + toast) — area **incognito** (deferred).
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
