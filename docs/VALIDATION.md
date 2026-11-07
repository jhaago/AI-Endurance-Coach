# Validation — 7 November 2026

## Executed

- `:core:test`: **41 tests passing**, including authentication/raw JWT/401 renewal, pagination, SI units, official response schema differences, original FIT decoding, null wellness, plan revisions, Perth matching, manual conflicts, event filtering, catch-up windows, bridge plan-refresh/actual separation, multi-megabyte Unicode-safe storage chunks and weekly running aggregation.
- `node --test backend/test/*.test.cjs`: **8 tests passing** against an in-memory Apps Script/Sheets/Drive harness, exercising authentication, validation-before-write, activity/archive idempotency, feedback retry/conflicts/formula escaping, revision snapshots and null metrics.
- `:app:assembleDebug`: Android debug APK successfully produced.
- `:app:lintDebug`: **0 errors, 11 warnings** in 0.3.0 (English-only text, small chart draw allocations, custom View editor-constructor guidance, synchronous credential removal and backup configuration guidance).
- `scripts/check_android_json.py`: compiled core JSON calls checked against Android API 35's real `android.jar`. This caught and eliminated a desktop-only `JSONObject.keySet()` call.
- Independent code review completed. Its six important findings were addressed: Android JSON compatibility, bounded SQLite rows, checked persistence failures, offline catch-up, active-event display and conflicting manual assignments.
- Private native Sheets import/readback verified event targets, ordered schema, ISO text timestamps and Perth timezone. Recovery check-in is the only initial plan item; no personal training history was fabricated.

## Not yet verified

- The first physical-device check confirmed Today/Insights rendering, saved connections, cloud plan retrieval and a live import of 14 activities. Android SQLite failure injection, upgrade persistence and JobScheduler timing still require device acceptance testing.
- Live Garmin activity import is confirmed. Sleep/HRV/resting HR did not appear in the first build, and plan polling hid earlier warnings. Version 0.1.1 preserves Garmin warnings, uses documented same-date sleep/HRV summary fallbacks, and uploads credential-safe HTTP/shape diagnostics with null metrics. The updated APK has now uploaded recovery records for 1–7 November: five dates have sleep, HRV and resting HR; two have partial measurements returned by Garmin.
- Google OAuth is approved and the live Apps Script `verifySetup` check passed: database/schema readable, one event and one plan revision, private archive accessible, secret present and valid length without displaying it. The web app is deployed as version 2; `Anyone` endpoint access was explicitly approved and all data operations require the bridge secret. Live unauthenticated GET returns `Use authenticated POST`, and an incorrect-secret POST returns `Unauthorized`; no data is returned. Authenticated Android-to-bridge activity uploads are verified. Recovery uploads from 0.1.1 are verified. Node tests use service doubles.
- Pushing structured workouts to Garmin is not implemented by this read-only Personal API.
- Original FIT files remain on the phone; cloud archives currently retain original JSON. Additional Garmin recovery endpoints, multi-user authentication, release signing and Play Store publication remain future work.

## Review decisions

- Use the official Personal API for this single-user project; a commercial integration is unnecessary for the current scope.
- Keep programme changes under chat review, rather than mutating training automatically from one metric. The app displays evidence and records feedback.
- Append-only Sheets plan history is enforced by app/bridge write separation and the documented chat workflow; an owner can still edit historical rows directly in Sheets.
- Keep ambiguous matches unassigned, and only show the active event's current schedule while preserving historical matching.
- Defer the localisation/custom-view tooling warnings and existing preference/backup guidance. They do not prevent this personal English-language APK from building.

## Version 0.1.1 recovery repair

- Added regression coverage for no-data vs HTTP errors, same-date summary selection, ambiguous daily-minimum HR, empty/partial sleep payloads, retained local/cloud metrics, and warning persistence.
- A summary resting/minimum value is retained only in original JSON and is never labelled confirmed resting HR. Null diagnostic rows cannot clear measured cloud wellness values.
- Independent review findings were fixed with regression tests. The update uses the same package and signing certificate as 0.1.0; install as an update to retain saved settings and local records.

Live follow-up: Wellness calendar cells were normalised to ISO text, and Wellness!A2:A1000 is formatted as text to prevent native date coercion from changing upsert keys. Values and diagnostics were read back. A second future Recovery check-in revision was appended through the chat-connected Sheets tools; revision 1 remains unchanged. App display of revision 2 will be confirmed on its next plan refresh.

## Version 0.2.0 interface refresh

- Dark default, selectable Light and Follow phone, matching native dialogs/inputs/charts/system bars, rounded controls and accessible icon navigation. No new runtime dependencies; Robolectric/AndroidX dependencies are test-only.
- Today prioritises the next workout and a daily check-in. Plan and Progress retain optional/completed/matching states; unavailable activity duration stays blank. Insights presents dated recovery tiles and measured-only trends, including gaps and empty states.
- Revision explanations moved to workout details; timestamps, credential-safe import diagnostics and data coverage notes moved to Settings. Current warnings remain visible and tapping the status opens its full current message, including an unsaved database error.
- **8 offline Android UI tests** exercise both themes across all four tabs, appearance-change data/outbox preservation, system day/night selection, narrow screens with 150% text, diagnostic relocation, null activity duration, daily check-in availability for a future recovery workout, and current error details. These use synthetic data and real native-graphics rendering through Robolectric; screenshots are under `app/build/ui-previews`.
- Independent review found three regressions; each was reproduced by a failing UI test and fixed. Core (33) and bridge (8) tests also pass. Physical-device installation, Android system-bar rendering and a real upgrade with existing encrypted connections remain device acceptance checks. The package ID and debug signing certificate are unchanged.

## Version 0.3.0 weekly progress

- **41 core + 20 Android UI + 8 bridge tests pass (69 total).** UI tests run on API 28 and 35 with native graphics, in dark/light themes and with a narrow-screen/large-text case. Graph tests select earlier/later weeks through accessibility actions and render synthetic 12-week previews. Plan data remains separate from actual activity totals.
- Core tests verify Perth Monday boundaries, run/trail/treadmill inclusion, walk exclusion, duplicate suppression, current-week coverage, verified zeros versus unknown imports, missing metric totals, initial backfill and long offline catch-up.
- A paginated history backfill imports older list summaries once; their JSON is retained and richer local/cloud records remain intact. Recent detail/FIT import continues. Coverage is saved after the entire activity list is persisted; an incomplete wellness import cannot clear valid activity coverage.
- Independent review found no new important/critical issues. Preview inspection also found navigation single-line layouts using an unnecessarily wide scrolling layout; constrained single-line layouts now render the labels, with a reproducing test.
- Debug APK build, Android JSON compatibility and same-signer verification pass. Physical-device upgrade and authenticated 12-week backfill require the athlete's next phone install/sync; credentials are not available in the development environment.
