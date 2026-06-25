# Nekuva — Third‑party credentials guide

How to obtain the credentials for the "blocked by credentials" features. **All of these are FREE** — none
require payment. Where you paste each value is noted.

| Service | Cost | Where the value goes | Needs the `nekuva://oauth` redirect? |
|---|---|---|---|
| AniList | Free | `ScrobblerConfig.ANILIST_CLIENT_ID` / `ANILIST_CLIENT_SECRET` | Yes |
| MyAnimeList (MAL) | Free | `ScrobblerConfig.MAL_CLIENT_ID` (PKCE, no secret) | Yes |
| Kitsu | Free | already set (shared public client) — **no action** | No (password login) |
| Shikimori | Free | `ScrobblerConfig.SHIKIMORI_CLIENT_ID` / `SHIKIMORI_CLIENT_SECRET` | Yes |
| Telegram backup bot | Free | `local.properties` → `tg_backup_bot_token=…` + `TelegramBackupConfig.BOT_NAME` | No |
| Discord RPC | Free | `ScrobblerConfig.DISCORD_APP_ID` | No (menu hidden) |

> The values currently in `ScrobblerConfig.kt` are **Doki/Kotatsu's** public dev credentials. For AniList /
> MAL / Shikimori they are registered against Doki's own redirect URL, so login will fail until you replace
> them with your own. **Kitsu** is the exception — its values are Kitsu's *shared public* client, so it works
> as‑is.

File: `composeApp/src/jvmSharedMain/kotlin/org/nekosukuriputo/nekuva/scrobbling/common/ScrobblerConfig.kt`
(search `TODO(credentials)`). **Redirect / callback URL for every OAuth app below: `nekuva://oauth`.**

---

## AniList
1. Sign in at <https://anilist.co> → **Settings → Developer** (<https://anilist.co/settings/developer>).
2. **Create New Client**. Name: `Nekuva`. **Redirect URL:** `nekuva://oauth`.
3. Save, then copy the **Client ID** and **Client Secret**.
4. Paste into `ANILIST_CLIENT_ID` and `ANILIST_CLIENT_SECRET`.

## MyAnimeList (MAL)
1. Sign in at <https://myanimelist.net> → **Account Settings → API** (<https://myanimelist.net/apiconfig>).
2. **Create ID**. App Type: `other`. App Name: `Nekuva`. **App Redirect URL:** `nekuva://oauth`.
   Fill the required fields (homepage/description — anything valid), agree, submit.
3. Copy the **Client ID**. Paste into `MAL_CLIENT_ID`.
4. MAL uses **PKCE**, so there is **no client secret** — leave it as is (only the id is needed).

## Kitsu
- **Nothing to do.** Kitsu uses a single shared public OAuth client (already in `KITSU_CLIENT_ID` /
  `KITSU_CLIENT_SECRET`) and logs in with the user's Kitsu **email + password** (resource‑owner password
  grant) — there's no per‑app registration and no redirect URL.

## Shikimori
1. Sign in at <https://shikimori.one> → **OAuth applications** (<https://shikimori.one/oauth/applications>).
2. **New Application.** Name: `Nekuva`. **Redirect URI:** `nekuva://oauth`. Scopes: at least `user_rates`
   (add `comments`, `topics` etc. only if needed).
3. Save, then copy the **Client ID** and **Client Secret**.
4. Paste into `SHIKIMORI_CLIENT_ID` and `SHIKIMORI_CLIENT_SECRET`.
   - Shikimori requires a descriptive **User‑Agent** = your app name; the app already sends one.

## Telegram backup bot
The default `BOT_NAME` is Kotatsu's public bot (`kotatsu_backup_bot`), but you don't have its **token**
(it's their secret), so make your **own** bot:
1. In Telegram, open **@BotFather** → `/newbot` → give it a name and a username ending in `bot`.
2. BotFather replies with a **token** like `123456789:AAH…`.
3. Set `TelegramBackupConfig.BOT_NAME` to your bot's username (without `@`).
4. Put the token in `local.properties` (NOT committed):
   ```
   tg_backup_bot_token=123456789:AAH…
   ```
   (or pass `-Dtg_backup_bot_token=…` / env `TG_BACKUP_BOT_TOKEN`), then rebuild.
5. The Telegram backup section is hidden until the token is present; once built with it, open the app, start a
   chat with your bot, and follow the in‑app flow to link your chat id.

## Discord RPC (currently hidden — personal app)
The menu is gated off by `SHOW_DISCORD_RPC_MENU = false` in `ServicesSettingsScreen.kt`; the code is intact.
To re‑enable later: flip that flag to `true`, then:
1. <https://discord.com/developers/applications> → **New Application** → name `Nekuva`.
2. Copy the **Application ID** → `ScrobblerConfig.DISCORD_APP_ID`.
   (Login itself uses your Discord user token captured via the in‑app webview; the app id just names the
   "playing" activity.)

---

### Notes
- After editing `ScrobblerConfig.kt`, rebuild. An **empty** client id makes that scrobbler "unconfigured"
  (its login row shows "coming soon" and is disabled).
- None of the above cost money, so nothing here is deferred for payment. Discord is hidden only because this
  is a personal app (no community), not because of cost.
- **Sync server** (Kotatsu sync) is separate: the code is complete but needs a running Kotatsu‑sync server +
  an account to run‑verify — no client credential to paste.
- **Translate this app** stays disabled until a Weblate/Crowdin project exists for Nekuva (no credential).
