<!-- Thanks for contributing to Nekuva! Keep this short but complete. -->

## Summary
<!-- What does this PR change, and why? -->

Fixes #<!-- issue number, if any -->

## Type
<!-- Conventional Commit type — tick one -->
- [ ] `feat` — new feature
- [ ] `fix` — bug fix
- [ ] `refactor` / `chore` / `build` / `ci`
- [ ] `docs` / `test`

## Area
<!-- e.g. reader, explore, details, downloads, extensions, settings, backup, ci -->

## How it was tested
> Compiling is **not** enough — say what you actually ran (Nekuva's Definition of Done).

- [ ] Android — `./gradlew :composeApp:assembleDebug` builds
- [ ] Desktop — `./gradlew :composeApp:run` launches
- [ ] **Run-verified** on a real device / desktop (not just compiled) — describe below
- [ ] Tests pass where applicable

Platforms verified: <!-- Android / Windows / Linux / macOS -->

What I exercised: <!-- e.g. opened KomikTap → read 3 chapters, no flicker -->

## Screenshots / recording
<!-- Required for any UI change — before / after, or a short clip. -->

## Checklist
- [ ] No hardcoded user-facing strings — text goes through Compose Resources (`Res.string`)
- [ ] No `doki` / `dokiteam` / "Doki" branding introduced (GPL attribution in headers/README excepted)
- [ ] No parser / scraper / site-specific code added here (those belong in **nekuva-exts**)
- [ ] Parity checked against the legacy Doki reference where relevant
- [ ] `MIGRATION.md` / docs updated if behaviour or scope changed
- [ ] No secrets committed (kept in `local.properties` / CI secrets)
