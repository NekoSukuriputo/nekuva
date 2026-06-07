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
- [x] `details`
- [x] `reader` (Minimal fungsional)
- [x] `main` (Shell, adaptive navigasi)
- [ ] `image`
- [ ] `search`
- [ ] `filter`
- [x] `favourites`
- [ ] `history`
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
- [ ] Navigasi Prev/Next Chapter saat menyentuh batas akhir/awal.
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

### Area: Settings & Cross-cutting
- [ ] Security (Biometric Lock / App Lock).
- [ ] Tema / UI Lanjutan (Mis. AMOLED murni, Material You dynamic color).
- [ ] Crashlytics / ACRA (Platform specific).