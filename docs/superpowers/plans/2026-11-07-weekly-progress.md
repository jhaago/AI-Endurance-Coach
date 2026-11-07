# Weekly running progress

Goal: add a simple Strava-like 12-week distance graph to Progress with selectable Monday–Sunday weeks (Perth time), distance/time/elevation totals, and honest missing-data states.

- [x] Test and implement a pure core weekly aggregation model: run/trail/treadmill inclusion, walks excluded, UTC→Perth boundaries, deduplication, null metrics, real zero weeks vs unknown coverage and current week so far.
- [x] Backfill older activity summaries once for the displayed 12-week horizon using the existing documented paginated Garmin activity-list endpoint. Preserve detailed records already held. Continue recent detail/FIT imports; mark coverage only after every listed record is saved.
- [x] Build a native teal line/area chart with selectable points, accessibility/keyboard actions, dark/light support and empty/partial-history labels. Keep actual activities separate from the training plan.
- [x] Add UI tests and render synthetic previews; run all existing tests, JSON API check, build, lint and a focused review.
- [x] Publish source and same-signer 0.3.0 APK/ZIP update.

The training programme is edited independently in the existing private Google Sheet, using append-only plan revisions. It is personal training data and does not belong in source fixtures or the repository. No credentials or API endpoints change.
