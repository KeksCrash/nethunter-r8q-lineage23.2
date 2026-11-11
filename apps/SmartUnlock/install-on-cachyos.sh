#!/usr/bin/env bash
set -euo pipefail
[[ -f "$HOME/.android/debug.keystore" ]] || { echo 'Debug-Keystore fehlt';exit 1; }
gradle --no-daemon :app:assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
[[ -s "$APK" ]] || exit 1
apksigner verify --print-certs "$APK"
adb install -r "$APK"
