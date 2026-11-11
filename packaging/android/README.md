# Android packaging

Release signing (milestone 6, UAS-PRD-011) reads the keystore from the environment, never from the repository:

| Variable | Meaning |
|---|---|
| `R3AKT_UAS_KEYSTORE` | path to the release keystore |
| `R3AKT_UAS_KEYSTORE_PASSWORD`, `R3AKT_UAS_KEY_ALIAS`, `R3AKT_UAS_KEY_PASSWORD` | keystore secrets |

The scaffold produces debug APKs only. `r3akt-uas.example.properties` shows the planned runtime settings; it contains no credentials.

## DJI app key

The `dji4` variant registers with DJI only when it is built with an app key issued by the DJI developer site for package `org.r3akt.uas`. Put it in `~/.gradle/gradle.properties`, never in this repository:

```properties
djiApiKey=<key from developer.dji.com>
```

The first registration needs network access once. Without a key the cockpit shows `NO DJI KEY` and nothing connects.

## RCH publish test builds (`simNet`, `dji4Net`)

The `simNet` variant publishes the simulator to an RCH hub; `dji4Net` publishes a real DJI aircraft (D-18). They are built only with `-Pr3aktPublish=true`, and that build then contains no `sim` or `dji4` variant. CI builds them without a key or hub settings, only to check that they compile. Hub settings come from Gradle properties outside the repository:

```bash
./gradlew -Pr3aktPublish=true -Pr3aktHubAddress=<host>:4242 -Pr3aktHubDestination=<hub lxmf.delivery hash> [-Pr3aktTeamUid=<RCH canonical team UID>] :app:assembleSimNetDebug   # or :app:assembleDji4NetDebug, which also needs djiApiKey
```

Publishing starts from LOG → START PUBLISH and stops when the cockpit screen pauses, but not when DJI's controller screen opens; the status bar then shows `PUBLISH STOPPED` (D-16). Each variant keeps its own identity and store, so the DJI aircraft is a separate object from the simulator on the same phone. `dji4Net` sends a fix only with DJI GPS signal level 3 or better, and without accuracy. Its altitude is take-off-relative, so it travels as an unknown datum. PLACE AT CAMERA is refused because DJI SDK 4 reports no position accuracy (D-15). The hub operator must grant `mission.content.write` to the app destination hash shown in the log before fixes are applied. With a team, the operator also adds that destination hash, and each REM client's destination hash, as members of the team (create the team by its canonical UID); the hub then relays the aircraft to those REM clients.
