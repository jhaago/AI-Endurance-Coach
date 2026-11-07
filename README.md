# Endurance Coach

A small, personal Android training app: Today, weekly Plan, Progress and recovery Insights. Garmin activities and wellness are imported using the **official Garmin Connect Personal API**. ChatGPT can review the private Google Sheets training database and append future workout revisions through its connected Google Drive tools. No in-app AI service or OpenAI API key is required.

Initial programme: Herdy's Frontyard Ultra, **12 March 2027**. Primary **10 yards / 67.06 km**, stretch **12 yards / 80.47 km**. Three weekly runs, optional fourth, plus strength and recovery. Future events are configured in the Events sheet.

## Current status

- Native Android app with four screens, dark/light/system appearance, encrypted connections, original-file archive, background/manual sync, feedback outbox and conservative automatic workout matching.
- Garmin client, token renewal, activity pagination, normalization and original FIT decoding covered by synthetic tests.
- Apps Script bridge and private Sheets ledger support idempotent data upload and append-only plan revisions.
- **Live activity, plan and recovery import are verified.** Version 0.1.1 retains recovery warnings and provides safe import diagnostics. The Google bridge is deployed, its live configuration check passed, and anonymous or incorrect-secret requests are rejected. No account credentials are included.
- The initial central plan contains a recovery check-in. A full running programme should be set after importing current training and recovery data.

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
4. Tap **Sync Garmin + plan**. Check activity totals against Garmin Connect and then add a test feedback record.

The plan checks for updates when the app opens and every minute while it remains open. Garmin imports run manually and approximately every six hours via Android JobScheduler; Android can defer background work. Garmin data is available only after the watch syncs to Garmin Connect. This is not a live watch sensor connection.

Version 0.2.0 opens in dark mode. Choose **Settings → Appearance** for Light or Follow phone. Daily screens show workouts, activity summaries and recovery measurements; revision explanations are in **Workout details**, and sync timestamps/HTTP diagnostics are in **Settings → Sync & import details**. Tap the status message to open those details directly. Missing measurements remain blank and current warnings remain visible. The native rendering tests use synthetic offline records and write previews to `app/build/ui-previews`.

Version 0.3.0 adds a selectable **12-week running-distance graph** to Progress, with distance, time and elevation for Monday–Sunday weeks in Perth. The current week is labelled "So far". Tap a point or use the chart's earlier/later accessibility actions. Unknown import periods are gaps; verified empty weeks are zero. An unavailable measurement stays blank rather than undercounting its week's total.

After updating, tap **Sync Garmin + plan** once. A paginated activity-list backfill supplies the 12-week chart history. Older summaries retain their original JSON, and any existing richer activity records are preserved. Recent activities continue to receive detailed JSON and FIT imports; older backfilled summaries do not automatically download FIT streams. Coverage is recorded only after every listed activity has been saved. New plan rows in the shared database need only Refresh plan, without another deployment.

See [Garmin verification](docs/GARMIN.md), [chat update workflow](docs/CHAT_WORKFLOW.md), [data contract](docs/DATA.md) and [validation and limitations](docs/VALIDATION.md).
