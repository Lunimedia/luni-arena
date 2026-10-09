#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
python3 tools/fetch_core.py
version=8.11.1
if [ ! -x "tools/gradle-$version/bin/gradle" ]; then
  curl -fL "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "tools/gradle-$version-bin.zip"
  unzip -q "tools/gradle-$version-bin.zip" -d tools
  rm "tools/gradle-$version-bin.zip"
fi
"tools/gradle-$version/bin/gradle" --no-daemon assembleDebug
printf 'APK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
