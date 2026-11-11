# Diet Quality Implementation Plan

Goal: provisional diet quality, inline breakdown and trend history in Diary.
Spec: docs/superpowers/specs/2026-11-11-diet-quality-design.md.
Stack: Java17/Android/org.json, Apps Script, Node/JUnit/Robolectric.

- [x] Read-only A:W assessment ledger, strict fields, total/revision/conflict validation.
- [x] Core selection, signatures, history, zero-vs-missing and provisional rules.
- [x] Durable independent cache, retaining last valid history on bad/older responses.
- [x] Diary section and accessible fixed-scale chart without new navigation tab.
- [x] Regression tests for contract, sync, stale food edits, UI and averages.
- [x] Coach handover with fixed rubric and safe append/readback protocol.
- [x] New worksheet headers created/verified without seeding scores.
- [x] Original build: backend47/core80/Android81, lint, JSON and signed APK checks.
- [x] Independent review; cached-history regression fixed and tested.
- [x] Published to current development branch after explicit public-repository approval; CI run91 passed.
- [x] Existing Apps Script web app updated to version5; read-only setup check passed.
- [ ] Confirm authenticated phone sync and physical device behaviour.

Review focus: revision conflicts and rollback; local food edits/deletes/moves;
nonfatal missing new sheet; genuine zero vs unknown; future/withdrawn/stale days.
