# Diet Quality validation — 11 November 2026

The original build passed backend47/core80/Android81 automated tests (208 total),
Android lint with zero errors, JSON API compatibility, signed release assembly,
and native collapsed/expanded UI rendering at320dp width. Nonblocking lint
warnings remain. Robolectric runs in a fresh JVM per class with a ten-minute
unit-test cap inside the fifteen-minute CI job limit.

Independent review found cached-history rollback; import now rejects older
nonempty ledgers and cross-snapshot same-revision conflicts while preserving
valid-empty clearing. Core/UI regressions cover this. A deferred minor is that
chart accessibility summarises counts rather than every historical point;
individual day details are available through Diary date navigation.

The source checkout expired while awaiting explicit public-upload approval.
Source was recovered from the build record; backend/core checks are repeated
and CI will verify Android tests/lint/build again after publication. This is
recovery of the approved implementation, not a new feature change.

The saved APK0.7.0/code14 has SHA256
9c47d85cc3e1e91ca8544d8b2a3033cfe1a9a6e4d5843abbe75e171d3fd90553.
Its saved-key certificate SHA256 is
b8e828b3237e7e7fd31ca143d8f66aa9eee705e5529d585a55e784e0fb0de275.
It differs from the temporary-key0.6.1 APKs; sync and confirm no pending entries
before uninstalling one of those, because uninstall clears local data/settings.

DietQuality A:W headers were created and read back in the existing workbook;
no personal scores were seeded and no food, targets, weight or plans changed.
The prepared bridge still requires deployment to the existing Apps Script web
app, preserving URL/identity/access/secrets. Authenticated phone sync and actual
phone interaction remain unverified until that activation is complete.
