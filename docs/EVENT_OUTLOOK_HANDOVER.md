# Event outlook handover

Paste this into the normal Endurance Coach running-coach conversation. The connected Google account needs edit access to the private training database. The app does not calculate success probabilities.

---

I have updated Endurance Coach to display your conservative assessment of my current likelihood of reaching the event's primary and stretch goals. Please review the latest evidence and write the current assessment to the active event row in `Events`.

Before assessing, read `Events`, `Plan`, `Activities`, `Wellness`, `Feedback`, `WorkoutStatus`, `Health` (for recent weight entries), and `NutritionTargets` (for the current weight goal). Compare planned and completed training, recent weekly consistency, longest relevant efforts, fatigue/recovery, pain or injury notes, and remaining time to the event. Account for the Herdy's backyard format: each yard is 6.706 km and starts on the hour. Missing watch data is unknown, not evidence of recovery or inactivity.

Give two separate, conservative percentages:

- Chance of reaching the primary 10-yard / 67.06 km goal.
- Chance of reaching the stretch 12-yard / 80.47 km goal.

These percentages mean estimated chances of reaching those distances if the event were held based on current preparation; they are not expected finishing times, race placement, or statistically calibrated forecasts. Avoid false precision and optimism. Do not raise an estimate because of one good workout or lower it sharply because of one isolated missed session. Explain the main evidence and uncertainties, and distinguish the likelihood of success from the quality of the available evidence. If there is not enough recent evidence for an honest assessment, leave the five outlook cells blank and explain why.

Write the current values to the existing active Herdy's event row, without changing event targets, training workouts, nutrition targets, activities or health records:

| Column | Header | Format |
|---|---|---|
| L | `primary_confidence_pct` | Number 0–100 |
| M | `stretch_confidence_pct` | Number 0–100 |
| N | `confidence_as_of` | Perth date `YYYY-MM-DD`, stored as text |
| O | `confidence_quality` | `low`, `medium` or `high` |
| P | `confidence_reason` | Brief evidence summary, at most 280 characters |

Update all five fields together and read them back to confirm. The app shows the assessment after the next successful sync. `Events` stores the latest assessment only; it does not yet show an outlook history or trend. The app marks an assessment older than 14 days as due for review.

Suggested shorthand request: **“Frontyard outlook”**. When I use it, review the latest training and recovery evidence, update these five fields if justified, and summarize the estimate, evidence and uncertainty. Do not alter the plan unless I separately ask for a plan review.
