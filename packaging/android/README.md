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

## RCH publish test build (milestone 2a)

The `simNet` variant publishes the simulator to an RCH hub. It is built only on request and never by CI; the same build then contains no `sim` or `dji4` variant. Hub settings come from Gradle properties outside the repository:

```bash
./gradlew -Pr3aktPublish=true -Pr3aktHubAddress=<host>:4242 -Pr3aktHubDestination=<hub lxmf.delivery hash> :app:assembleSimNetDebug
```

Publishing starts from LOG → START PUBLISH and stops when the cockpit leaves the foreground. The hub operator must grant `mission.content.write` to the app destination hash shown in the log before fixes are applied.
