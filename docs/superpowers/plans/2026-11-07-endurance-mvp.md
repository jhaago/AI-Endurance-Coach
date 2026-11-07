# Endurance Coach Implementation Plan

Goal: produce an installable Android MVP, a verified official Garmin adapter, private chat-editable training ledger and repeatable tests.
Architecture: Android calls Garmin directly; Apps Script mediates a private Sheets ledger. Local SQLite cache/outbox supports offline use. No OpenAI API.
Spec: ../specs/2026-11-07-endurance-design.md

## Global constraints
- Event 2027-03-12; 10 yards 67.06 km / 12 yards 80.47 km; Perth timezone.
- No personal data or credentials in GitHub; plan and actuals separate.
- Original source records retained; partial wellness and gaps visible.

## Review focus
- A response lost after server commit must not duplicate an upload.
- Multiple same-day activities/workouts must not be incorrectly matched.
- Concurrent phone sync and chat plan revision must not overwrite a revision.
- A revoked Garmin token retries once; redirects must not leak credentials.
- Missing wellness must never display invented recovery scores.

## Tasks
- [x] Core: tests for version resolution, matching ambiguity, timezone, null metrics, token expiry/401, pagination and FIT parsing. Implement PlanEngine, GarminClient, Metrics, FitRecords and Transport; run :core:test.
- [x] Bridge: tests for authentication, validation before mutations, stable upserts, conflict rejection, feedback idempotency, formula escaping and snapshot revisions. Implement Apps Script and in-memory API harness; run node --test backend/test/*.test.cjs.
- [x] Android: four screens, encrypted settings, SQLite outbox/cache, original archive, background/manual sync, feedback, manual matching and source sample detail; compile APK and run lint.
- [x] Central ledger: create/import native private Sheet, verify headers/event; include bridge deployment and chat-update instructions.
- [ ] Integration: run all tests, independent review, fix important findings; package source/APK; publish to user-created GitHub repository if available.
