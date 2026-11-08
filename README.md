# Endurance Coach

A small, personal Android training app: Today, weekly Plan, Progress, recovery Insights and a Health diary. Garmin activities and wellness are imported using the **official Garmin Connect Personal API**. ChatGPT can review the private Google Sheets training database and append future workout revisions through its connected Google Drive tools. No in-app AI service or OpenAI API key is required.

Initial programme: Herdy's Frontyard Ultra, **12 March 2027**. Primary **10 yards / 67.06 km**, stretch **12 yards / 80.47 km**. Three weekly runs, optional fourth, plus strength and recovery. Future events are configured in the Events sheet.

## Current status

- Native Android app with five screens, dark/light/system appearance, encrypted connections, original-file archive, background/manual sync, feedback outbox and conservative automatic workout matching.
- Garmin client, token renewal, activity pagination, normalization and original FIT decoding covered by synthetic tests.
- Apps Script bridge and private Sheets ledger support idempotent data upload and append-only plan revisions.
- **Live activity, plan and recovery import are verified.** Version 0.1.1 retains recovery warnings and provides safe import diagnostics. The Google bridge is deployed, its live configuration check passed, and anonymous or incorrect-secret requests are rejected. No account credentials are included.
- The central running programme includes Tuesday/Thursday sessions and Sunday morning long runs with Emerald Striders; future changes remain editable in the private database.

## Build

Requires JDK 17, Android SDK platform 35 and build tools 35.0.0. Android Studio can install these. Set `ANDROID_HOME` or let Android Studio create the ignored `local.properties`.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
node --test backend/test/*.test.cjs
```

Install `app/build/outputs/apk/debug/app-debug.apk` on Android 8+. The first debug build is for personal testing. CI produces a downloadable debug APK without using Garmin credentials. Release signing must use your own secure signing key.

## Connect

1. Follow [setup](docs/SETUP.md) to deploy the Google Sheets bridge.
2. Generate a Personal API key in Garmin Connect web account settings; enter it under **Settings → Garmin & training database**.
3. Enter the deployed bridge `/exec` URL and its separate random secret in the same connection screen.
4. Open **Settings → Sync & import details → Sync Garmin + plan**. Check activity totals against Garmin Connect and then add a test feedback record.

Garmin imports now run when the app opens or returns to the foreground, while cached training stays visible. The plan and pending notes also refresh each minute while it remains open. Garmin imports remain available manually and approximately every six hours via Android JobScheduler as a background backup; Android can defer background work. Garmin data is available only after the watch syncs to Garmin Connect. This is not a live watch sensor connection.

The app opens in dark mode. Choose **Settings → Appearance** for Light or Follow phone. Daily screens show workouts, activity summaries and recovery measurements. From 0.3.2, **Workout details** shows the current session and feedback action; revision history remains in the database and is hidden from the app. From 0.3.1, sync status, warnings, queue counts, timestamps and controls live under **Settings → Sync & import details**, with no sync banner on daily tabs. The heading reads **ENDURANCE COACH**. Missing measurements remain blank. Native rendering tests use synthetic offline records and write previews to `app/build/ui-previews`.

Version 0.3.0 adds a selectable **12-week running-distance graph** to Progress, with distance, time and elevation for Monday–Sunday weeks in Perth. The current week is labelled "So far". Tap a point or use the chart's earlier/later accessibility actions. Unknown import periods are gaps; verified empty weeks are zero. An unavailable measurement stays blank rather than undercounting its week's total.

After updating, use **Settings → Sync & import details → Sync Garmin + plan** once. A paginated activity-list backfill supplies the 12-week chart history. Older summaries retain their original JSON, and any existing richer activity records are preserved. Recent activities continue to receive detailed JSON and FIT imports; older backfilled summaries do not automatically download FIT streams. Coverage is recorded only after every listed activity has been saved. New plan rows need only **Refresh plan** in the same Settings view, without another deployment.

Version 0.3.1 reduces repeat-sync requests: unchanged activity details can be reused for up to 24 hours when a successful detail and matching full list summary are held locally. Summary changes, expiry, invalid/missing cache entries or clock rollback trigger a fetch. Missing FIT files still retry. Upload responses already contain complete snapshots, so the app skips a redundant final fetch after uploads; a no-change refresh still fetches one snapshot. **Refresh plan** skips Garmin downloads and sends queued updates. Initial backfill, recovery requests and Apps Script/network latency remain; no live phone speed benchmark is claimed.

See [Garmin verification](docs/GARMIN.md), [chat update workflow](docs/CHAT_WORKFLOW.md), [data contract](docs/DATA.md) and [validation and limitations](docs/VALIDATION.md).

## Health and workout completion (0.5.0)

Health has Diary and Weight views. The Diary shows Coach-approved daily or baseline calorie targets, logged intake, remaining calories, progress and training type. Add/edit individual meals, choose breakfast/lunch/dinner/snacks, save favourites, add a portion directly or customise it, and copy recent meals. Earlier dates can be corrected; future dates show targets only. Weight includes daily entry, editable history, measured 30/90-day chart and trailing seven-day average with measurement count.

ChatGPT Coach calculates approved targets and writes them to `NutritionTargets` through writable Google Sheets tools. The app only reads those targets; it never adds Garmin exercise calories. Optional macro targets display and per-portion actual macro fields are preserved for future controls. `Health` actuals remain separate and immutable edits preserve history. Use the [Coach handover](docs/NUTRITION_COACH_HANDOVER.md) for exact fields and safe update instructions. Normal-conversation connector/model availability still applies; no Work session or in-app AI service is needed to update Sheet values.

Workout cards distinguish planned and actual minutes, including completed shorter/longer runs. Details let you mark past/today sessions completed, skipped or reset. Feedback can optionally mark completion atomically. Automatic matching and original Garmin records remain intact. Plan revision history stays in Sheets and is hidden in the app.

Health/completion changes persist offline. A fresh server capability check prevents the old bridge acknowledging newer Health fields it cannot store. Failed uploads remain queued. Invalid nutrition targets retain the last valid target cache and warn without blocking training and notes. Sync controls/status remain in Settings.

The normal package is `au.com.endurancecoach`, version0.5.2/code11, signed with the same privately backed-up replacement key as0.4.x. Install over0.4.x or later without uninstalling. Original0.3.x uses a lost older key; see [replacement checks](docs/INSTALL_REPLACEMENT.md). CI uses its own ephemeral signer and is not the phone update artifact.

The Sheet schema is migrated and read back. Existing Apps Script deployment version3 is live at the same URL and its0.5.0 version response and read-only setup check are verified. Physical phone roundtrip remains to be checked; see [validation status](docs/VALIDATION.md).

Post-run: save the activity on your watch, wait for Garmin Connect availability (Strava may receive its own copy), then open Endurance Coach and add feedback to the imported activity. Saved feedback queues immediately for the central database. Before discussing it in ChatGPT later, Settings → Sync & import details can confirm cloud sync and no pending changes. ChatGPT reads the connected database when asked; there is no automatic coaching conversation triggered by a run.
