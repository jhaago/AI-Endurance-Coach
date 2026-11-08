# Post-run app-open sync

Jordan's workflow: save watch activity, review it in Strava, open Endurance Coach to add session feedback, then discuss the centrally synced training history in this chat later. Endurance Coach reads Garmin data, not Strava edits. Watch-to-Garmin availability remains a prerequisite.

Use the existing asynchronous full Garmin import when the Activity resumes. Render cached training immediately; retain Settings-only status. Keep minute-by-minute cloud refresh and the six-hour background backup. Existing detail caching and changed-row upload suppression apply. No time cooldown that could hide a just-saved run. Requests during a current sync coalesce into one follow-up, with Garmin priority over cloud-only requests; queued note writes must get a follow-up even if the user leaves the Activity. Repository serialisation must wait interruptibly rather than skip an app-open sync behind the background job. Attempt cloud upload even when Garmin fails, preserving its warning and all local entries. Do not change Garmin authentication, plan logic or Health server deployment.

Approaches considered: reducing background interval would still leave a post-run delay; adding another activity-only path adds a second import policy. App-open full sync reuses the verified importer and is the smallest maintainable change. Builder's call: no arbitrary open cooldown; coalesce overlapping requests and reuse caches.

Ship normal package au.com.endurancecoach, version0.4.1/code8, same backed-up replacement signer as0.4.0. It can update0.4.0, but cannot update old0.3.x in place. No phone installation or backend deployment is performed here.
