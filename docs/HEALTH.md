# Health and nutrition data

Diet Quality appears inside Diary with an estimated 0–100 score, provisional or
complete status, confidence, assessment time, one next step and an expandable
five-part explanation. Coach owns the separate `DietQuality` A:W revision ledger;
Android never scores food or invokes AI. See DIET_QUALITY_COACH_HANDOVER.md.
Weights are 30/20/20/15/15, rubric dq-v1. Food change IDs fingerprint the current
active meals: edits, deletions and moves immediately flag an assessment for review.
The 7/30-day graph has a fixed 0–100 scale and missing-day gaps; today's provisional
point is hollow. Only complete, matching assessments enter the trailing 7-day
average, with a day count. Invalid/absent assessment data retains the last good
cache and warns without blocking existing sync. An empty valid ledger clears it.

Coach-approved targets and phone-recorded actuals are separate. `NutritionTargets` is the source of truth for calorie/macro/weight-management targets. The app never calculates calorie requirements, changes approved targets, or adds Garmin exercise calories. See [NUTRITION_COACH_HANDOVER.md](NUTRITION_COACH_HANDOVER.md) for exact headers, types, revision selection and writer protocol.

`Health` is an append-only actuals ledger. Existing A:K fields are unchanged; meal_type and nullable per-portion protein/carbohydrate/fat grams are appended at L:O. Kinds remain meal/favourite/weight/legacy goal. Legacy meal groups become Other and missing grams remain null. Meal calories and grams are per portion. Favourite and recent copies are independent meal records. Macro targets display now; macro entry controls are not included yet, but edits/copies preserve stored grams.

Diary offers date selection, logged calories/target/remaining/progress, training type, breakfast/lunch/dinner/snacks/other, multiple editable meals, favourites (one portion or custom portions), recent copies, and historical correction. Future dates display targets but disable actual logging. Missing intake is unknown; an explicitly logged zero-calorie meal is zero. Calories remaining can be negative after exceeding a target. No exercise adjustment occurs.

Weight offers quick daily entry, latest weight, date-specific history/correction/removal, 30/90-day measured charts, and trailing seven-day average with the number of measured days. Missing days are not filled with zero. One current measurement per Perth date, with edit history retained. Weight goals are read-only from approved Coach targets in `NutritionTargets`; legacy Health-record goals are ignored.

Each Health edit/deletion has a new UUID change_id, stable record_id and UTC millisecond timestamp. Current state is maximum parsed timestamp, then lexical change_id. Deleted records remain available so stale deliveries cannot resurrect entries. Same ID/same payload retries are idempotent; conflicting payloads fail. Weight IDs are weight:YYYY-MM-DD; legacy goal ID goal-weight.

`WorkoutStatus` is a separate append-only completion ledger. Completed/skipped/reset statuses never rewrite Plan or original Garmin activity records. Matched activity duration can be shorter/longer than planned and still count completed. Unknown duration stays unknown. Manual status overrides automatic matching; reset returns to Garmin evidence. Feedback can optionally mark completion in the same local transaction.

App uploads batches of30; server maximum100. Health and completion queues clear only on explicit acknowledgements from a supporting bridge. An older bridge leaves queued changes on the phone. Actual state and immutable changes save atomically in SQLite; snapshots and acknowledgements likewise. Nutrition snapshots replace only the validated target cache. Missing/malformed/conflicting targets retain last valid cache, warn the user, and do not prevent training or feedback sync. A valid empty ledger clears targets. Target changes never clear Health or workout queues.

Sync automatically starts on app-open; Settings → Sync & import details contains manual refresh, status and pending counts. Background sync remains periodic. Network/import failures keep durable data for retry. Changes in Sheets appear at the next successful cloud sync without a new app deployment.

Deployment: Health A:O, NutritionTargets A:Q, WorkoutStatus A:F and Events A:P headers have been added and read back in the private spreadsheet. The existing Apps Script web-app deployment is version4 with the same URL/access/Script Properties. Public GET returns only bridge_version0.6.0 and an authenticated-POST instruction; it exposes no records. The read-only Coach setup check passed. Authenticated phone sync is still needed for end-to-end verification. Do not read or copy secrets into source/chat/GitHub. Consult VALIDATION.md for evidence.

Normal package au.com.endurancecoach, release0.6.0/code12, uses the same privately backed-up replacement signer as0.4.x and updates those installations in place. See INSTALL_REPLACEMENT.md for the older0.3.x certificate limitation. Do not uninstall an app containing unsynced data.
