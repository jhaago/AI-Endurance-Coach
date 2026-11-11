# Route planner setup and handover

Version 0.8.0/code15. Entry: upcoming running session → Workout details → Route planner.

Suggested / My routes / Create share a real native map. Choose a place, map start or coordinates for this session. Apply distance/duration, surface, climbing, shape and start-radius filters. Saved/generated/discovered routes are ranked by fit; mismatches are explicit. Draw by tapping waypoints, drag numbered markers or edit coordinates, then Calculate mapped route. Adjust on map works for generated and imported routes as well. Import one continuous GPX track or route (5 MB/20000 points maximum). Attach saves a pinned route copy plus filters and remembers the location/filter choice by session type. Favourites and drafts remain available without network.

## Providers and costs

No billing account or secret is required by the default personal configuration. FOSSGIS Valhalla provides pedestrian routing and sampled DEM heights; OSM provides raster tiles and manual Nominatim searches; Overpass provides up to 12 nearby mapped hiking/foot/running relations within a maximum 25 km discovery radius. Disconnected/incomplete relations are skipped. This is permitted open-data discovery, not scraping Strava/AllTrails or asserting an event course is official.

Map services & sources can disable calls or change compatible HTTPS route/search/discovery endpoints. Search is user-triggered, never autocomplete. Non-tile requests are serialised/paced at least 1.1 seconds apart per host with an identifying User-Agent and X-Client-Id. Route/search/discovery responses cache for one day; heights for seven days. Tile requests cover only the visible viewport and cache at least seven days. No offline map downloads. Do not run automated map panning against public tiles. Services may throttle/change/withdraw access; commercial or broader distribution needs separate provider review. Endpoint changes take effect without an app update; map enablement takes effect on reopening the planner.

Policies and contracts checked 11 November 2026:
- https://valhalla.github.io/valhalla/start/introduction/
- https://valhalla.github.io/valhalla/api/route/api-reference/
- https://valhalla.github.io/valhalla/api/elevation/
- https://operations.osmfoundation.org/policies/tiles/
- https://operations.osmfoundation.org/policies/nominatim/
- https://dev.overpass-api.de/overpass-doc/en/preface/commons.html

Coordinates/search terms/map areas are sent to the chosen public providers. Garmin keys and bridge secrets are never used by mapping calls. Saved routes use the existing private database connection. No GPS permission, navigation, background location or OpenAI API key.

## Backend activation

Save the updated backend/Code.gs into the existing Apps Script project. Run editor-only `setupRoutePlanner()` once, which creates only a missing RoutePlanner worksheet and verifies its header. Update the existing web app deployment, retaining its deployment ID, URL, execution identity and access settings. Run read-only `verifySetup()`. Authenticated snapshots advertise route_schema_version:1 and route_changes. Phone requires a fresh capability response before upload, and explicit ack_routes before removing queued changes. Old bridges leave route changes on phone; other data sync continues.

RoutePlanner columns A:F: change_id, record_id, kind, payload_json, deleted, updated_at. All strings are literal text; updated_at is UTC ISO with millisecond precision. Immutable retries must have the same six fields. Same change_id with changed content is a conflict. Current state uses updated_at then change_id; retain tombstones. record_id prefixes are route:, attachment:<workout_id>, preference:<session-type-key>. kind is route, attachment or preference. payload_json ≤45000 characters. Routes contain name/source/points/waypoints/distance_m/ascent_m/terrain/favourite/simplified/attribution/source_url, optionally profile/elevation_note. Coordinates are [latitude,longitude,optional elevation]. Geometry ≤1000 points; editable waypoints ≤24; height profiles ≤200 points. Attachments contain workout_id, session_revision, route snapshot and requirements. Preferences contain validated requirements. See core RoutePlan.java and backend validRouteChange for the shared validation contract.

Future Coach chat integration can append valid immutable rows to this ledger using the same schema; never mutate an existing change_id. No chat-to-route integration is claimed in this version.

## Practical limits

Generation tests three directions, not an unlimited optimiser. Distances and shape can differ from the target and are labelled. Pedestrian access is not proof of road/path/trail surface. Unknown surface/elevation never passes a corresponding strict filter. Running estimates use the median of comparable runs from the last 90 days, or an explicitly labelled 7:00 min/km fallback with trail/climb allowance; walking ETA is ignored. Actual conditions, stops and effort vary. Ascent is sampled/approximate, not barometric. Missing heights stay unknown. Original route distance is retained when display geometry is simplified. Current closures/access are not independently verified. GPX and relation gaps never get invented straight connectors.

## Validation status

See docs/ROUTE_PLANNER_VALIDATION.md for actual evidence and pending activation/device checks.
