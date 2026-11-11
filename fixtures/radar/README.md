# Radar fixtures

Text case files for the radar arithmetic in
`apps/android/app/src/main/kotlin/org/r3akt/uas/screens/RadarLogic.kt`, run on the JVM by
`RadarLogicTest` (`cd apps/android && ./gradlew :app:testSimDebugUnitTest`). One case per line;
`#` starts a comment; the first comment line of each file names its columns.

| File | Cases | Requirements |
|---|---|---|
| `projection.txt` | cardinal directions, heading-up rotation, heading wrap at 359 → 0, missing heading (north-up); each case also round-trips through the inverse used by the radar pick | UAS-RAD-001, UAS-RAD-008 |
| `range.txt` | range boundaries at 500 m, 1, 2 and 5 km | UAS-RAD-002 |
| `nearest.txt` | 50-contact limit, limited flag, count beyond the range | UAS-RAD-003 |
| `hit.txt` | tap selection, overlapping contacts (chooser order), touch boundary | UAS-RAD-005 |
| `aged.txt` | stale states: aged after 120 s or with an unknown position time; own markers never aged | UAS-RAD-007 |
| `format.txt` | ages (unknown is never 0), ring and distance labels, true bearing | UAS-RAD-002, UAS-RAD-006 |

Range and bearing are inputs: the app takes them from Android's `Location.distanceBetween`
(gap G-13), so no case here tests Earth geometry. Not covered: revocation (the app has no
inbound message path, gap G-17), missing aircraft position (the page draws no contacts) and
on-device drawing.
