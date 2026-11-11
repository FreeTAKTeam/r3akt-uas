# Pages and radar: emulator evidence (11 November 2026)

Scope: the six pages opened from the cockpit (connection/setup, map, radar, observations, sessions/recordings, settings) on branch `feat/screens-radar`. **Implemented but unproven**: run only with the `sim` build on the API 35 x86_64 emulator. No aircraft, no hub, no publish build on a device. The test phones were not used because each holds the `dji4Net` field-trial build under the same application id.

## Checks

- `scripts/check.sh`: Rust format, clippy, tests, fixture, boundaries and docs pass.
- `./gradlew :app:assembleSimDebug :app:assembleDji4Debug :app:testSimDebugUnitTest` and `-Pr3aktPublish=true :app:assembleSimNetDebug :app:assembleDji4NetDebug` build. JVM tests: `RadarLogicTest` 6 (cases in `fixtures/radar/`), `MapModelTest` 6, `SessionRecordTest` 3 (reads `fixtures/flights/sim-session-v1.jsonl`); none fail.
- CI guards by hand: no `publish`/`marker` UniFFI symbols in the `sim` or `dji4` native library (both ABIs); the `sim` APK requests no INTERNET.

## Operator view (emulator, `sim`)

| Screenshot | Shows |
|---|---|
| [menu](m4-pages-emu-menu.png) | Cockpit menu with RADAR, SETUP, HIST, MAP, OBS, LOG; the small radar draws simulated contacts and the saved range |
| [radar](m4-pages-emu-radar.png) | 500 m range, linear rings, "LIMITED · 50 OF 62", count beyond range, "SIMULATED AIRCRAFT", "NORTH-UP · NO HEADING" (the simulator reports no heading) |
| [overlap chooser](m4-pages-emu-radar-chooser.png) | Two simulated contacts about 10 m apart: a tap offers both (UAS-RAD-005) |
| [details](m4-pages-emu-radar-details.png) | Distance, true bearing, source, and position, contact and reception ages shown separately (UAS-RAD-006) |
| [map](m4-pages-emu-map.png) | North-up local plot, no basemap (labelled), session track, simulated contacts, scale bar |
| [setup](m4-pages-emu-setup.png) | Checklist: build, phone, permissions, aircraft; DJI, video and hub "NOT IN THIS BUILD" |
| [sessions](m4-pages-emu-hist.png) | The current session record; recordings say they are not built |
| [observations](m4-pages-emu-obs.png) | Detection not built; markers need a publish build |
| [settings](m4-pages-emu-settings.png) | Radar default range; read-only build configuration; key never shown |

## Not shown

- Contacts from any other identity: the app has no inbound message path (G-17). The radar and map show only the simulator's contacts and this app's own markers; revocation (UAS-RAD-007) cannot be exercised.
- Heading-up rotation with a real heading (fixture tests only).
- The publish builds' pages (setup hub rows, observations marker list, radar pick, D-10 (b)) on a device or against a hub.
- Any DJI row of the setup page with an aircraft connected.
- New local storage (UAS-VID-009): the session record in `filesDir/sessions/`, newest 50 sessions, 512 KiB each; its need is stated in `SessionLog.kt` and `fixtures/flights/README.md`.
