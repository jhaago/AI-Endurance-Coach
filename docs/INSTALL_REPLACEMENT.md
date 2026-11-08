# Endurance Coach installation and updates

If Endurance Coach 0.4.0 is already installed with the backed-up replacement key, install 0.4.1 directly over it. Keep the app installed; settings and local data remain in place.

For the older 0.3.x installation only:

This APK has the normal name and package (`au.com.endurancecoach`). It contains the Health features and is signed using the new privately backed-up key. The original 0.3.x signing key is unavailable. Android cannot install this APK over that older app; renaming the file or increasing the version cannot fix the certificate mismatch.

Keep the current app installed until these checks are complete:

1. In the existing app, open Settings → Sync & import details. Sync and confirm there is no error and no pending check-in/change count. If it cannot sync, stop and keep the existing installation.
2. Preserve the Garmin key, bridge URL and bridge secret securely before replacing the app. Settings and tokens do not transfer. Do not send them in chat or put them in GitHub.
3. Check that the history you need is present in the central database. Files or records stored only on the phone are not automatically migrated. If any important local-only data remains, stop and retain the old app.
4. Only after reviewing those checks, choose whether to uninstall the old Endurance Coach and install this replacement. This step is user-controlled; development has not changed the phone.
5. Re-enter connections in Settings and download central training data. If you installed the separate Preview, its data remains in that separate app until you remove it; keep it while it has unsynced Health entries.

The Health bridge deployment is still pending Google sign-in. Health entries save offline but cannot upload until the server update is deployed. Uninstalling either app discards its unsynced local data.

Future Endurance Coach updates must use the exact replacement signing key, already saved privately in the Endurance-Coach-Preview-Signing-Backup.zip archive. The key is the same for this normal package despite that archive's historical filename. Never use the ephemeral CI signing certificate for phone updates.
