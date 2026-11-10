# Android packaging

Release signing (milestone 6, UAS-PRD-011) reads the keystore from the environment, never from the repository:

| Variable | Meaning |
|---|---|
| `R3AKT_UAS_KEYSTORE` | path to the release keystore |
| `R3AKT_UAS_KEYSTORE_PASSWORD`, `R3AKT_UAS_KEY_ALIAS`, `R3AKT_UAS_KEY_PASSWORD` | keystore secrets |

The scaffold produces debug APKs only. `r3akt-uas.example.properties` shows the planned runtime settings; it contains no credentials.
