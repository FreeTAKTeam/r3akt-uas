# Third-party notices

Original code in this repository is licensed under EPL-2.0 (see `LICENSE`). The following third-party material is used.

## Adapted into this repository

### FreeTAKUAS

- Source: https://github.com/FreeTAKTeam/FreeTAKUAS at `704f4aa3ba127ed4c090da2e30240971b6c55b55`
- Licence: MIT. Some FreeTAKUAS files are TensorFlow examples under Apache-2.0; their headers are kept if imported.
- Used: DJI SDK native-library packaging rules (`doNotStrip`/`pickFirst` lists, excludes) in `apps/android/app/build.gradle.kts`.

```
MIT License

Copyright (c) 2021 niccellular

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Dependencies (not redistributed in source form)

| Component | Licence | Used by |
|---|---|---|
| r3akt-map-core (Reticulum Community Hub) | EPL-2.0 | `r3akt-uas-app` |
| UniFFI 0.28.3 | MPL-2.0 | `r3akt-uas-app`, `tools/uniffi-bindgen` |
| JNA 5.14.0 | Apache-2.0 OR LGPL-2.1-or-later | `native-bridge` |
| DJI Mobile SDK 4.18 | DJI SDK licence (proprietary) | `dji4` variant only |
| AndroidX AppCompat, ConstraintLayout | Apache-2.0 | `dji4` variant only |

The full transitive Rust dependency list is in `Cargo.lock`; `cargo deny`/`cargo about` reporting is a milestone 6 release task.
