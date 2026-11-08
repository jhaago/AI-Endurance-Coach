# Nutrition and completion implementation plan

Spec: ../specs/2026-11-08-nutrition-targets-design.md
Execute inline root integration, independent backend and Health UI owners. Same protocol fixed before delegation. Published remote baseeb60cee9ab2ebe8f2b46f1a784f345b10ec2e728/local8a2a3a0; no original0.3 signing key recovery.

- [x] Restore build/runtime/private signer; checkpoint source in GitHub before credit pause.
- [x] Backend owner: Health trailing fields/WorkoutStatus/NutritionTargets validation +snapshots/legacy safety/literal calendar dates; Node red->green; no live writes.
- [x] Health owner: actual Health model/Diary/Weight UI and tests; consumes root NutritionTargets.Target and repository getters, no target writes.
- [x] Root: NutritionTargets validation/selection/cache tests; repository warning propagation/transactional cache; WorkoutCompletion core/store/outbox/UI and tests.
- [x] Integrate tests/lint/JSON/cleanAPK/signer, native previews and focused review; fix important findings via regressions.
- [ ] Root: inspect live headers, additive header migration only, deploy same Apps Script URL if auth available; run read-only verification. No synthetic nutrition targets in personal sheet.
- [ ] Publish source/updatePR/APK+ZIP; Coach handover exactschema/protocol and limits. Explicit outstanding phone/deployment checks.

Checkpoint: Google Sheet additive headers verified. Apps Script signed out; deployment and authenticated phone roundtrip remain blocked until secure sign-in. No live target values seeded. Final artifact signer/package verified.
