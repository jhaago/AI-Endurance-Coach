# Endurance Coach design

The supplied project brief is the implementation authority. Build a single-user native Android app with Today, Plan, Progress and Insights, reusable event settings, three preferred runs and an optional fourth. Initial event: Herdy's Frontyard Ultra, 2027-03-12; primary 10 yards / 67.06 km; stretch 12 / 80.47 km. Do not generate a full training prescription without current training evidence.

## Approaches
1. Official Garmin Personal API + private Google Sheets: selected developer recommendation; supported personal authentication, rich wellness and original FIT access, chat-editable plan, no paid AI runtime.
2. Health Connect: supported Android route but Garmin's published sharing list lacks HRV and sleep score; incomplete for this brief.
3. Commercial Garmin API: useful for multi-user distribution, onboarding and backend infrastructure unnecessary for this personal MVP.

## Boundaries
Java 17, Android 8+ and platform UI keep dependencies minimal. Garmin's official FIT SDK decodes time series. Credentials in Android Keystore AES-GCM storage; backups disabled. Official Personal API only, fixed host, key-to-token exchange, no password scraping. Run background sync with JobScheduler and explicit manual refresh; never promise instantaneous sync.

Private Sheets is the accessible central database. A small Apps Script POST bridge holds a separate random shared secret in Script Properties; never Garmin credentials. Append-only Plan rows use workout_id + integer revision, metadata and reason. Highest revision wins; cancelled revisions remove future workouts. Activities and wellness are separate from the plan. LockService serializes writes. Stable IDs and content hashes make retries idempotent. Original JSON and original FIT ZIP remain in phone-private storage; original JSON also archived to a private Drive folder when uploaded. Cloud normalized metrics are accessible through the connected Sheets tools.

Missing wellness is null, never zero. Watch-off working hours are explicitly disclosed as coverage gaps. Fatigue suggestions are qualitative and never auto-change the plan. ChatGPT reviews evidence and appends future revisions. The app reloads the ledger on refresh/foreground without APK changes. Feedback uses a durable outbox and server acknowledgement before deletion. Ambiguous matches remain unassigned, with manual correction available.

## Authorization dependencies
User creates private GitHub repository (connected tooling lacks repository creation). Garmin personal key entered directly in installed app. Apps Script needs user deployment/Google consent; no supported connected Apps Script deployment tool is exposed. Build and fixture-based tests proceed independently; live account sync remains unverified until those steps.
