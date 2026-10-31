#!/system/bin/sh
# BLE-GATT → TCP Bridge for NetHunter (Android-side, runs as root)
# Bridges internal QCA6390 BLE to a TCP socket for the Kali chroot.
#
# Usage:
#   ble-bridge.sh                     # scan for BLE OBD adapters
#   ble-bridge.sh scan                # same as above
#   ble-bridge.sh AA:BB:CC:DD:EE:FF   # connect to specific device
#   ble-bridge.sh AA:BB:CC:DD:EE:FF 0x000d 0x000b  # custom RX/TX handles
#
# Default GATT handles are for ELM327-compatible BLE OBD adapters:
#   Service UUID: 0000fff0-0000-1000-8000-00805f9b34fb
#   TX (write):   0000fff1 → handle varies per device
#   RX (notify):  0000fff2 → handle varies per device

BRIDGE_PORT="${BLE_BRIDGE_PORT:-35000}"
BUSYBOX="/system/bin/busybox_nh"
[ ! -x "$BUSYBOX" ] && BUSYBOX="busybox"
GATTTOOL=""
HCITOOL=""
NC=""
PIDFILE="/data/local/tmp/ble-bridge.pid"

log() { echo "[ble-bridge] $*"; }

find_tools() {
    for dir in /system/bin /system/xbin /data/local/nhsystem/kali-arm64/usr/bin; do
        [ -z "$GATTTOOL" ] && [ -x "$dir/gatttool" ] && GATTTOOL="$dir/gatttool"
        [ -z "$HCITOOL" ] && [ -x "$dir/hcitool" ] && HCITOOL="$dir/hcitool"
        [ -z "$NC" ] && [ -x "$dir/nc" ] && NC="$dir/nc"
    done
    [ -z "$GATTTOOL" ] && GATTTOOL=$(which gatttool 2>/dev/null)
    [ -z "$HCITOOL" ] && HCITOOL=$(which hcitool 2>/dev/null)
    [ -z "$NC" ] && NC=$(which nc 2>/dev/null)
    [ -z "$NC" ] && [ -x "$BUSYBOX" ] && NC="$BUSYBOX nc"
}

check_tools() {
    find_tools
    local missing=""
    [ -z "$GATTTOOL" ] && missing="$missing gatttool"
    [ -z "$HCITOOL" ] && missing="$missing hcitool"
    [ -z "$NC" ] && missing="$missing nc"
    if [ -n "$missing" ]; then
        log "FEHLT:$missing"
        log "Installiere BlueZ-Tools im Chroot: apt install bluez"
        log "Oder kopiere gatttool/hcitool nach /system/bin/"
        return 1
    fi
    return 0
}

cleanup() {
    log "Aufräumen..."
    [ -n "$LISTEN_PID" ] && kill "$LISTEN_PID" 2>/dev/null
    [ -n "$NC_PID" ] && kill "$NC_PID" 2>/dev/null
    rm -f "$PIDFILE"
    exit 0
}

do_scan() {
    log "BLE-Scan starten (10 Sekunden)..."
    rfkill unblock bluetooth 2>/dev/null

    local hci_dev=""
    if command -v hciconfig >/dev/null 2>&1; then
        hci_dev=$(hciconfig 2>/dev/null | grep "^hci" | head -1 | cut -d: -f1)
    fi
    [ -z "$hci_dev" ] && hci_dev="hci0"

    hciconfig "$hci_dev" up 2>/dev/null

    log "Suche BLE-Geräte auf $hci_dev..."
    timeout 12 $HCITOOL -i "$hci_dev" lescan 2>/dev/null &
    SCAN_PID=$!
    sleep 10
    kill "$SCAN_PID" 2>/dev/null
    wait "$SCAN_PID" 2>/dev/null

    log ""
    log "OBD-typische Gerätenamen: OBDII, ELM327, OBDLink, Vgate, V-LINK,"
    log "  Carista, KONNWEI, iCar, Veepeak, LELink, Bluetooth_LE"
    log ""
    log "Verbinden: ble-bridge.sh <MAC-Adresse>"
}

discover_handles() {
    local mac="$1"
    log "GATT-Handles von $mac ermitteln..."

    local chars
    chars=$($GATTTOOL -b "$mac" --characteristics 2>/dev/null)
    if [ -z "$chars" ]; then
        log "Keine GATT-Characteristics gefunden. Gerät erreichbar?"
        return 1
    fi

    echo "$chars"

    # ELM327/OBD-typical: fff1 = TX (write), fff2 = RX (notify)
    local tx_handle rx_handle
    tx_handle=$(echo "$chars" | grep -i "fff1" | head -1 | sed 's/.*value handle = //' | awk '{print $1}')
    rx_handle=$(echo "$chars" | grep -i "fff2" | head -1 | sed 's/.*value handle = //' | awk '{print $1}')

    if [ -n "$tx_handle" ] && [ -n "$rx_handle" ]; then
        log "ELM327-OBD Handles gefunden: TX=$tx_handle RX=$rx_handle"
        echo "$rx_handle $tx_handle"
        return 0
    fi

    log "Standard OBD-Handles (fff1/fff2) nicht gefunden."
    log "Alle Characteristics oben angezeigt — gib Handles manuell an:"
    log "  ble-bridge.sh $mac <RX_HANDLE> <TX_HANDLE>"
    return 1
}

