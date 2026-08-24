# cDm — COSMOS Dashboard Mobile (Android)

**Owner:** Keith · **Builders:** Grok Bot (GBt) + Grok Build 4.6 (Cursor), collaborating, tested in GitHub CI.
The **mobile companion** to cDeck. Sibling to VMC (the voice app), on the **same COSMOS web service**.

## What it is
A **native Android app** that is the mobile monitoring/control deck for COSMOS — the phone-sized cDeck. It reads the same COSMOS HTTP API as VMC and cDeck. It is a DASHBOARD (glanceable panels + a command bar), distinct from VMC which is the hands-free VOICE app. They share the API and the same server; cDm may reuse VMC's API-client code (see the `cosmos-android` repo `CosmosClient.kt`).

## Tech (recommended, Grok may override)
- **Kotlin + Jetpack Compose** — same stack as VMC (`cosmos-android` repo) so code/patterns are shared. minSdk 26, targetSdk 34.
- CI: GitHub Actions `assembleDebug` → APK artifact (mirror the `cosmos-android` workflow: JDK 17, setup-android, `gradle wrapper` then `./gradlew assembleDebug`, upload the APK).

## COSMOS API (same as cDeck — read `cosmos` repo `cosmos/cosmos_service.py`)
Base `http://<host>:8791` (LAN or Tailscale `http://100.103.9.112:8791` over LTE). Bearer optional (`--no-auth` trial). GET status/audit/jobs/health/spend/rails/makers/events?since_seq; POST jobs/command.

## Screens (v1)
- **Dashboard**: status pill (READY/tree_id/ledger head), health verdict, spend headroom per rail, job states, and the live event feed (`/events?since_seq=` append-only cursor). Each card shows last-refreshed age.
- **Command**: a text command bar (POST /command) with the result shown.
- **CREATE**: the maker map (Agent/Tool/Connector/Skill) from `/makers`.
- **Settings**: server URL (LAN/Tailscale) + optional bearer, persisted (in-memory or SharedPreferences; do NOT persist the bearer to disk).
- Phone-first, big touch targets, dark theme matching VMC, no horizontal scroll.

## Non-negotiables
- Never treat cached `/api/*` as live (show age — frozen-dashboard scar). · No secrets in repo/APK. · Graceful offline (say "offline", don't hang). · Self-contained (no CDNs baked into any embedded web view).

## Relationship to VMC
VMC = voice (Vosk in + bundled TTS out + the /voice conversational seam). cDm = visual dashboard. Keep them SEPARATE apps sharing the API; do not merge. (If a shared Kotlin module for the API client makes sense, propose it.)

## Collaboration
GBt + Grok 4.6 split the work, review each other's PRs, converge on `main`, keep CI green, open issues for decisions.
