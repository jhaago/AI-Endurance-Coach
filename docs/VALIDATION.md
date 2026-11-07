# Validation — 7 November 2026

## Executed

- `:core:test`: **33 tests passing**, including authentication/raw JWT/401 renewal, pagination, SI units, official response schema differences, original FIT decoding, null wellness, plan revisions, Perth matching, manual conflicts, event filtering, catch-up windows, bridge plan-refresh/actual separation, and multi-megabyte Unicode-safe storage chunks.
- `node --test backend/test/*.test.cjs`: **8 tests passing** against an in-memory Apps Script/Sheets/Drive harness, exercising authentication, validation-before-write, activity/archive idempotency, feedback retry/conflicts/formula escaping, revision snapshots and null metrics.
- `:app:assembleDebug`: Android debug APK successfully produced.
- `:app:lintDebug`: **0 errors, 5 warnings** (English-only strings/localisation and custom View editor-constructor guidance).
- `scripts/check_android_json.py`: compiled core JSON calls checked against Android API 35's real `android.jar`. This caught and eliminated a desktop-only `JSONObject.keySet()` call.
- Independent code review completed. Its six important findings were addressed: Android JSON compatibility, bounded SQLite rows, checked persistence failures, offline catch-up, active-event display and conflicting manual assignments.
- Private native Sheets import/readback verified event targets, ordered schema, ISO text timestamps and Perth timezone. Recovery check-in is the only initial plan item; no personal training history was fabricated.

## Not yet verified

- The first physical-device check confirmed Today/Insights rendering, saved connections, cloud plan retrieval and a live import of 14 activities. Android SQLite failure injection, upgrade persistence and JobScheduler timing still require device acceptance testing.
- Live Garmin activity import is confirmed. Sleep/HRV/resting HR did not appear in the first build, and plan polling hid earlier warnings. Version 0.1.1 preserves Garmin warnings, uses documented same-date sleep/HRV summary fallbacks, and uploads credential-safe HTTP/shape diagnostics with null metrics. Real recovery availability remains unverified until the updated APK syncs.
- Google OAuth is approved and the live Apps Script `verifySetup` check passed: database/schema readable, one event and one plan revision, private archive accessible, secret present and valid length without displaying it. The web app is deployed as version 1; `Anyone` endpoint access was explicitly approved and all data operations require the bridge secret. Live unauthenticated GET returns `Use authenticated POST`, and an incorrect-secret POST returns `Unauthorized`; no data is returned. Authenticated Android-to-bridge activity uploads are verified. Recovery uploads from 0.1.1 remain a device check. Node tests use service doubles.
- Pushing structured workouts to Garmin is not implemented by this read-only Personal API.
- Original FIT files remain on the phone; cloud archives currently retain original JSON. Additional Garmin recovery endpoints, multi-user authentication, release signing and Play Store publication remain future work.

## Review decisions

- Use the official Personal API for this single-user project; a commercial integration is unnecessary for the current scope.
- Keep programme changes under chat review, rather than mutating training automatically from one metric. The app displays evidence and records feedback.
- Append-only Sheets plan history is enforced by app/bridge write separation and the documented chat workflow; an owner can still edit historical rows directly in Sheets.
- Keep ambiguous matches unassigned, and only show the active event's current schedule while preserving historical matching.
- Defer the five lint warnings, full localisation and custom-view layout-editor support. They do not prevent this personal English-language APK from building.

## Version 0.1.1 recovery repair

- Added regression coverage for no-data vs HTTP errors, same-date summary selection, ambiguous daily-minimum HR, empty/partial sleep payloads, retained local/cloud metrics, and warning persistence.
- A summary resting/minimum value is retained only in original JSON and is never labelled confirmed resting HR. Null diagnostic rows cannot clear measured cloud wellness values.
- Independent review findings were fixed with regression tests. The update uses the same package and signing certificate as 0.1.0; install as an update to retain saved settings and local records.
