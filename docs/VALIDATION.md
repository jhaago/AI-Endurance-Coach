# Validation — 7 November 2026

## Executed

- `:core:test`: **45 tests passing**, including authentication/raw JWT/401 renewal, pagination, SI units, official response schema differences, original FIT decoding, null wellness, plan revisions, Perth matching, manual conflicts, event filtering, catch-up windows, bridge plan-refresh/actual separation, multi-megabyte Unicode-safe storage chunks and weekly running aggregation.
- `node --test backend/test/*.test.cjs`: **8 tests passing** against an in-memory Apps Script/Sheets/Drive harness, exercising authentication, validation-before-write, activity/archive idempotency, feedback retry/conflicts/formula escaping, revision snapshots and null metrics.
- `:app:assembleDebug`: Android debug APK successfully produced.
- `:app:lintDebug`: **0 errors, 13 warnings** in 0.3.1 (English-only text, small chart draw allocations, custom View editor-constructor guidance, synchronous credential removal and backup configuration guidance).
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


## Version 0.3.1 header and sync cleanup

- Banner now reads ENDURANCE COACH. Sync status, warnings, pending-check-in counts, timestamps and manual controls are in Settings only. Today, Plan, Progress and Insights omit the sync header, and Today omits the full-sync button. The Settings dialog updates its current message and timestamps as sync runs. Refresh plan in Settings skips Garmin downloads while sending queued changes.
- Unchanged successful activity details may be reused for less than 24 hours. Full list-summary comparison ignores object key order but preserves all values/array order. Changed, absent, empty, expired or future-clock entries refetch. Cache timestamps are saved after the activity record; missing FIT streams still retry. This does not bypass wellness imports or the first history backfill.
- Removed the redundant snapshot-only request after uploads that already return full post-write snapshots. Tests verify one activity batch uses one request instead of two, activity+feedback uses two instead of three, and an idle refresh still fetches one snapshot. Failed requests preserve queued feedback and do not stamp upload hashes.
- **45 core + 24 Android tests + 8 bridge tests pass (77 total)**. UI tests cover both API 28 and 35; four repository exchange tests cover actual SQLite acknowledgements and request counts. Native Today/large-text previews were inspected. Lint has zero errors; JSON compatibility and the existing debug signer are verified.
- Independent review found leftover Plan/error retry controls; those were moved/removed with a failing then passing settings-only UI test. No important cache or acknowledgement issue remained. Live phone timing is not measured: Apps Script latency, initial import and recovery requests can still be slow, and the detail cache needs its first successful fill.

## Version 0.3.2 workout detail cleanup

- Workout details shows the current date, type, duration, instructions and feedback action. Previous revisions, change reasons and administrative notes are hidden from the app; the database and sync retain the complete revision ledger.
- Regression coverage uses two stored versions, verifies only the current instructions appear, confirms the feedback action remains, and reads back both stored versions. It failed before the UI removal and passes on API 28/35 afterward.
- **45 core + 26 Android + 8 bridge tests pass (79 total)**. Debug build, lint (zero errors), Android JSON compatibility and the same signing certificate are verified. Focused review found no important issues. The proposed Health tab remains a design discussion and is not included in this release.

## Version 0.4.0 Health preview

52 core + 38 Android + 21 bridge tests pass (111 total). Native UI checks cover API28/35, meal portions, favourite quick-add, invalid input, weights, goal removal and narrow 320dp navigation. Repository tests cover SQLite restart, offline outbox, explicit acknowledgements, old bridge and stale snapshot deletion. Clean APK build, lint (0 errors, 14 warnings) and Android framework JSON compatibility pass. APK identity: au.com.endurancecoach.preview, version0.4.0/code7. The prior signing key is unavailable; this separate package preserves the old installation. Independent review found a stale weight/goal dialog after removal; a failing regression test now passes with dialog dismissal. No remaining important review findings. Dark preview inspected; automated light/native checks pass, but the image viewer could not display the valid generated light PNG. Live Health sync awaits renewed Google authentication and bridge deployment.

## Normal package rebuild

At the user's request, removed the preview suffix and label override. Clean build, 38 Android tests on APIs28/35, and lint pass (0 errors). APK package is au.com.endurancecoach, version0.4.0/code7, label Endurance Coach. It uses the backed-up replacement key, certificate SHA256 b8e828b3237e7e7fd31ca143d8f66aa9eee705e5529d585a55e784e0fb0de275. Original0.3.2 certificate e43c1319587aebb78462eb4c091f7d6b02bf28674ee6188e557561d6dd2d4bb1 differs. Saved source and all six prior install archives contain no signing keystore. This APK requires a user-controlled replacement; it cannot update the old installation in place. No phone data was changed. Health deployment still pending. Remote CI rerun19 fixed SDK setup but stopped on MavenHTTP429 while downloading Robolectric native runtime, before Android tests could run; local verification remains green.

