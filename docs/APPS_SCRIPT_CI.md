# Apps Script deployment from GitHub Actions

This workflow updates the existing Endurance Coach Apps Script web app without changing its deployment URL. Ordinary pushes run tests only. Inspections do not modify the live project.

## One-time authorisation (Windows PC)

1. Install Node.js 22+ and enable the Google Apps Script API: https://script.google.com/home/usersettings.
2. In PowerShell run npm install -g @google/clasp@3.4.1, followed by clasp login. Sign into the Google account owning the existing Endurance Coach Apps Script project.
3. In your Endurance Coach spreadsheet go to Extensions > Apps Script, then Project Settings. Copy the existing Script ID. Under Deploy > Manage deployments copy the existing web app Deployment ID. Neither ID is your spreadsheet ID; do not create a new deployment.
4. In GitHub Settings > Secrets and variables > Actions > New repository secret, set:
   - APPS_SCRIPT_ID: Script ID from existing Apps Script project.
   - APPS_SCRIPT_DEPLOYMENT_ID: existing web app Deployment ID.
   - APPS_SCRIPT_CLASPRC_JSON: the entire sensitive JSON contents of ~/.clasprc.json created by clasp login. In PowerShell, copy directly into the GitHub secret field using Get-Content (Join-Path $HOME '.clasprc.json') -Raw | Set-Clipboard, then clear clipboard using Set-Clipboard -Value ''.

Never paste OAuth JSON, refresh tokens, Garmin keys or BRIDGE_SECRET into ChatGPT or repository source. Treat the OAuth grant as a sensitive password.

## Read-only inspection

On feat/route-planner, a commit with message starting inspect-bridge: triggers a read-only inspection. The workflow checks the deployment ID, pulls the current project and prints Remote fingerprint SHA256 and whether the live source matches a known Git revision. No Apps Script file is changed.

The workflow also defines an Actions > Apps Script Bridge > Run workflow form for inspect or deploy; GitHub may only expose manual workflow_dispatch after the workflow has been merged to the default branch. The commit-triggered workflow is available on feat/route-planner.

If the inspection says Remote source in Git history: NO - BLOCKED or reports extra files, stop. Changes from previous Work sessions or the Apps Script editor must be reconciled rather than overwritten.

## Guarded deploy

After a successful inspection, a commit starting deploy-bridge: <64-character-remote-SHA256> on feat/route-planner triggers deployment. The job checks current online code against repository history and the exact fingerprint, pushes backend/Code.gs and backend/appsscript.json, re-pulls to verify, and only then updates the SAME existing deployment ID to a new version. This preserves its /exec URL and Script Properties. No ordinary commit deploys the bridge.

Once deployed, run the read-only verifySetup() in your existing Apps Script editor and check the app's normal sync. The new RoutePlanner worksheet already exists.

Google OAuth may need reauthorisation when it expires or is revoked. A failure after pushing but before redeploying can modify unversioned source HEAD without altering the active versioned web app.

References: https://developers.google.com/apps-script/guides/clasp and https://github.com/google/clasp
