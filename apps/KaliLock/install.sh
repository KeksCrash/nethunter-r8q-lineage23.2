#!/usr/bin/env bash
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERIAL="${1:-${KALILOCK_ADB:-}}"
[[ -n "$SERIAL" ]] || { echo 'Aufruf: install.sh ADB-SERIENNUMMER (adb devices -l)' >&2; exit 2; }
adb -s "$SERIAL" get-state
bash "$BASE/build.sh" install
# Update only: never uninstall or clear existing registration/password data.
adb -s "$SERIAL" install --no-incremental -r "$BASE/build/install/KaliLock.apk"
adb -s "$SERIAL" shell am start -W -n de.nethunter.flipperunified/de.nethunter.flippernfc.MainActivity
