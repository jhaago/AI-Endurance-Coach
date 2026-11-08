# Updating the plan from ChatGPT

Use the private training spreadsheet linked in the project chat. No public publishing of that sheet is required. Its existing connected Google Drive tools can read/imported history and append future workout revisions without direct access to Garmin credentials. ChatGPT does not automatically run after every watch sync; request a review here when desired.

Suggested request:

> Read my AI Endurance Coach training database. Compare my last two weeks of actual activities, feedback and sleep/HRV trends with the latest workout revisions. I wear my Forerunner 955 overnight and for training but remove it at work. My goal is Herdy's on 12 March 2027, primary 10 yards and stretch 12. Prefer three weekly runs, optional fourth. Review next week and append any justified future workout changes as new Plan revisions. Preserve original activities and earlier plan rows. Explain the changes and any missing evidence.

Writer contract:

1. Read metadata and bounded ranges from Events, Plan, Activities, Wellness, Feedback and Matches. Read all revisions needed for affected workout IDs.
2. Resolve each workout's maximum integer revision. Keep actuals and subjective feedback distinct. Do not infer recovery from absent daytime data or a single HRV point.
3. Append a row with the same workout ID and `revision + 1`. Copy the event ID, specify date/type/title/duration/intensity/optional/status, and record `author`, UTC `created_at` and `reason`.
4. To cancel, append a higher revision with `status = cancelled`. To create a new session, use a new unique workout ID and revision 1.
5. Use a single Sheets `appendCells` request for a coherent set of changes. Never overwrite the sheet from a previously read full snapshot. Keep ISO dates as explicit string cells.
6. Read back affected rows, verify there are no conflicting workout/revision pairs and report the changes. In the Android app refresh the plan or wait for its next foreground poll.

The phone cannot overwrite the plan through the bridge. Direct Google Sheets access remains editable by the owner, so append-only history is a workflow contract rather than a tamper-proof audit log. Two independent ChatGPT writers must not revise the same workout concurrently; duplicate revision conflicts are surfaced for correction instead of silently choosing a winner. Sheets file version history offers additional recovery.

When setting a programme, read the latest actual weekly volume rather than treating an unusually long race as the habitual baseline. Keep club/day preferences in workout titles and instructions; record recovery gates and the provisional nature of later progression. Prefer reviewing the next one or two weeks after subjective feedback and activity imports. Do not infer medical clearance or invent missing recovery metrics. Personal prescriptions and training data stay in the private training database, separate from source code and synthetic tests.


Nutrition targets are managed in the normal Coach conversation through writable Google Sheets tools. Use [the nutrition handover](NUTRITION_COACH_HANDOVER.md). The app does not calculate or edit calorie targets; food and weight stay in the separate Health actuals ledger.

For the event outlook on Today, follow [the event outlook handover](EVENT_OUTLOOK_HANDOVER.md). Read the full recent training history and current event targets before updating the active `Events` row. Write both goal estimates, the Perth assessment date, evidence quality and a concise rationale together. These are Coach judgements, not calibrated statistical forecasts; do not make them artificially optimistic or revise them because of one isolated workout. Leave the fields blank rather than invent an estimate when the record is insufficient. The app displays them after its next successful sync.
