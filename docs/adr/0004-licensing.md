# ADR 0004: Licensing

**Decision.** Original code is EPL-2.0 (`LICENSE`), matching RCH and R3AKTClient. Copied or adapted third-party material keeps its notice in `THIRD_PARTY_NOTICES.md` and its file headers. The proprietary DJI SDK is a binary dependency of the `dji4` variant only and is never redistributed in source form.

**Consequences.** Each import from FreeTAKUAS (MIT, with some Apache-2.0 TensorFlow files) records its file, revision and licence before it lands.
