# Interface refresh

Goal: modernise the native Android app, offer dark and light appearance, and remove explanatory clutter from the four daily screens.

Approach: retain Java/platform views and the existing repository, sync, matching and credential storage. A charcoal/teal dark theme is the default; Settings also offers Light and Follow phone. Custom rounded controls and icon navigation replace platform-grey buttons. No new runtime dependencies.

- [x] Add coherent dark/light palettes and persistent appearance selection. Keep dialogs, inputs, charts and system bars readable in each mode.
- [x] Reorder Today around the next workout; compact the event goal. Simplify Plan, activity cards and Insights. Keep dates, units, optional/completed states, missing measurements, actionable warnings and pending feedback visible.
- [x] Move revision history, import diagnostics, sync timestamps and data coverage explanations into Settings/detail views. Preserve all connection editing, manual matching and feedback actions.
- [x] Run core/backend tests, Android JSON compatibility audit, APK build and lint. Review theme switching, narrow screens, scrolling dialogs, unavailable metrics, sync warnings and state preservation.
- [x] Review the full diff, publish source to the private GitHub repository, and package a same-signer update APK plus ZIP fallback.

Training prescriptions, Garmin API calls, cloud data and authentication are outside this presentation change. The installed app must retain its encrypted connections, local records and pending feedback when updated.

Verification: existing behavioural tests plus Android build/lint and a visual check where runtime support is available. Do not add tests that merely duplicate colour or layout declarations.