## Version0.4.1 post-run app-open sync

57 core +44 Android +21 bridge tests pass (122 total). Clean APK build, lint (0errors,14warnings), frameworkJSON compatibility, package au.com.endurancecoach/version0.4.1/code8 and the same0.4.0 replacement signing certificate are verified. New tests exercise request coalescing, Garmin priority, new-run import/upload, cloud-only note upload, Garmin503 with successful note acknowledgement, two repositories contending on an import, actual Activity resume, and Activity destruction with a note queued after the first upload batch was captured. The destroyed Activity regression failed before the ownership fix and now confirms upload, database closure and executor termination. Independent review's important lifecycle finding is fixed; no remaining important findings. Already-requested work finishes after Back/rotation while the process survives; Android process death leaves the durable outbox for a later sync. No live Garmin speed benchmark or phone installation was performed. Health bridge deployment remains pending.

## Version0.5.0 Coach-managed nutrition and completion

2026-11-08: clean Android build completed successfully. **73core+74Android+42bridge tests pass(189total)**; lint0errors/15warnings, Android frameworkJSON compatibility passes. UI behavior covers API28/35, including future-target views, disabled future actual entry, readonly targets, meal groups/favourites/recent copies, edited historical weights, completed-shorter/longer, manual skip/reset, and optional atomic feedback completion. SQLite tests cover restart, cached target retention on missing/invalid/error responses, validempty cacheclear, exact actual-record preservation across target revisions, explicit completion acknowledgements, oldserver queue retention and a fresh Health schema preflight before sending extended fields. Existing Garmin/open-sync/lifecycle tests continue to pass.

Independent review reproduced oldbridge acknowledgements silently dropping new Health fields; a RED then GREEN regression now confirms no Health payload is sent until a fresh snapshot advertises health_schema_version2. Review also found repeated matching per historical Plan card; matching/manual outcomes now compute once per render. Re-review found no remaining important findings in those paths. No live performance benchmark is claimed.

APK identity verified: au.com.endurancecoach, version0.5.0/code9, minSDK26. CertificateSHA256 b8e828b3237e7e7fd31ca143d8f66aa9eee705e5529d585a55e784e0fb0de275 matches the privately backed-up0.4.x replacement signer. APK SHA2566798bd91e844f6aaaae57260c4ae59b649c6362ff48401313a6c3abba32ea3d6. Install over0.4.x in place; older0.3.x certificate limitation remains documented.

Live spreadsheet inspected: Australia/Perth timezone; Health A:K existing ordered headers preserved and L:O appended; NutritionTargets A:Q and WorkoutStatus A:F added with header-only rows. Exact headers read back successfully. No real targets, meals, weights or completion rows were seeded. Google Sheets connector writes and reads succeeded. Backend behavior is tested in an Apps Script-compatible synthetic harness, not a live authenticated phone roundtrip.

**Live deployment verified:** renewed Google sign-in succeeded. Existing editor source inspected (seven original ledgers,9582characters) and backed up privately. Pasted source exactly matched tested backend/Code.gs(SHA2560cbae261a7e125ad728d9680cc735fe1bb268e93707208fe99dc0afd9ed2aec9). Live verifyCoachSetup completed with ok:true,nutrition_targets_ready:true,zero Health/completion/target counts. Existing web-app deployment updated fromversion2 toversion3 on8Nov2026,12:46Perth; same deploymentID/execURL/execute-as/access settings retained. Its public GET returned {ok:false,error:"Use authenticated POST",bridge_version:"0.5.0"}. This verifies deployment identity and unauthenticated read rejection, not an authenticated physical-phone roundtrip. No credentials were read by the model, exposed or changed. Deployment confirmation captured.

Remaining checks: install on phone without uninstalling; Settings→Sync & import details→Refresh plan; user-entered meal/weight upload, edit and retry; approved future target written by normalCoach tools and read back after phone refresh; preserve prior actuals. Normal-chat Sheets write capability/account limits are external to this app. Macronutrient targets display and actual macro fields persist, but manual macro entry UI and estimated Garmin energy import are not included. There is no automatic coach invocation after a run or nutritional recalculation triggered inside the app.

## Version 0.5.1 Health date navigation

The Health Diary now uses a compact previous-day / selected-date / next-day control. The selected date opens the calendar picker, and selecting a date still updates meal logging and date-specific targets. The Weight view no longer shows the date selector; historical measurements remain editable from the weight history. Android package version is 0.5.1/code10, using the existing replacement signer. This UI-only update does not require an Apps Script deployment. No test suite was run for this small layout change; the APK build and signing identity are verified separately.