do_bridge() {
    local mac="$1"
    local rx_handle="$2"
    local tx_handle="$3"

    trap cleanup INT TERM

    rfkill unblock bluetooth 2>/dev/null
    hciconfig hci0 up 2>/dev/null

    if [ -z "$rx_handle" ] || [ -z "$tx_handle" ]; then
        local handles
        handles=$(discover_handles "$mac")
        local last_line
        last_line=$(echo "$handles" | tail -1)
        rx_handle=$(echo "$last_line" | awk '{print $1}')
        tx_handle=$(echo "$last_line" | awk '{print $2}')
    fi

    if [ -z "$rx_handle" ] || [ -z "$tx_handle" ]; then
        log "Kann keine GATT-Handles ermitteln. Abbruch."
        return 1
    fi

    log "Verbinde mit $mac (RX=$rx_handle TX=$tx_handle)..."
    log "TCP-Bridge auf 127.0.0.1:$BRIDGE_PORT"

    echo $$ > "$PIDFILE"

    # Enable notifications on the RX characteristic
    # CCCD handle is typically rx_handle + 1
    local cccd_handle
    cccd_handle=$(printf "0x%04x" $(($(printf "%d" "$rx_handle") + 1)))

    # Create named pipes for bidirectional communication
    local fifo_in="/data/local/tmp/ble-fifo-in"
    local fifo_out="/data/local/tmp/ble-fifo-out"
    rm -f "$fifo_in" "$fifo_out"
    mkfifo "$fifo_in" "$fifo_out" 2>/dev/null || {
        $BUSYBOX mkfifo "$fifo_in" "$fifo_out"
    }

    # Start gatttool in interactive mode with notification listener
    $GATTTOOL -b "$mac" --char-write-req --handle="$cccd_handle" --value=0100 2>/dev/null
    log "Notifications aktiviert auf $cccd_handle"

    # Background: listen for notifications and pipe to fifo_out
    (
        $GATTTOOL -b "$mac" --listen 2>/dev/null | while IFS= read -r line; do
            case "$line" in
                *"Notification handle"*)
                    # Extract hex value after "value: "
                    val=$(echo "$line" | sed 's/.*value: //')
                    # Convert hex to ASCII
                    echo "$val" | tr -d ' ' | sed 's/../\\x&/g' | xargs printf
                    ;;
            esac
        done > "$fifo_out"
    ) &
    LISTEN_PID=$!

    # TCP server: read from client → write to BLE TX, read from BLE RX → send to client
    while true; do
        log "Warte auf Verbindung auf Port $BRIDGE_PORT..."
        (
            # Read from TCP client, send to BLE device
            while IFS= read -r cmd; do
                [ -z "$cmd" ] && continue
                # Convert ASCII to hex
                local hex
                hex=$(printf '%s' "$cmd" | od -An -tx1 | tr -d ' \n')
                $GATTTOOL -b "$mac" --char-write-req --handle="$tx_handle" \
                    --value="$hex" 2>/dev/null
            done
        ) < "$fifo_in" &
        local WRITER_PID=$!

        # nc: pipe TCP ↔ fifos
        $NC -l -p "$BRIDGE_PORT" < "$fifo_out" > "$fifo_in"
        NC_PID=$!

        kill "$WRITER_PID" 2>/dev/null
        log "Client getrennt, warte auf neue Verbindung..."
    done
}

stop_bridge() {
    if [ -f "$PIDFILE" ]; then
        local pid
        pid=$(cat "$PIDFILE")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            log "Bridge (PID $pid) gestoppt."
        fi
        rm -f "$PIDFILE"
    else
        log "Keine laufende Bridge gefunden."
    fi
}

# --- Main ---

check_tools || exit 1

case "${1:-scan}" in
    scan)
        do_scan
        ;;
    stop)
        stop_bridge
        ;;
    status)
        if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
            log "Bridge läuft (PID $(cat "$PIDFILE")), Port $BRIDGE_PORT"
        else
            log "Keine Bridge aktiv."
        fi
        ;;
    *)
        # First arg is MAC address
        do_bridge "$1" "$2" "$3"
        ;;
esac
