# Nekuva KMP Migration Plan

## Phase 0: Skeleton (Kerangka Multiplatform)
Fokus: Bangun kerangka KMP/CMP tanpa memigrasikan fitur, memastikan aplikasi Android saat ini tetap bisa di-build.
- Konversi Gradle ke Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`).
- Siapkan `gradle/libs.versions.toml` dengan dependensi KMP (Kotlin Multiplatform, Compose Multiplatform, Room KMP, Koin, Ktor client, Coil 3).
- Buat modul `composeApp` (target Android & Desktop).
- Entrypoint: Android (`androidMain`) dan Desktop (`desktopMain`) menampilkan placeholder Compose Material 3 ("Nekuva KMP â€” Phase 0 OK").
- Setup dependensi plumbing di `commonMain` (Koin, Ktor, Room KMP, Coil 3).
- Sambungkan `nekuva-exts` dan buat implementasi `MangaLoaderContext` konkret per platform (WebView Android, Desktop JVM engine JS).
- Setup keystore release baru (bukan milik doki).
- **Kriteria Selesai Phase 0**: `./gradlew :composeApp:assembleDebug` hijau, Desktop run jalan, `nekuva-exts` tersambung. Tidak ada fitur lama yang terhapus dan TIDAK ADA KODE/FILE BARU dengan branding "doki".

## Phase 1: Android + Desktop Parity (Re-implementasi Fitur)
Fokus: Membangun ulang seluruh UI/fitur dari XML Views ke Compose Multiplatform satu per satu, mendukung Android dan Desktop (Windows/macOS).

### Checklist Migrasi Phase 1 (Core + 25 Area Fitur)

**Keputusan Source Set Non-Main:**
- legacy: Dihapus (KMP adalah proyek legacy-free)
- debug / elease: Dipertahankan untuk konfigurasi ndroidMain (mis. API key spesifik) namun diletakkan di composeApp/src/androidDebug dst jika memang diperlukan, jika tidak dihapus.

**Progress Checklist:**
- [x] 1. Core (Models, Prefs, Network, DB, Util Infrastructure)
- [ ] 2. Local Storage
- [ ] 3. Explore (Catalog Browse)
- [ ] 4. List (Manga Lists)
- [ ] 5. Details (Manga Details)
- [ ] 6. Reader
- [ ] 7. Search
- [ ] 8. Filter
- [ ] 9. Favourites (Library)
- [ ] 10. History
- [ ] 11. Bookmarks
- [ ] 12. Download
- [ ] 13. Tracker
- [ ] 14. Scrobbling
- [ ] 15. Sync
- [ ] 16. Settings
- [ ] 17. Main Shell
- [ ] 18. Navigation
- [ ] 19. Widget
- [ ] 20. Backups
- [ ] 21. Stats
- [ ] 22. Suggestions
- [ ] 23. About / Info
- [ ] 24. App Updates
- [ ] 25. Extension Management
- [ ] 26. Security (Biometric Lock)

