# Route planner validation

Recovered current development source exactly from feat/health-meals-preview at5c3192d6acdc4bfe012d32acf90c174dabe64e5c. Original local workspace was preserved. Work is on feat/route-planner.

Fresh local checks: backend52/52 and core90/90 passing; Android production Java compiles against Android15 framework classes. Core tests cover real geometry, GPX and XML rejection, immutable conflicts, estimates, missing surface/elevation and provider fixtures. Backend tests cover immutable retries/tombstones, literal JSON, old schema isolation and whole mixed-batch validation. Android Robolectric suites, lint and real APK assembly are pending CI; compilation is not a replacement for them.

No live bridge activation or physical-device route sync/interaction is claimed yet. No personal training/health/event rows were changed for testing. Default map services have no paid account configured. Live-provider smoke test and final code review pending.
