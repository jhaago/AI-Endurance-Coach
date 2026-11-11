# Route planner — recovered design, 11 November 2026

Continue the user's approved route-planning brief and reviewed mockup inside the existing Java Android app. Planning happens before a session; there is no navigation during an activity. Session details open Suggested, My routes and Create. The start is chosen per session, with remembered choices by session type, never an assumed home/trailhead.

Show a real interactive map, elevation profile (unknown stays unknown), distance, ascent, estimated running time and a candid session-fit explanation. Search a place or choose a map start; filter by distance or duration, road/path/trail, climbing and loop/out-and-back/point-to-point. Support saved favourites, GPX, permitted established-route discovery, generated routes and map waypoints snapped to pedestrian-accessible roads/paths. A generated route can be adjusted and recalculated. Clearly label source and approximation/mismatches.

## Approach

Continue the previous session's choice: FOSSGIS Valhalla pedestrian routing and height data; OSM raster maps, Nominatim manual place search and Overpass established walking/running route relations. Paid providers require account/billing and self-hosting needs infrastructure; neither is necessary for this personal app. Services are replaceable via a provider boundary; no credentials go to map providers. This integration choice is the builder's, not an additional user decision.

Native map/profile views preserve the app's dark/light theme and avoid remote executable JavaScript or added SDKs. Only visible tiles are fetched and cached for at least seven days. No offline tile downloads. Place search requires a button press, never autocomplete. Public API requests use an identifying client header, global per-service pacing, bounded requests, caching and explicit failure states. Allow public-service disable/change from planner settings. Public services have no uptime guarantee; no paid account is needed. Distribution beyond this personal build requires provider review.

## Data and resilience

Use a separate immutable RoutePlanner ledger: change_id, record_id, kind (route/attachment/preference), payload_json, deleted, updated_at. Route payload includes geometry, sampled elevations, source attribution, editable waypoints, favourite and measured metrics. Attachments contain a copy of the selected route and filter context so later route edits cannot silently alter a planned session. Preferences key session type, not every run globally. Store geometry compactly with bounded simplification (preview warning); no straight-line fallback for failed routing. GPX rejects disconnected tracks, unsafe XML, oversized files and invalid coordinates.

SQLite/outbox save before network. Validate the entire incoming/outgoing ledger; immutable IDs are idempotent and conflicting reuse fails. Require a fresh route_schema_version capability and explicit acknowledgements before clearing queued changes. Old bridges keep core sync working while route changes remain on phone. Snapshot retention merges by timestamp/ID and preserves offline changes. This schema also supports future Coach chat writes without a separate route format.

## Implementation bounds

Distance/shape/ascent and start radius are checked against actual geometry. Surface preference influences routing but is labelled unverified without edge classification; unknown terrain/elevation cannot pass strict filters. Running estimates use recent comparable Garmin runs with a stated fallback and terrain/climb adjustment, never the routing provider's walking duration. Generation tries a bounded three directional candidates; nearest matches are labelled, never advertised as guaranteed loops or exact distance. Established route discovery keeps only contiguous geometry, preserves an OSM relation link and never implies event-course endorsement or current access verification.

## Verification

Core tests cover geometry, GPX, matching/uncertainty and HTTP contracts. Backend tests cover immutable route delivery, literal text, old requests and mixed-batch validation. Android tests cover SQLite/outbox/old server/new edits and entry points. Run all existing bridge/core/Android tests, lint, build and JSON compatibility. Record live service and physical-device checks separately from synthetic CI evidence.
