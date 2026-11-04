#!/usr/bin/env bash
# Existing, temporary device-tested hooks. No installation, flashing or autostart.
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CHECK=0
if [[ "${1:-}" == --check ]]; then CHECK=1; shift; fi
SERIAL="${1:-${KALILOCK_ADB:-}}"
[[ -n "$SERIAL" ]] || { echo 'Aufruf: start-live.sh [--check] ADB-SERIENNUMMER' >&2; exit 2; }
FRIDA="${FRIDA_BIN:-}"
if [[ -z "$FRIDA" ]]; then FRIDA="$(command -v frida || true)"; fi
if [[ -z "$FRIDA" && -x "$HOME/.cache/kalilock-frida-live/venv/bin/frida" ]]; then
 FRIDA="$HOME/.cache/kalilock-frida-live/venv/bin/frida"
fi
[[ -n "$FRIDA" && -x "$FRIDA" ]] || { echo 'Frida CLI fehlt (FRIDA_BIN setzen)' >&2; exit 1; }
command -v adb >/dev/null
for script in screenoff-v4.js dispatch-live.js; do test -f "$BASE/$script"; done
adb -s "$SERIAL" get-state
PID="$(adb -s "$SERIAL" shell pidof com.android.nfc | tr -d '\r')"
[[ "$PID" =~ ^[0-9]+$ ]] || { echo 'NFC-PID nicht eindeutig' >&2; exit 1; }
echo "Frida CLI: $("$FRIDA" --version); NFC-PID: $PID"
if [[ "$CHECK" == 1 ]]; then
 echo 'Startbedingungen geprüft; keine Hooks geladen. Passender Frida-Server muss bereits laufen.'
 exit 0
fi
LOG="${KALILOCK_LIVE_LOG:-$HOME/.cache/kalilock-frida-live/combined-session.log}"
mkdir -p -- "$(dirname -- "$LOG")"
echo 'Beide Live-Hooks starten. Vorherige Sitzungen vorher beenden. Stoppen: Strg+C.'
echo 'Kein permanenter Patch; nach NFC-/Handyneustart neu starten.'
exec "$FRIDA" -D "$SERIAL" -p "$PID" -q -t inf -l "$BASE/screenoff-v4.js" -l "$BASE/dispatch-live.js" -o "$LOG"
