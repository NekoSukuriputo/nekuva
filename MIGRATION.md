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
- [x] `reader` (Done, history integrated)
- [x] `main` (Shell, adaptive navigasi)
- [ ] `image`
- [ ] `search`
- [ ] `filter`
- [x] `favourites`
- [x] `history` (2 bug inti FIXED & run-verified Android+Desktop 2026-06-08: tampil di History + resume halaman. Item parity lanjutan tetap deferred — lihat ledger)
- [ ] `bookmarks`
- [ ] `download`
- [ ] `tracker`
- [ ] `scrobbling`
- [ ] `sync`
- [ ] `settings` (Termasuk Security/Biometric, UI/Theme lanjutan, ACRA dll)
- [ ] `alternatives`
- [ ] `browser`
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
- [ ] Fase berikut — **Global search dari Explore** (multi-source): hasil dikelompokkan per source,
      header, paging per source. Search bar Explore masih dummy sampai fase ini.
- [ ] Search suggestions (query/author/tag/source) + riwayat pencarian (recent queries) + hint.
- [ ] **Parity item grid (lintas-layar, sesi terpisah)**: overlay di cover — ikon hati favorit +
      badge progres baca (%/centang) — seperti Doki (Explore/Favourites/History/RemoteList).
- [ ] Field filter lain: Demographics, Locale & Original locale, Year / Year range, Author search.
- [ ] **SavedFilters preset** (tombol "Simpan"): serialisasi `MangaListFilter`, simpan/rename/hapus
      preset bernama per-source (`SavedFiltersRepository`), chip preset di atas sheet. Tombol "Simpan"
      SUDAH ditampilkan di posisi/label Doki tapi **DISABLED** (deviasi sadar) sampai subsistem ini dibuat.
- [ ] "Lebih"/full tags catalog sheet (TagsCatalogSheet) dengan search tag (kini sheet tampilkan
      semua tag tersedia sebagai chip).
- [ ] Footer "Global search" pada hasil per-source; "Open in browser" pada error.

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

### Area: Settings & Cross-cutting
- [ ] Security (Biometric Lock / App Lock).
- [ ] Tema / UI Lanjutan (Mis. AMOLED murni, Material You dynamic color).
- [ ] Crashlytics / ACRA (Platform specific).