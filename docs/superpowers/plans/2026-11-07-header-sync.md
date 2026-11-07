# Header and sync cleanup

- Rename the banner ENDURANCE COACH, keeping the narrow/large-text header usable.
- Keep sync status, timestamps and manual Garmin sync under Settings only; remove them from all four tab headers and Today actions. Preserve current errors and update an open sync-details dialog as sync progresses.
- Eliminate the final redundant snapshot call after upload responses already supplied a complete snapshot. Continue snapshot-only polling when there are no writes; keep acknowledgements and retry hashes durable.
- Reuse nonempty activity details for up to 24 hours only when the complete list summary is unchanged. Changed, uncached, failed, expired or future-clock records refetch; missing FIT files still retry. No API/authentication/schema changes or concurrency increase.
- Run core and Android UI/persistence tests, backend tests, JSON compatibility, build/lint and a focused review. Inspect native previews, then publish source and same-signer 0.3.1 APK/ZIP.

Speed evidence is synthetic request counts, not a live phone benchmark. The initial 12-week import, wellness requests and Google Apps Script/network latency remain.
