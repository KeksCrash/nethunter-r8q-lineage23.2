#!/bin/bash
# BLE-TCP → Pseudo-TTY Bridge for Kali chroot (runs inside chroot)
# Creates a serial device that tools like python-obd can open.
#
# Usage:
#   ble-connect.sh                    # defaults: port 35000, /tmp/ble-serial
#   ble-connect.sh 35000 /tmp/obd     # custom port and PTY path
#   ble-connect.sh stop               # stop the bridge
#
# After connecting:
#   python3 -c "import obd; c = obd.OBD('/tmp/ble-serial'); print(c.query(obd.commands.RPM))"

BRIDGE_PORT="${1:-${BLE_BRIDGE_PORT:-35000}}"
PTY_PATH="${2:-/tmp/ble-serial}"
PIDFILE="/tmp/ble-connect.pid"

log() { echo "[ble-connect] $*"; }

check_socat() {
    if ! command -v socat >/dev/null 2>&1; then
        log "socat nicht installiert. Installiere..."
        if command -v apt >/dev/null 2>&1; then
            apt update -qq && apt install -y -qq socat
        elif command -v apt-get >/dev/null 2>&1; then
            apt-get update -qq && apt-get install -y -qq socat
        else
            log "FEHLER: Kann socat nicht installieren. Manuell: apt install socat"
            return 1
        fi
    fi
    return 0
}

do_connect() {
    check_socat || exit 1

    # Check if bridge is running on Android side
    if ! (echo > /dev/tcp/127.0.0.1/"$BRIDGE_PORT") 2>/dev/null; then
        log "FEHLER: Keine Bridge auf Port $BRIDGE_PORT erreichbar."
        log "Starte erst auf Android-Seite: su -c 'ble-bridge.sh <BLE_MAC>'"
        exit 1
    fi

    # Kill any existing instance
    do_stop 2>/dev/null

    log "Verbinde mit BLE-Bridge auf 127.0.0.1:$BRIDGE_PORT..."
    log "Pseudo-TTY: $PTY_PATH"

    rm -f "$PTY_PATH"

    socat \
        "pty,raw,echo=0,link=$PTY_PATH,mode=666" \
        "tcp:127.0.0.1:$BRIDGE_PORT" &
    local pid=$!

    echo "$pid" > "$PIDFILE"

    sleep 1
    if kill -0 "$pid" 2>/dev/null && [ -e "$PTY_PATH" ]; then
        log "Verbunden! Serielles Device: $PTY_PATH"
        log ""
        log "Beispiele:"
        log "  # Direkt testen (ELM327):"
        log "  echo 'ATZ' > $PTY_PATH && cat $PTY_PATH"
        log ""
        log "  # python-obd:"
        log "  python3 -c \"import obd; c=obd.OBD('$PTY_PATH'); print(c.query(obd.commands.RPM))\""
        log ""
        log "  # Screen/Minicom:"
        log "  screen $PTY_PATH 115200"
        log ""
        log "  # Stoppen:"
        log "  ble-connect.sh stop"
        log ""

        # Wait for socat to exit
        wait "$pid" 2>/dev/null
        log "Verbindung beendet."
        rm -f "$PIDFILE" "$PTY_PATH"
    else
        log "FEHLER: socat konnte keine Verbindung aufbauen."
        kill "$pid" 2>/dev/null
        rm -f "$PIDFILE"
        exit 1
    fi
}

do_stop() {
    if [ -f "$PIDFILE" ]; then
        local pid
        pid=$(cat "$PIDFILE")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            log "Bridge (PID $pid) gestoppt."
        fi
        rm -f "$PIDFILE" "$PTY_PATH"
    else
        # Try to find socat processes for our PTY
        local pids
        pids=$(pgrep -f "socat.*$PTY_PATH" 2>/dev/null)
        if [ -n "$pids" ]; then
            echo "$pids" | xargs kill 2>/dev/null
            log "Bridge gestoppt."
        else
            log "Keine laufende Bridge gefunden."
        fi
    fi
}

do_status() {
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
        log "Bridge läuft (PID $(cat "$PIDFILE"))"
        log "PTY: $PTY_PATH"
        [ -e "$PTY_PATH" ] && log "Device existiert: $(ls -la "$PTY_PATH")"
    else
        log "Keine Bridge aktiv."
    fi

    if (echo > /dev/tcp/127.0.0.1/"$BRIDGE_PORT") 2>/dev/null; then
        log "Android-Bridge auf Port $BRIDGE_PORT: ERREICHBAR"
    else
        log "Android-Bridge auf Port $BRIDGE_PORT: NICHT ERREICHBAR"
    fi
}

# --- Main ---

case "${1:-connect}" in
    stop)
        do_stop
        ;;
    status)
        do_status
        ;;
    connect)
        do_connect
        ;;
    [0-9]*)
        # First arg is port number
        do_connect
        ;;
    *)
        echo "Usage: ble-connect.sh [connect|stop|status] [PORT] [PTY_PATH]"
        echo "       ble-connect.sh 35000 /tmp/ble-serial"
        ;;
esac
