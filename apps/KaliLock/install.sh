#!/usr/bin/env bash
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERIAL="${1:-${KALILOCK_ADB:-}}"
[[ -n "$SERIAL" ]] || { echo 'Aufruf: install.sh ADB-SERIENNUMMER (adb devices -l)' >&2; exit 2; }
adb -s "$SERIAL" get-state
bash "$BASE/build.sh" install
# Update only: never uninstall or clear existing registration/password data.
adb -s "$SERIAL" install --no-incremental -r -f "$BASE/build/install/KaliLock.apk"
# Direct Boot requires internal storage even on phones allowing all apps on SD.
INSTALLED_PATH="$(adb -s "$SERIAL" shell pm path de.nethunter.flipperunified | tr -d '\r')"
if [[ "$INSTALLED_PATH" == *'/mnt/expand/'* ]]; then
 adb -s "$SERIAL" shell su -c 'pm move-package de.nethunter.flipperunified internal'
 INSTALLED_PATH="$(adb -s "$SERIAL" shell pm path de.nethunter.flipperunified | tr -d '\r')"
fi
[[ "$INSTALLED_PATH" == package:/data/app/* ]] || { echo 'Kari Nethunter muss intern installiert sein' >&2; exit 1; }
adb -s "$SERIAL" shell am start -W -n de.nethunter.flipperunified/de.nethunter.flippernfc.MainActivity
