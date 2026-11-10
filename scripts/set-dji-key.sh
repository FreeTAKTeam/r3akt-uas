#!/usr/bin/env bash
# Store the DJI app key as the djiApiKey Gradle property, outside the repository.
set -euo pipefail
props="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

read -rsp "DJI app key (input hidden): " key
echo
[[ "$key" =~ ^[A-Za-z0-9]+$ ]] || { echo "Key must be letters and digits only; nothing written." >&2; exit 1; }

mkdir -p "$(dirname "$props")"
touch "$props"
tmp="$(mktemp "$props.XXXXXX")"
chmod 600 "$tmp"
grep -v '^djiApiKey=' "$props" > "$tmp" || true
echo "djiApiKey=$key" >> "$tmp"
mv "$tmp" "$props"
echo "Saved djiApiKey to $props. Build with: (cd apps/android && ./gradlew :app:assembleDji4Debug)"
