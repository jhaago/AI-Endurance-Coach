# Data contract (v1)

All training dates are ISO `YYYY-MM-DD` in Australia/Perth; actual timestamps are UTC ISO strings ending in `Z`. Keep date/timestamp columns as text in Sheets. Missing metrics are empty cells/null, not zero. Units: metres, seconds, seconds/km, bpm, steps/minute, HRV milliseconds, sleep hours.

The exact ordered headers live in `backend/Code.gs: HEADERS`. The bridge checks them before access.

| Tab | Ownership / meaning |
|---|---|
| Events | Event settings, primary/stretch targets, preferred run count, optional fourth, active flag, and current Coach-reviewed outlook |
| Plan | Append-only ledger. Stable `workout_id`, integer `revision` ≥1; latest revision wins, `cancelled` removes the workout from the current schedule |
| Activities | Garmin actuals upserted by `activity_id`; import never edits Plan |
| Wellness | Per-day measured sleep/HRV/resting HR with source hash and coverage note |
| Feedback | Immutable `feedback_id`; session or daily check-in, effort/soreness/fatigue 0–10, optional note |
| Matches | Manual override per activity; empty workout ID explicitly means leave unmatched |
| NutritionTargets | Coach-owned append-only effective baselines and date-specific targets; app read-only |
| Health | Actual meals/favourites/weight ledger, independent from targets |
| WorkoutStatus | Completion/skip/reset ledger, independent from Plan and Garmin records |
| Archive | Content-addressed original JSON file references in a private Drive folder |

`Events` columns L:P hold the current outlook for the active event: `primary_confidence_pct`, `stretch_confidence_pct` (numbers 0–100, interpreted as estimated chances of reaching each yard target), `confidence_as_of` (Perth `YYYY-MM-DD`), `confidence_quality` (`low`, `medium` or `high`), and `confidence_reason` (short evidence summary). Leave all five cells blank until the Coach reviews current training. The normal Coach conversation updates these fields directly; the app only displays them. Only the latest assessment is stored in the row; Google Sheets version history is available for recovery.

Workout types used by the app are `run`, `strength`, `recovery`. Other types display but do not automatically match. A `scheduled` optional run still appears with an optional label. Plan revision metadata records author, creation time and reason. Never edit a historical row to revise a workout; append a higher revision. Two different rows with the same workout/revision are rejected by the app.

Automatic matching is a derived relationship: same Perth day + compatible activity type, only when each side has a unique candidate. Multiple same-day runs or workouts remain unmatched. Manual overrides take priority; duplicate manual assignment of two activities to one workout remains unresolved. Original activity JSON is never mutated to create a match. Changing a match does not change source data. Matches tab stores overrides; automatic relationships can be recalculated from Plan and Activities by ChatGPT.

SQLite stores the last accepted cloud snapshot and local Garmin records separately. Feedback and match changes go into a persistent outbox. A snapshot and its acknowledgement are saved in one local transaction; feedback is removed only for explicitly acknowledged IDs. A concurrently updated match is removed only if its exact sent JSON still matches. A script-wide lock serialises phone writes; the phone never writes Plan. Chat writers must use append requests instead of replacing the ledger.

Every original activity list/detail response is saved locally with a content hash and uploaded as JSON to the private archive folder when the bridge is connected. Original wellness responses are similarly archived. FIT ZIP files and decoded records are retained on the phone; FIT originals are not yet backed up to Drive. Android system backup is disabled to prevent credential/health-data leakage. App uninstall removes phone-local FIT files and cached data; cloud JSON history remains in Drive.

Snapshot currently returns the latest 500 activities and 60 wellness days, all plan revisions and feedback. The first Garmin import covers 30 activity days and 7 wellness days; subsequent syncs start from the last successful import with 7 activity days and 3 wellness days of overlap, catching up after longer offline gaps. Transient wellness failures retain retry dates. This is a personal MVP rather than an unlimited historical archive browser.

Recovery diagnostic rows may have a date and coverage note while every measurement is null. They are import attempts, not measured recovery observations. The HTTP status and response shape include no keys, tokens or error bodies. Same-day unavailable fields retain earlier local/cloud measurements and are explicitly labelled retained. Summary `restingValue` is not accepted as measured resting HR because Garmin may substitute the daily minimum.


Nutrition/Health/WorkoutStatus exact extended contracts and migration rules: [Health](HEALTH.md), [Coach handover](NUTRITION_COACH_HANDOVER.md). A fresh schema2 capability preflight runs before Health uploads; old acknowledgement-capable servers must not receive extended events. Nutrition validation errors do not roll back unrelated acknowledgements.
