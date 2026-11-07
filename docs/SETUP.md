# Personal setup

## Google Sheets and Apps Script

The development session creates a private training spreadsheet and a private original-record folder in your Google Drive. Their links are supplied in chat; private account identifiers are deliberately absent from the public source repository.

1. Open that spreadsheet, select **Extensions → Apps Script**. Replace the default code with `backend/Code.gs`. In Project Settings enable **Show appsscript.json manifest file** and replace it with `backend/appsscript.json`.
2. Add these **Script Properties**, never literals in source:

| Property | Value |
|---|---|
| `SPREADSHEET_ID` | ID of your training spreadsheet |
| `ARCHIVE_FOLDER_ID` | ID of your private original Garmin records folder |
| `BRIDGE_SECRET` | A separate, randomly generated secret of at least 32 characters; preferably 32 random bytes encoded as hex |

Generate the secret with a password manager. Do not reuse your Garmin key, place it in a Sheet, send it as a URL query, or commit it.

3. **Deploy → New deployment → Web app**. Execute as **Me**, access **Anyone**. Authorise the script's access to your spreadsheet and original-record folder. Although the endpoint is reachable, every data read and write requires the secret in its HTTPS POST body; `GET` returns no data. Google Workspace policies may prevent this deployment option.
4. Copy the resulting `/exec` URL into the app's Settings along with the bridge secret.
5. When changing bridge code later, **Manage deployments → Edit → New version** on the same deployment to preserve the URL. Workout revisions are ordinary data changes and do not require deployment.

The bridge requires Sheets and Drive scopes to archive original source JSON. Do not add general-purpose Apps Script code from an untrusted source to this deployment. This backend is intended for one personal account, not a multi-user service.

## Garmin

In a browser sign in to Garmin Connect. Open **Profile & Account → Account Settings → Personal API Key → Create Key**. Accept Garmin's terms yourself and select an expiration period. The key is displayed once. Store it in your password manager, then enter it directly into the installed app's Settings. Do not paste it into this chat or GitHub.

The app exchanges the key for a 24-hour bearer token with Garmin's official endpoint. The key and database connection settings are encrypted with Android Keystore AES-GCM; bearer tokens stay in process memory. Revoked access is retried once with a fresh token. Replace the key in Settings when it expires. Replacing the key does not delete your imported history.

## First live acceptance check

1. Sync the watch to Garmin Connect, then manually sync this app. First import requests the last 30 days of activities and 7 days of sleep/HRV/resting HR.
2. Compare the latest run's distance, duration, heart rate and cadence with Garmin Connect. Inspect its original FIT sample charts.
3. Check sleep and HRV against the same Garmin calendar day. Unavailable values must show a dash.
4. Add feedback and sync twice; verify exactly one Feedback row and one Activities row per source ID.
5. In ChatGPT append a new revision for a future workout. Refresh Plan and confirm the date/duration changes, the earlier row remains, and actual activity values remain intact.
6. Turn off internet, save feedback, restart the app, reconnect and sync. Verify the feedback is delivered once.

For future events add a row in Events and set it active; turn the previous event inactive. Use unique event IDs, and associate new Plan rows with the new event. All dates currently use the athlete's Australia/Perth training timezone.
