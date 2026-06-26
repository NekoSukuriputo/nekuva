# Nekuva — Third‑party credentials guide

How to obtain/configure the credentials for the "blocked by credentials" features. **All of these are FREE.**

**Real secrets are NEVER committed.** AniList / MyAnimeList / Telegram are read at build time from
`local.properties` (gitignored) — `-D<key>` / env also work (used for GitHub Actions secrets on release) —
and generated into code (`ScrobblerSecrets` / `TelegramSecrets`). An empty/missing key → that feature stays
"unconfigured" (its login row shows disabled). **OAuth redirect / callback URL for every app: `nekuva://oauth`.**

| Service | Cost | Where the value goes | In the menu? |
|---|---|---|---|
| AniList | Free | `local.properties` → `anilist_client_id`, `anilist_client_secret` | Yes |
| MyAnimeList (MAL) | Free | `local.properties` → `mal_client_id` (PKCE, no secret) | Yes |
| Telegram backup bot | Free | `local.properties` → `tg_backup_bot_token` | Yes (when token present) |
| Shikimori | Free | code only (`ScrobblerConfig`) | **Hidden** (code kept) |
| Kitsu | Free | code only (`ScrobblerConfig`) | **Hidden** (code kept) |
| Discord RPC | Free | `ScrobblerConfig.DISCORD_APP_ID` | **Hidden** (menu gate) |

> Shikimori/Kitsu are hidden from Settings → Services (filtered in `ScrobblerConfigViewModel`); their code is
> still compiled. Discord is hidden via `SHOW_DISCORD_RPC_MENU = false`. To re‑show any of them, undo that gate.

---

## `local.properties` (your machine — gitignored, never commit)

```
# Scrobbler OAuth
anilist_client_id=<AniList numeric Client ID>
anilist_client_secret=<AniList Client Secret>
mal_client_id=<MyAnimeList Client ID>      # PKCE app: id only, no secret

# Telegram backup bot
tg_backup_bot_token=<botfather token, e.g. 123456789:AAH…>
```

Rebuild after editing (the values are baked in by the `generateScrobblerSecrets` / `generateTelegramSecrets`
Gradle tasks). Verify it took: the generated file at
`composeApp/build/generated/scrobblerSecrets/kotlin/.../ScrobblerSecrets.kt` should show your ids.

## How to obtain each

### AniList
1. <https://anilist.co/settings/developer> → **Create New Client**. Name `Nekuva`, **Redirect URL** `nekuva://oauth`.
2. Copy **Client ID** (numeric) → `anilist_client_id`, **Client Secret** → `anilist_client_secret`.

### MyAnimeList (MAL)
1. <https://myanimelist.net/apiconfig> → **Create ID**. App Type `other`, **App Redirect URL** `nekuva://oauth`.
2. Copy **Client ID** → `mal_client_id`. MAL uses **PKCE** → no secret needed.

### Shikimori / Kitsu (hidden)
Nothing to do — hidden from the menu. If you ever re‑enable them, register at
<https://shikimori.one/oauth/applications> (redirect `nekuva://oauth`) / Kitsu uses email+password, and move
their ids to secrets the same way as AniList.

---

## GitHub Actions secrets (for the release build in CI)

`local.properties` isn't on CI, so add the same values as **repository secrets** and the build picks them up
from the matching **env var** (the Gradle tasks read `-D` → `local.properties` → env, in that order).

1. GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**. Add:
   - `ANILIST_CLIENT_ID`
   - `ANILIST_CLIENT_SECRET`
   - `MAL_CLIENT_ID`
   - `TG_BACKUP_BOT_TOKEN`
   - (already there for signing: `EXT_SIGNING_KEY`, `NEKUVA_STORE_FILE`/`…_PASSWORD`/`…_KEY_ALIAS`/`…_KEY_PASSWORD`)
2. In the release workflow, expose them to the Gradle step as env vars, e.g.:
   ```yaml
   - name: Build release
     env:
       ANILIST_CLIENT_ID: ${{ secrets.ANILIST_CLIENT_ID }}
       ANILIST_CLIENT_SECRET: ${{ secrets.ANILIST_CLIENT_SECRET }}
       MAL_CLIENT_ID: ${{ secrets.MAL_CLIENT_ID }}
       TG_BACKUP_BOT_TOKEN: ${{ secrets.TG_BACKUP_BOT_TOKEN }}
     run: ./gradlew :composeApp:assembleRelease
   ```
   The env‑var **names** must match exactly (uppercase). No code change needed.

---

## Telegram backup — bot + chat id (for testing)

The bot needs **no code/commands**: Nekuva sends the backup file itself via the bot token + your chat id.
The bot is just a delivery pipe ("all actions are performed by the application and YOU").

1. **Create the bot:** @BotFather → `/newbot` → get the token → put it in `local.properties` as
   `tg_backup_bot_token=…`, rebuild. (Optional: `/setdescription` to add the "What can this bot do?" text.)
2. **Start the bot once:** open your bot in Telegram and tap **Start** — a bot can't message you until you've
   started it.
3. **Get your chat id:** open **@userinfobot** → Start → it replies `Id: 123456789`. That number is your chat id.
   - Manual alternative: after messaging your bot, open
     `https://api.telegram.org/bot<TOKEN>/getUpdates` and read `"chat":{"id":…}` (don't share that URL — it
     contains the token).
4. **Enter it in the app:** Settings → **Backup berkala / Periodic backup → Telegram chat ID** → paste the
   number → **Test connection**. On success the bot sends a test file to your chat. (Stored as
   `AppSettings.KEY_BACKUP_TG_CHAT`; the Telegram section only appears once the token is built in.)

---

## Discord RPC (hidden — personal app)
Gated by `SHOW_DISCORD_RPC_MENU = false` in `ServicesSettingsScreen.kt`; code intact. To re‑enable: flip to
`true`, then set `ScrobblerConfig.DISCORD_APP_ID` from <https://discord.com/developers/applications> (the app
id just names the "playing" activity; login uses your Discord user token via the in‑app webview).

### Notes
- An **empty** client id → that scrobbler is "unconfigured" (login row disabled). Rebuild after editing.
- Nothing here costs money. Shikimori/Kitsu/Discord are hidden by choice (personal app), not cost.
- **Sync server** (Kotatsu sync) needs a running sync server + account to verify — no client credential.
