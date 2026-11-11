# Diet Quality in Health → Diary

Approved in the Running Coach conversation on 11 November 2026. Build on
feat/health-meals-preview and preserve existing food, target and training data.

Show an estimated 0–100 score, provisional while the day is incomplete, with
tap-to-expand component points/reasons, confidence, assessment time, strength
and one next step. The Coach assesses choices so far; Android displays saved
assessments and never calls AI or changes calories/targets. This is a custom
coaching convention, not a validated clinical score.

Weights: produce30, fibre20, protein/calcium20, fat quality15, discretionary15.
Keep calorie adherence and logging completeness separate. Treat running fuel
in context and acknowledge uncertain ingredients/photos.

Use a separate optional DietQuality worksheet, append-only per-date revision
ledger. Highest revision wins; withdrawn hides an assessment; conflicting
duplicates reject import. Snapshot errors retain the last valid cache without
blocking other sync. A valid empty ledger clears it. Nonempty snapshots must
extend cached immutable history, preserving withdrawals and conflict detection.

food_change_ids is the sorted active meal change IDs joined with |, excluding
deleted entries, superseded revisions and favourites. Changes mark the score
as needing review and exclude it from trends/averages until reassessed.

Charts inside Diary use fixed0–100 axes, gaps for missing/unreviewed days,
7/30-day windows, and a distinct hollow point for today's provisional value.
The trailing7-day average counts only completed/current assessments and shows
the contributing day count. No new app navigation tab.

Chosen approach: Coach → Sheets → existing authenticated bridge → Android.
App-side rules lack ingredient/nutrient data; embedded AI duplicates the photo
workflow the user chose to keep in ChatGPT. Exact headers and scoring anchors
are in DIET_QUALITY_COACH_HANDOVER.md.
