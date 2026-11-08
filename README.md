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

The plan checks for updates when the app opens and every minute while it remains open. Garmin imports run manually and approximately every six hours via Android JobScheduler; Android can defer background work. Garmin data is available only after the watch syncs to Garmin Connect. This is not a live watch sensor connection.

The app opens in dark mode. Choose **Settings → Appearance** for Light or Follow phone. Daily screens show workouts, activity summaries and recovery measurements. From 0.3.2, **Workout details** shows the current session and feedback action; revision history remains in the database and is hidden from the app. From 0.3.1, sync status, warnings, queue counts, timestamps and controls live under **Settings → Sync & import details**, with no sync banner on daily tabs. The heading reads **ENDURANCE COACH**. Missing measurements remain blank. Native rendering tests use synthetic offline records and write previews to `app/build/ui-previews`.

Version 0.3.0 adds a selectable **12-week running-distance graph** to Progress, with distance, time and elevation for Monday–Sunday weeks in Perth. The current week is labelled "So far". Tap a point or use the chart's earlier/later accessibility actions. Unknown import periods are gaps; verified empty weeks are zero. An unavailable measurement stays blank rather than undercounting its week's total.

After updating, use **Settings → Sync & import details → Sync Garmin + plan** once. A paginated activity-list backfill supplies the 12-week chart history. Older summaries retain their original JSON, and any existing richer activity records are preserved. Recent activities continue to receive detailed JSON and FIT imports; older backfilled summaries do not automatically download FIT streams. Coverage is recorded only after every listed activity has been saved. New plan rows need only **Refresh plan** in the same Settings view, without another deployment.

Version 0.3.1 reduces repeat-sync requests: unchanged activity details can be reused for up to 24 hours when a successful detail and matching full list summary are held locally. Summary changes, expiry, invalid/missing cache entries or clock rollback trigger a fetch. Missing FIT files still retry. Upload responses already contain complete snapshots, so the app skips a redundant final fetch after uploads; a no-change refresh still fetches one snapshot. **Refresh plan** skips Garmin downloads and sends queued updates. Initial backfill, recovery requests and Apps Script/network latency remain; no live phone speed benchmark is claimed.

See [Garmin verification](docs/GARMIN.md), [chat update workflow](docs/CHAT_WORKFLOW.md), [data contract](docs/DATA.md) and [validation and limitations](docs/VALIDATION.md).

## Health (0.4.0)

The fifth tab logs individual meals, calories per portion and adjustable portions. Save favourites, add one portion with one tap, or choose Options to adjust portions. Favourite edits do not change past meals. Meals, favourites and weights can be edited or removed; choose the date at the top for earlier entries. Weight has an optional goal, 30-day measured chart and seven-day measured average. Logged calories have a 14-day chart and seven-day average over logged days only; missing entries stay blank. Calories are entered manually; there is no food database, automatic estimate or prescribed deficit.

All Health changes persist locally and queue offline. A dedicated immutable `Health` change ledger preserves history and tombstones. Cloud sync requires deploying the updated `backend/Code.gs` to the existing bridge URL; against the previous bridge, Health stays queued and Settings explains the required upgrade. Acknowledgements remove only explicitly accepted changes. Existing training clients remain supported. See [Health contract](docs/HEALTH.md).

The APK now uses the normal package `au.com.endurancecoach` and label **Endurance Coach**, version 0.4.0 (7), as requested. The original 0.3.x signing key was lost during workspace reset and could not be recovered from saved source/install archives. The replacement is signed with the backed-up new key, so Android will reject an in-place update over 0.3.x. Do not uninstall until the [replacement checks](docs/INSTALL_REPLACEMENT.md) are complete. Credentials and offline-only data do not automatically migrate. Later builds can update this replacement when signed with the same backed-up key; CI uses a different temporary certificate and its APK must not be offered as an update. Never commit keys or secrets. Health bridge deployment remains pending renewed Google authentication; no live Health write has been claimed.
