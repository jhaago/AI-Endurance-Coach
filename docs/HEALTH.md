# Health data

Sheet `Health` is an append-only ledger. Headers, in order:

`change_id,record_id,kind,date,name,calories_kcal,portions,weight_kg,note,deleted,updated_at`

Kinds: meal, favourite, weight, goal. Calories are per portion; daily totals multiply by meal portions. Favourites copy values into new independent meal records. Each edit/deletion creates a UUID change ID with stable record ID and UTC millisecond timestamp. Current state is the maximum parsed timestamp, then lexical change ID. Deleted records remain in snapshots so old deliveries cannot resurrect entries. Same ID/same payload retries are idempotent; changed payloads conflict. Weight IDs are `weight:YYYY-MM-DD`; optional goal ID is `goal-weight`.

App uploads batches of 30, server accepts up to 100. Responses include `ack_health` and materialised `snapshot.health`. A previous bridge without those fields leaves the app queue intact. Local state and queued immutable changes save in one SQLite transaction; snapshots and acknowledgements do likewise. This uses the existing documents/outbox schema without destructive migrations. Missing days are null, never zero. Seven-day averages use logged/measured days and display their count. A zero-calorie meal is an explicitly logged day.

Deploy: create the Health sheet/header once, paste updated Code.gs into the existing Apps Script project, save, run read-only `verifyHealthSetup`, and update the existing deployment to a new version while retaining its URL, access settings and secret. No new scopes are needed. Never paste the bridge secret into source, URLs, chat or GitHub. On the phone use Settings → Sync & import details → Refresh plan to upload Health without Garmin downloads.

Current deployment status: Health sheet/header created and verified; updated bridge source tested locally but not yet deployed because Google sign-in expired. Actual phone/cloud Health synchronisation requires completing that deployment and checking one user-entered meal. Tests and previews use synthetic records only.
