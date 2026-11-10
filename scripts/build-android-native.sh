#!/usr/bin/env bash
# Builds the thin Rust integration for Android ABIs and generates its Kotlin
# bindings with UniFFI. Usage: build-android-native.sh OUT_DIR [FEATURES]
# FEATURES is a comma-separated Cargo feature list (for example: publish).
# Produces OUT_DIR/jniLibs/<abi>/libr3akt_uas_app.so and OUT_DIR/kotlin/.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=$(mkdir -p "$1" && cd "$1" && pwd)
features=${2:-}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
ndk=${ANDROID_NDK_HOME:-$sdk/ndk/27.0.12077973}
# NDK ships x86_64 host toolchains only (Apple silicon runs them via Rosetta).
host=$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64
target_dir=${CARGO_TARGET_DIR:-$root/target}
bin=$ndk/toolchains/llvm/prebuilt/$host/bin
api=26
[ -d "$bin" ] || { echo "NDK toolchain not found at $bin" >&2; exit 1; }

for pair in aarch64-linux-android:arm64-v8a x86_64-linux-android:x86_64; do
  target=${pair%%:*}
  abi=${pair##*:}
  upper=$(echo "$target" | tr '[:lower:]-' '[:upper:]_')
  export "CARGO_TARGET_${upper}_LINKER=$bin/${target}${api}-clang"
  # NDK 27 links for 4 KB pages by default; Android 15+ devices may use 16 KB.
  export "CARGO_TARGET_${upper}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384"
  # C code in dependencies (cc crate) needs the NDK compiler and archiver too.
  under=$(echo "$target" | tr '-' '_')
  export "CC_${under}=$bin/${target}${api}-clang" "AR_${under}=$bin/llvm-ar"
  cargo build --manifest-path "$root/Cargo.toml" -p r3akt-uas-app --release --locked --target "$target" \
    ${features:+--features "$features"}
  mkdir -p "$out/jniLibs/$abi"
  cp "$target_dir/$target/release/libr3akt_uas_app.so" "$out/jniLibs/$abi/"
done

rm -rf "$out/kotlin"
cargo run --manifest-path "$root/Cargo.toml" --locked -q -p r3akt-uas-uniffi-bindgen -- \
  generate --library "$out/jniLibs/arm64-v8a/libr3akt_uas_app.so" \
  --language kotlin --no-format \
  --config "$root/crates/r3akt-uas-app/uniffi.toml" \
  --out-dir "$out/kotlin"
