# Persistent Android signing

Endurance Coach updates use the existing replacement signing key. The private backup is `Endurance-Coach-Preview-Signing-Backup.zip`; its `PRIVATE-README.txt` gives the alias and passwords. The key's SHA-256 certificate fingerprint was checked against the existing replacement app signer.

Never commit the keystore, its base64 data, or passwords to Git. Store them as encrypted repository Actions secrets.

## Add repository secrets

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
| --- | --- |
| `ANDROID_SIGNING_KEYSTORE_BASE64` | Base64 of the `debug.keystore` file inside the private backup ZIP (not the ZIP itself). |
| `ANDROID_SIGNING_STORE_PASSWORD` | Store password from `PRIVATE-README.txt`. |
| `ANDROID_SIGNING_KEY_ALIAS` | Alias from `PRIVATE-README.txt`. |
| `ANDROID_SIGNING_KEY_PASSWORD` | Key password from `PRIVATE-README.txt`. |

On Windows PowerShell, after extracting the ZIP, generate the first secret value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\debug.keystore"))
```

Paste the output directly into the GitHub secret value field. Do not put it in a source file or commit it.

## Build a signed APK

After saving all four secrets:

After saving the four secrets, tell ChatGPT the secrets are set. A release commit beginning with `release:` on `feat/health-meals-preview` will start the signed build automatically. You can also start it manually from **Actions → Android MVP → Run workflow** and select that branch.

Wait for the **signed-release** job, then download the `endurance-coach-0.7.0-signed` artifact.

The artifact contains `app-release.apk`, signed with the same persistent key as the existing app. Android can install it as an update, preserving app settings and local data. The separate debug APK artifact uses a temporary CI key and is not for updating the installed app.
