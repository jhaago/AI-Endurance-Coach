# Verified Garmin integration options

Checked **7 November 2026** against Garmin's official pages and the actual OpenAPI specification embedded in the Swagger page. This is interface verification, not a successful request using the athlete's key.

| Route | Officially documented capability | Decision |
|---|---|---|
| Connect Personal API | Own account; personal key generates OAuth2 bearer tokens; pull access to activity JSON, original ZIP/FIT and health JSON | Implemented for this personal app |
| Garmin Connect Developer Program | Organisation integrations, Activity/Health and Training/Courses APIs; access tiers and onboarding apply | Consider only for future multi-user distribution |
| Garmin → Android Health Connect | Activity distance/speed/HR/elevation and sleep stages; Garmin's published sharing list does not include overnight HRV, sleep score or running cadence | Incomplete substitute for requested data |
| Connect IQ / Health SDK | Separate watch applications/direct-device use cases | Unnecessary for cloud account import |

## Implemented Personal API endpoints

Base: `https://personal.connectapi.garmin.com`

| Method and path | Purpose |
|---|---|
| POST `/personalapitoken-service/personal-api/personal-api-key/token` | Header `Authorization: ApiKey …`; returns a **raw JWT string**, not a token JSON object |
| GET `/activitylist-service/activities/search/activities` | Paged activity list, `start`, `limit`, `startDate`, `endDate` |
| GET `/activity-service/activity/{activityId}` | Detail with `activityTypeDTO` and `summaryDTO` |
| GET `/download-service/files/activity/{activityId}` | Original ZIP containing FIT, decoded with Garmin's official FIT SDK |
| GET `/sleep-service/sleep/dailySleep?date=YYYY-MM-DD` | Top-level `DailySleepDTO`: `sleepTimeSeconds`, `sleepScores.overall.value` |
| GET `/hrv-service/hrv/{calendarDate}` | `hrvSummary.lastNightAvg` |
| GET `/wellness-service/wellness/dailyHeartRate?date=YYYY-MM-DD` | `restingHeartRate`; original full response is archived |

API keys have configurable expiration (30/90/180 days or one year). Access tokens last up to 24 hours; there is no refresh token. A token is renewed when within one hour of expiry and once on a 401. No Garmin password is collected.

Activity detail's `summaryDTO.averageRunCadence` is documented in **steps/minute**. The activity-list counterpart is `averageRunningCadenceInStepsPerMinute`. FIT record cadence follows FIT cycle cadence; for a running activity, record cadence including fractional cadence is doubled to show steps/minute. Original fields and source files remain intact.

Sleep quality and HRV are nullable and depend on actual device recordings. Removing the watch during work leaves daytime gaps. The app does not invent Garmin Training Readiness, recovery time, training status or training-load values. Additional documented body-battery, stress, daily-summary and oxygen endpoints could be added later. Personal API has no documented workout-publishing route, so pushing the plan to the watch is outside this MVP.

## Weekly chart history (0.3.0)

The first Garmin sync after this update widens the existing paginated activity-list query to the Monday eleven weeks before the current week. One preceding date is included as a guard around account/UTC date boundaries. The chart itself groups recorded start instants in Perth time. Subsequent imports use the existing six-day overlap and offline catch-up window.

Records older than the normal detailed-import window use only the documented list response; its original JSON is archived. Existing local or cloud activity rows are preserved rather than overwritten by a thinner summary. Recent records retain the existing detail/FIT path. Import coverage start/end is committed only after the full list is saved, before the independent wellness phase. A failed or partial import cannot turn older unknown weeks into zeros. Live backfill still requires the athlete's phone/key; no key is used in development tests.

## Primary sources

- https://developer.garmin.com/gc-developer-program/overview/
- https://developer.garmin.com/gc-developer-program/personal/personal-api/
- https://developer.garmin.com/gc-developer-program/personal/personal-faq/
- https://developer.garmin.com/gc-developer-program/personal/swagger-docs/
- https://github.com/garmin/connect-personal-api-python (Garmin's official reference client; consulted, not copied)
- https://github.com/garmin/fit-java-sdk (official decoder dependency)
- https://support.garmin.com/en-IN/?faq=JToBEy0jfe6pIygark2Ui5 (Health Connect sharing list)
