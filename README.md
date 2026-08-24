# cDm

Native **Android** dashboard for [COSMOS](https://github.com/keithbbf-gif/cosmos). The phone-sized sibling of [cDeck](https://github.com/keithbbf-gif/cdeck). Kotlin + Jetpack Compose. Not VMC (voice) — they share the API, not the app.

Reads the live COSMOS HTTP API (`http://<host>:8791`) over LAN or Tailscale.

Full brief: [`SPEC.md`](SPEC.md).

## Screens

| Screen | What it shows |
| --- | --- |
| **Dashboard** | Status pill (READY / `tree_id` / ledger head), health verdict, per-rail spend headroom, job states, live `/events?since_seq=` feed. Every card shows last-refreshed age. |
| **Command** | Text bar → `POST /api/v1/command`. Result shown. |
| **CREATE** | Maker map (Agent / Tool / Connector / Skill) from `GET /api/v1/makers`. |
| **Settings** | Server URL (LAN / Tailscale presets) persisted. Optional bearer **never written to disk**. |

A down server is named **offline** (8s connect timeout). Last good data stays on screen with its measured age. Cached `/api/*` is never treated as live.

## What it talks to

| Method | Path |
| --- | --- |
| GET | `/api/v1/status` |
| GET | `/api/v1/health` |
| GET | `/api/v1/spend` |
| GET | `/api/v1/jobs` |
| GET | `/api/v1/makers` |
| GET | `/api/v1/events?since_seq=N` |
| POST | `/api/v1/command` `{text}` |

The HTTP client is the VMC `CosmosClient` pattern (`reference/CosmosClient.kt`): `HttpURLConnection`, blocking, run on `Dispatchers.IO`.

Default URL is the Tailscale host `http://100.103.9.112:8791`. Leave the bearer blank when COSMOS is running `--no-auth`.

No secrets belong in this repo or the APK.

## Build

Requires JDK 17 and the Android SDK (compile/target 34, min 26).

```bat
gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions (`.github/workflows/android.yml`) runs JDK 17 + `android-actions/setup-android` + `./gradlew assembleDebug` and uploads that APK.

## Layout

```
app/src/main/java/com/cosmos/cdm/
  data/          CosmosClient, models, SettingsStore (URL only)
  ui/            Dashboard / Command / CREATE / Settings
reference/       VMC CosmosClient.kt (the pattern we reuse)
.github/workflows/android.yml
```

## License

Same owner as COSMOS (Keith). Internal deck.
