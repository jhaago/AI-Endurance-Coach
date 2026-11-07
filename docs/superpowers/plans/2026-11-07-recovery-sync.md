# Recovery sync repair

Observed: first device sync imported 14 activities and their raw JSON, but no wellness rows; Insights confirms no local recovery records. Foreground plan polling overwrites Garmin warnings with `Up to date`, so the prior build hides the evidence needed to distinguish no-data, HTTP failure and unexpected response shape.

1. Add tests for credential-safe HTTP/shape diagnostics, no-data vs error, official same-date range-summary fallbacks, and warning persistence through plan polling.
2. Add typed Garmin JSON results and a focused RecoveryReader. Use only documented Personal API endpoints and schemas. Preserve original objects separately, select summaries by calendarDate, and keep unavailable measurements null. Never return HTTP bodies, keys or tokens in diagnostic text.
3. Save a nullable per-day wellness record with diagnostic coverage notes even if no metric is returned. Preserve previous measured values when fresh attempts are unavailable, label them as retained, and retry transient errors. Preserve recovery status across plan-only polling.
4. Expose the latest diagnostic note in Insights, bump to 0.1.1, and test/build/lint/JSON-API-check. Compare signing certificate against 0.1.0 to support an in-place update.
5. Commit and upload to the existing private repository. Save the installable update and ZIP fallback. Device must run the new diagnostics before the upstream cause can be confirmed; do not claim actual recovery import repaired solely from fixture tests.

Review fixes: never interpret ambiguous summary daily-minimum HR as measured resting HR; require actual sleep values; retain prior same-day cloud/local measurements per field, and protect cloud values from null diagnostic uploads. Live recovery import still needs the user's updated APK.
