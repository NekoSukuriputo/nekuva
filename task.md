# Nekuva KMP — Task Ledger (status NYATA)

> Status di sini disinkronkan ke kondisi yang BENAR-BENAR jalan (run-verified), bukan compile.
> Tanda `[~]` = in-progress / re-opened. Tanda `[x]` hanya setelah run-verified (§0/§7 CLAUDE.md).
> Detail item tertunda per-area ada di `MIGRATION.md`.

## Area Fitur (Phase 1)

- [x] Core (model, prefs, db, network, util, theme, i18n setup, back-nav)
- [x] local
- [x] explore
- [x] list
- [x] remotelist
- [x] details
- [x] reader (MINIMAL — reader lanjutan ditunda, lihat MIGRATION.md)
- [x] main (shell, navigasi adaptif)
- [x] favourites
- [x] history — 2 bug inti FIXED & run-verified Android+Desktop (2026-06-08):
      1. Manga yang dibaca KINI muncul di tab History (tulis history andal; error tak ditelan diam).
      2. Continue/Read KINI resume ke chapter + halaman terakhir (scrollToItem + guard page-0).
      Item parity lanjutan (sort/filter/grouping/list-mode/pagination/multi-select/clear-opsi/
      incognito-banner/progress-indicator/string i18n) tetap deferred — lihat MIGRATION.md "Area: History".
- [ ] bookmarks
- [ ] search
- [ ] filter
- [ ] download
- [ ] tracker
- [ ] scrobbling
- [ ] sync
- [ ] settings
- [ ] alternatives
- [ ] browser
- [ ] picker
- [ ] widget
- [ ] backups
- [ ] stats
- [ ] suggestions
- [ ] image

## Fokus saat ini: FIX area `history`

Lihat parity checklist + rencana fix di sesi ini. Definisi done: build hijau Android+Desktop,
RUN Desktop, dan tab History menampilkan manga yang baru dibaca + Continue resume ke halaman
terakhir, dikonfirmasi manusia via screenshot (§7).
