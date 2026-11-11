# Route Planner Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task. Continue the previously approved brief without repeating its approval process.

**Goal:** Select, create, import and retain routes for upcoming sessions.
**Architecture:** Java core geometry/matching/provider contracts; native Android map/planner and SQLite outbox; separate Apps Script immutable ledger.
**Tech Stack:** Java17, Android26+, org.json, Apps Script, Node/JUnit/Robolectric.
**Spec:** docs/superpowers/specs/2026-11-11-route-planner-design.md

## Global Constraints
- Existing package au.com.endurancecoach, native dark/light appearance, no new paid account or AI key.
- No navigation, automatic home start or fabricated route/elevation/access claims.
- Public provider caching/pacing, strict routing failure and offline durable saves.
- Immutable RoutePlanner events; capability gate and explicit ack; preserve existing data.

## Review Focus
- Disconnected GPX or OSM segments must not become straight-line routes.
- A route edit arriving during sync must remain queued; old servers must not discard data.
- Unknown ascent or surface must not be represented as a confirmed strict match.
- Rotation/Back during requests must not attach stale results or destroy drafts.
- Longitude wrapping, invalid coordinates, oversized XML and unexpected provider JSON must fail clearly.

### Task 1: Route model, GPX and matching
Files: core/.../RoutePlan.java, RouteMatcher.java, GpxRoutes.java; RoutePlanTest.java.
Interfaces: RoutePlan.geometry(JSONArray), create(...), change(...), current(JSONArray...), validateChange(JSONObject); RouteMatcher.defaults(session,activities), assess(route,requirements,activities).
- [ ] Write tests for real geometry, missing elevation, immutable conflicts, malicious/disconnected GPX, estimates and strict unknowns; run to see missing feature.
- [ ] Implement and run all core tests with JUnit; commit.

### Task 2: Map service integration
Files: core/.../MapServices.java, PublicMapTransport.java; MapServicesTest.java.
Interfaces: search(query), route(waypoints,terrain), generate(start,requirements), discover(start,radius); provider calls transport-injected for offline tests.
- [ ] Write fixtures for pedestrian requests, bounded candidates, invalid shape, rate-limit failures and disconnected discovery; run red.
- [ ] Implement caching/pacing hooks, route parsing and height handling; run core suite; commit.

### Task 3: Persistence and bridge
Files: backend/Code.gs, backend/test/bridge.test.cjs, app/.../LocalStore.java, CoachRepository.java; RouteSyncTest.java.
Interfaces: store.routeRecords(), saveRouteChanges(changes), ackRouteChanges(snapshot,ack); repo syncCloud sends route_changes after fresh capability check.
- [ ] Add failing bridge/sync tests for idempotence, atomic validation, old bridge retention and concurrent edits.
- [ ] Implement ledger/schema, snapshot/error isolation, local merge and explicit acknowledgement; run backend/core tests; commit.

### Task 4: Session planner and interactive map
Files: app/.../RoutePlannerActivity.java, RouteMapView.java, RouteProfileView.java, RouteStore.java; MainActivity.java, AndroidManifest.xml; RoutePlannerUiTest.java.
Interfaces: activity launched with workout_id; map callbacks for start/waypoint editing; route store holds persistent drafts and cache. Activity reads freshest session and stored preferences, saves selections atomically, invokes normal sync.
- [ ] Add UI entry/navigation/offline/draft tests; run red in CI if SDK unavailable locally.
- [ ] Implement Suggested/My routes/Create, place/start/filters, route detail, GPX picker, generation/drawing, favourites, attachment/removal and session-type preferences.
- [ ] Verify all suites/lint/build/JSON checks; review whole branch; address important findings.

### Task 5: Delivery
- [ ] Document providers/setup/limits/schema/handover and measured validation.
- [ ] Publish feature branch, obtain passing CI and signed APK, preserve existing signing identity.
- [ ] Activate bridge schema/deployment if connected authenticated tools permit; otherwise retain route outbox and give exact remaining step. No false deployment or device claims.
