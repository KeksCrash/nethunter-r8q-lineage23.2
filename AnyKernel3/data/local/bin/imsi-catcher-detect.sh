#!/system/bin/sh
# IMSI Catcher Detection + Nearby Device/IMSI Monitor v2
# Works WITH and WITHOUT SIM card
# Uses: diag (SnoopSnitch), telephony, WiFi (scan + monitor), BT, ARP, tcpdump
# Run as root: su -c sh /data/local/bin/imsi-catcher-detect.sh [mode]

DATADIR="/data/local/bin"
LOG="$DATADIR/imsi-catcher-log.txt"
DEVICES_DB="$DATADIR/nearby-devices.csv"
IMSI_DB="$DATADIR/imsi-nearby.csv"
WATCHLIST="$DATADIR/imsi-watchlist.txt"
PROBE_LOG="$DATADIR/wifi-probes.csv"
INTERVAL=30
HAS_SIM=""
HAS_DIAG=""
HAS_MONITOR=""

RED='\033[0;31m'
YEL='\033[1;33m'
GRN='\033[0;32m'
CYN='\033[0;36m'
MAG='\033[0;35m'
WHT='\033[1;37m'
NC='\033[0m'

log()       { local m="$(date '+%Y-%m-%d %H:%M:%S') $1"; echo -e "$m"; echo "$m" >> "$LOG" 2>/dev/null; }
alert()     { log "${RED}[ALERT] $1${NC}"; vibrate 500; }
warn()      { log "${YEL}[WARN] $1${NC}"; }
info()      { log "${GRN}[INFO] $1${NC}"; }
scan()      { log "${CYN}[SCAN] $1${NC}"; }
watch_hit() { log "${MAG}[WATCHLIST] $1${NC}"; vibrate 200; sleep 0.3; vibrate 200; sleep 0.3; vibrate 200; }
header()    { log "${WHT}======= $1 =======${NC}"; }

vibrate() { service call vibrator 1 i64 "$1" 2>/dev/null; }

# ===== Watchlist =====
init_watchlist() { [ ! -f "$WATCHLIST" ] && touch "$WATCHLIST"; }

add_to_watchlist() {
    local id="$1" label="${2:-unknown}" type="${3:-any}"
    init_watchlist
    if grep -q "^${id}|" "$WATCHLIST" 2>/dev/null; then
        echo "Already on watchlist: $id"; return
    fi
    echo "${id}|${label}|${type}|$(date '+%Y-%m-%d %H:%M:%S')" >> "$WATCHLIST"
    echo "Added: $id ($label, $type)"
}

list_watchlist() {
    init_watchlist
    echo "=== Watchlist ==="
    [ ! -s "$WATCHLIST" ] && echo "(empty)" && return
    printf "%-20s %-20s %-10s %s\n" "ID" "LABEL" "TYPE" "ADDED"
    while IFS='|' read -r id label type added; do
        printf "%-20s %-20s %-10s %s\n" "$id" "$label" "$type" "$added"
    done < "$WATCHLIST"
}

check_watchlist() {
    local id="$1" source="$2"
    [ ! -s "$WATCHLIST" ] && return 1
    local match=$(grep "^${id}|" "$WATCHLIST" 2>/dev/null)
    if [ -n "$match" ]; then
        local label=$(echo "$match" | cut -d'|' -f2)
        watch_hit "MATCH: $id ($label) via $source!"
        return 0
    fi
    return 1
}

# ===== Device database =====
init_devices_db() {
    [ ! -f "$DEVICES_DB" ] && echo "timestamp,type,id,name,rssi,extra" > "$DEVICES_DB"
    [ ! -f "$IMSI_DB" ] && echo "timestamp,type,imsi_or_id,mcc_mnc,source,extra" > "$IMSI_DB"
    [ ! -f "$PROBE_LOG" ] && echo "timestamp,mac,ssid_probed,rssi,freq" > "$PROBE_LOG"
}

record_device() {
    local type="$1" id="$2" name="$3" rssi="$4" extra="$5"
    echo "$(date '+%Y-%m-%d %H:%M:%S'),$type,$id,$name,$rssi,$extra" >> "$DEVICES_DB"
    check_watchlist "$id" "$type"
    [ -n "$name" ] && [ "$name" != "unknown" ] && [ "$name" != "hidden" ] && check_watchlist "$name" "${type}-name"
}

record_imsi() {
    local type="$1" id="$2" mcc_mnc="$3" source="$4" extra="$5"
    echo "$(date '+%Y-%m-%d %H:%M:%S'),$type,$id,$mcc_mnc,$source,$extra" >> "$IMSI_DB"
    check_watchlist "$id" "$source"
}

record_probe() {
    local mac="$1" ssid="$2" rssi="$3" freq="$4"
    echo "$(date '+%Y-%m-%d %H:%M:%S'),$mac,$ssid,$rssi,$freq" >> "$PROBE_LOG"
    check_watchlist "$mac" "wifi-probe"
}

# ===== Capability detection =====
detect_capabilities() {
    # SIM
    local sim_state=$(getprop gsm.sim.state 2>/dev/null)
    local sim_op=$(getprop gsm.sim.operator.numeric 2>/dev/null)
    if [ "$sim_state" = "READY" ] || [ "$sim_state" = "LOADED" ] && [ -n "$sim_op" ]; then
        HAS_SIM="yes"
    else
        HAS_SIM="no"
    fi

    # Diag
    [ -c /dev/diag ] && HAS_DIAG="yes" || HAS_DIAG="no"

    # WiFi monitor mode — qcacld-3.0 advertises monitor but can't do it
    # parallel with managed. Real monitor needs: wlan0 down, or nexmon patch.
    HAS_MONITOR="no"
    if iw phy phy0 info 2>/dev/null | grep -q "monitor"; then
        # Test if we can actually create a working monitor interface
        iw dev mon0 del 2>/dev/null
        iw phy phy0 interface add mon0 type monitor 2>/dev/null
        if iw dev mon0 info 2>/dev/null | grep -q "type monitor"; then
            HAS_MONITOR="yes"
        fi
        iw dev mon0 del 2>/dev/null
    fi

    info "SIM: $HAS_SIM | /dev/diag: $HAS_DIAG | WiFi monitor: $HAS_MONITOR"
}

# ===== 1. CELL TOWERS (works without SIM — emergency scan) =====
scan_cells() {
    scan "Cell towers (visible to modem)..."
    local found=0

    # Primary: dumpsys telephony.registry
    local tel=$(dumpsys telephony.registry 2>/dev/null)

    # Extract ALL cell identities (serving + neighbors)
    echo "$tel" | grep -oE 'CellIdentity(Gsm|Lte|Wcdma|Nr|Tdscdma):\{[^}]+\}' | sort -u | while read -r cell; do
        local type=$(echo "$cell" | grep -oE 'CellIdentity(Gsm|Lte|Wcdma|Nr|Tdscdma)')
        local mcc=$(echo "$cell" | grep -oE 'mMcc=[0-9]+' | cut -d= -f2)
        local mnc=$(echo "$cell" | grep -oE 'mMnc=[0-9]+' | cut -d= -f2)
        local lac=$(echo "$cell" | grep -oE 'mLac=[0-9]+' | cut -d= -f2)
        local tac=$(echo "$cell" | grep -oE 'mTac=[0-9]+' | cut -d= -f2)
        local cid=$(echo "$cell" | grep -oE 'mCid=[0-9]+' | cut -d= -f2)
        local nci=$(echo "$cell" | grep -oE 'mNci=[0-9]+' | cut -d= -f2)
        local pci=$(echo "$cell" | grep -oE 'mPci=[0-9]+' | cut -d= -f2)
        local arfcn=$(echo "$cell" | grep -oE 'mArfcn=[0-9]+' | cut -d= -f2)
        local earfcn=$(echo "$cell" | grep -oE 'mEarfcn=[0-9]+' | cut -d= -f2)
        local alpha=$(echo "$cell" | grep -oE 'mAlphaLong=[^ }]+' | cut -d= -f2)
        local area="${lac:-$tac}"
        local cellid="${cid:-$nci}"

        [ -z "$mcc" ] || [ "$mcc" = "2147483647" ] && continue

        record_imsi "CELL" "${mcc}/${mnc}/${area}/${cellid}" "${mcc}/${mnc}" "telephony" "type=${type} pci=${pci} arfcn=${arfcn}${earfcn} op=${alpha}"
        scan "  $type MCC=$mcc MNC=$mnc LAC/TAC=$area CID=$cellid PCI=${pci:-?} ARFCN=${arfcn}${earfcn} ${alpha}"
        found=$((found + 1))
    done

    # Signal strengths
    echo "$tel" | grep -oE 'CellSignalStrength(Gsm|Lte|Wcdma|Nr):\{[^}]+\}' | head -5 | while read -r sig; do
        local type=$(echo "$sig" | grep -oE 'CellSignalStrength(Gsm|Lte|Wcdma|Nr)')
        local rssi=$(echo "$sig" | grep -oE 'rssi=-?[0-9]+' | head -1 | grep -oE '\-?[0-9]+')
        local rsrp=$(echo "$sig" | grep -oE 'rsrp=-?[0-9]+' | head -1 | grep -oE '\-?[0-9]+')
        local val="${rsrp:-$rssi}"
        [ -n "$val" ] && [ "$val" != "2147483647" ] && scan "  Signal: $type ${val}dBm"
    done

    # Registration state
    local reg_state=$(echo "$tel" | grep -oE 'mVoiceRegState=[0-9]+' | head -1 | cut -d= -f2)
    local data_state=$(echo "$tel" | grep -oE 'mDataRegState=[0-9]+' | head -1 | cut -d= -f2)
    local emergency=$(echo "$tel" | grep -oE 'mIsEmergencyOnly=[a-z]+' | head -1 | cut -d= -f2)
    info "Registration: voice=$reg_state data=$data_state emergency=$emergency"
}

# ===== 2. DIAG / SnoopSnitch — OTA GSM Layer 3 messages =====
scan_diag_imsi() {
    [ "$HAS_DIAG" != "yes" ] && return

    scan "Qualcomm diag — OTA IMSI/TMSI capture..."

    # Radio logcat for any baseband-level identity messages
    local radio=$(logcat -d -t 600 -b radio 2>/dev/null)
    local main=$(logcat -d -t 600 2>/dev/null)

    # Combine and search for IMSI patterns
    local combined="$radio
$main"

    # IMSIs (exactly 15 digits, NOT part of a longer number/hex string, valid MCC 2xx-9xx)
    echo "$combined" | grep -oE '(^|[^0-9a-fA-Fx])[0-9]{15}([^0-9a-fA-Fx]|$)' | grep -oE '[0-9]{15}' | sort -u | while read -r imsi; do
        local mcc=$(echo "$imsi" | cut -c1-3)
        local mnc=$(echo "$imsi" | cut -c4-5)
        # Skip obvious non-IMSIs: all same digit, sequential, or MCC not in ITU range
        echo "$imsi" | grep -qE '^(.)\1{14}$' && continue
        [ "$mcc" -lt 200 ] 2>/dev/null && continue
        [ "$mcc" -gt 799 ] 2>/dev/null && continue
        record_imsi "IMSI" "$imsi" "${mcc}/${mnc}" "logcat" ""
        scan "  IMSI: $imsi (MCC=$mcc MNC=$mnc)"
    done

    # TMSIs (8 hex digits with 0x prefix or after TMSI keyword)
    echo "$combined" | grep -oE 'TMSI[=: ]*0x[0-9A-Fa-f]{8}' | grep -oE '0x[0-9A-Fa-f]{8}' | sort -u | while read -r tmsi; do
        record_imsi "TMSI" "$tmsi" "" "logcat" ""
        scan "  TMSI: $tmsi"
    done

    # IMSI paging (network sends IMSI instead of TMSI — catcher indicator)
    local paging_imsi=$(echo "$combined" | grep -c -i -E "paging.*IMSI|IMSI.*paging|identity.request")
    [ "$paging_imsi" -gt 0 ] && alert "IMSI PAGING detected ($paging_imsi)! Possible IMSI catcher!"

    # Authentication reject (network rejected — suspicious if unexpected)
    local auth_reject=$(echo "$combined" | grep -c -i -E "auth.*reject|authentication.*failure")
    [ "$auth_reject" -gt 0 ] && warn "Authentication rejects detected ($auth_reject)"

    # Cipher mode (A5/0 = no encryption)
    echo "$combined" | grep -i -E "cipher.*mode|encryption.*algorithm|A5/" | head -5 | while read -r line; do
        echo "$line" | grep -q -i "A5/0\|cipher.*0\|no.*encrypt" && alert "NO ENCRYPTION (A5/0) — IMSI catcher!"
        echo "$line" | grep -q -i "A5/1" && warn "Weak encryption (A5/1)"
        scan "  Cipher: $line"
    done

    # Location area updates (suspicious if frequent without movement)
    local lau=$(echo "$combined" | grep -c -i "location.*update\|tracking.*area.*update")
    [ "$lau" -gt 5 ] && warn "Frequent location updates ($lau) — possible cell hopping attack"

    # SnoopSnitch specific log messages
    echo "$combined" | grep -i "snoopsnitch\|qdmon\|diag_mdlog" | head -5 | while read -r line; do
        scan "  SnoopSnitch: $line"
    done

    info "Diag scan done"
}

# ===== 3. WIFI — scan results + probe capture =====
scan_wifi() {
    scan "WiFi environment..."

    # Trigger fresh scan
    cmd wifi start-scan 2>/dev/null
    sleep 2

    # Scan results (all visible APs)
    local results=$(cmd wifi list-scan-results 2>/dev/null)
    [ -z "$results" ] && results=$(dumpsys wifi 2>/dev/null | sed -n '/Latest Scan Results/,/^$/p')

    local ap_count=0
    echo "$results" | tail -n +2 | while IFS= read -r line; do
        local bssid=$(echo "$line" | awk '{print $1}')
        echo "$bssid" | grep -qE '^[0-9a-f]{2}:' || continue
        local freq=$(echo "$line" | awk '{print $2}')
        local rssi=$(echo "$line" | awk '{print $3}' | grep -oE '\-?[0-9]+' | head -1)
        local age=$(echo "$line" | awk '{print $4}')
        local ssid=$(echo "$line" | awk '{for(i=5;i<=NF;i++) printf "%s ", $i; print ""}' | sed 's/ *$//')
        local flags=$(echo "$line" | grep -oE '\[.*\]')

        record_device "WIFI" "$bssid" "${ssid:-hidden}" "${rssi:-0}" "freq=$freq flags=$flags"
        check_watchlist "$bssid" "wifi-ap"
        [ -n "$ssid" ] && check_watchlist "$ssid" "wifi-ssid"
        ap_count=$((ap_count + 1))
    done

    # Connected stations (if in AP mode via hostapd/swlan0)
    local ap_clients=$(ip neigh show dev swlan0 2>/dev/null | grep -oE '[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}')
    [ -n "$ap_clients" ] && {
        scan "  AP-mode connected clients:"
        echo "$ap_clients" | while read -r mac; do
            record_device "AP-CLIENT" "$mac" "" "0" "via swlan0"
            scan "    Client: $mac"
        done
    }

    # WiFi Direct peers
    local p2p=$(dumpsys wifip2p 2>/dev/null | grep -oE '[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}' | sort -u)
    [ -n "$p2p" ] && {
        echo "$p2p" | while read -r mac; do
            record_device "P2P" "$mac" "" "0" ""
            scan "  WiFi-Direct peer: $mac"
        done
    }

    # ARP table
    scan "  ARP neighbors..."
    cat /proc/net/arp 2>/dev/null | grep -v "IP address" | while read -r ip _ _ mac _ iface; do
        [ "$mac" = "00:00:00:00:00:00" ] && continue
        record_device "ARP" "$mac" "$ip" "0" "iface=$iface"
        scan "    ARP: $mac ($ip) on $iface"
        check_watchlist "$ip" "arp-ip"
    done

    info "WiFi scan done"
}

# ===== 4. WiFi PROBE REQUEST capture (detects all searching devices) =====
scan_wifi_probes() {
    [ "$HAS_MONITOR" != "yes" ] && {
        scan "WiFi probes: monitor mode not available (Qualcomm internal WiFi can't do parallel monitor)"
        scan "  → Install nexmon, or run: $0 --monitor-scan (disconnects WiFi for 15s)"
        return
    }

    # Check if mon0 already exists
    local mon_existed="no"
    ip link show mon0 2>/dev/null && mon_existed="yes"

    if [ "$mon_existed" = "no" ]; then
        iw phy phy0 interface add mon0 type monitor 2>/dev/null
        ip link set mon0 up 2>/dev/null
        sleep 1
    fi

    if ! ip link show mon0 2>/dev/null | grep -q "UP"; then
        scan "WiFi probes: could not bring up monitor interface"
        return
    fi

    # Set channel to match wlan0 (most devices probe on the channel they're near)
    local cur_freq=$(iw dev wlan0 info 2>/dev/null | grep -oE 'channel [0-9]+' | awk '{print $2}')
    [ -n "$cur_freq" ] && iw dev mon0 set channel "$cur_freq" 2>/dev/null

    scan "WiFi probe capture (10s on mon0, ch ${cur_freq:-auto})..."

    # Capture probe requests with tcpdump (type mgt subtype probe-req)
    timeout 10 tcpdump -i mon0 -e -s 256 'type mgt subtype probe-req' 2>/dev/null | while read -r line; do
        local src_mac=$(echo "$line" | grep -oE 'SA:[0-9a-f:]+' | cut -d: -f2-)
        [ -z "$src_mac" ] && src_mac=$(echo "$line" | grep -oE '[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}' | head -1)
        local probed_ssid=$(echo "$line" | grep -oE 'Probe Request \([^)]*\)' | sed 's/Probe Request (\(.*\))/\1/')
        [ -n "$src_mac" ] && {
            record_probe "$src_mac" "${probed_ssid:-broadcast}" "" ""
            record_device "PROBE" "$src_mac" "${probed_ssid:-broadcast}" "0" "probe-req"
            scan "  Probe: $src_mac searching for '${probed_ssid:-*}'"
        }
    done

    # Clean up if we created it
    [ "$mon_existed" = "no" ] && {
        ip link set mon0 down 2>/dev/null
        iw dev mon0 del 2>/dev/null
    }

    info "Probe capture done"
}

# ===== 5. BLUETOOTH — full discovery =====
scan_bluetooth() {
    scan "Bluetooth scan..."

    local bt_dump=$(dumpsys bluetooth_manager 2>/dev/null)

    # All known MACs (bonded + seen)
    echo "$bt_dump" | grep -oE '[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}' | sort -u | while read -r mac; do
        [ "$mac" = "00:00:00:00:00:00" ] && continue
        local name=$(echo "$bt_dump" | grep -B5 "$mac" | grep -oE 'name=[^,}]+' | head -1 | cut -d= -f2)
        local rssi=$(echo "$bt_dump" | grep -A10 "$mac" | grep -oE 'rssi=-?[0-9]+' | head -1 | cut -d= -f2)
        local bt_type=$(echo "$bt_dump" | grep -A3 "$mac" | grep -oE 'type=[0-9]+' | head -1 | cut -d= -f2)
        record_device "BT" "$mac" "${name:-unknown}" "${rssi:-0}" "type=${bt_type}"
        scan "  BT: $mac ${name:+($name)} ${rssi:+rssi=$rssi}"
    done

    # BLE scan results (recent advertisements)
    echo "$bt_dump" | grep -i "scanresult\|ble\|advertis" | grep -oE '[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}' | sort -u | while read -r mac; do
        [ "$mac" = "00:00:00:00:00:00" ] && continue
        record_device "BLE" "$mac" "" "0" "advertisement"
        scan "  BLE: $mac"
    done

    info "BT scan done"
}

# ===== 6. IMSI CATCHER DETECTION (heuristic checks) =====
check_imsi_catcher() {
    local tel=$(dumpsys telephony.registry 2>/dev/null)

    # RAT downgrade
    local rat=$(echo "$tel" | grep -oE 'accessNetworkTechnology=[A-Z_0-9]+' | head -1 | cut -d= -f2)
    if [ -n "$PREV_RAT" ] && [ "$PREV_RAT" != "UNKNOWN" ]; then
        case "$PREV_RAT" in
            LTE|NR|NR_NSA)
                case "$rat" in
                    GSM|GPRS|EDGE) alert "RAT DOWNGRADE: $PREV_RAT → $rat (forced downgrade attack!)" ;;
                    UMTS|HSPA) warn "RAT downgrade: $PREV_RAT → $rat" ;;
                esac ;;
        esac
    fi

    # Signal anomaly
    local gsm_rssi=$(echo "$tel" | grep -oE 'rssi=-?[0-9]+' | head -1 | grep -oE '\-?[0-9]+')
    [ -n "$gsm_rssi" ] && [ "$gsm_rssi" != "2147483647" ] && \
        [ "$gsm_rssi" -gt -50 ] 2>/dev/null && alert "Signal $gsm_rssi dBm (suspiciously strong — fake BTS?)"

    # Silent SMS (Type 0)
    local silent=$(logcat -d -t 120 2>/dev/null | grep -c -i -E "SMS.*type.*0|silent.*sms|class.*0.*sms")
    [ "$silent" -gt 0 ] && alert "Silent SMS detected ($silent instances)!"

    # LAC/CID jump
    local lac=$(echo "$tel" | grep -oE 'mLac=[0-9]+' | head -1 | cut -d= -f2)
    local cid=$(echo "$tel" | grep -oE 'mCid=[0-9]+' | head -1 | cut -d= -f2)
    [ -n "$PREV_LAC" ] && [ -n "$lac" ] && [ "$lac" != "$PREV_LAC" ] && [ "$lac" != "2147483647" ] && \
        warn "LAC changed: $PREV_LAC → $lac"
    [ -n "$PREV_CID" ] && [ -n "$cid" ] && [ "$cid" != "$PREV_CID" ] && [ "$cid" != "2147483647" ] && \
        warn "Cell ID changed: $PREV_CID → $cid"

    PREV_RAT="$rat"
    PREV_LAC="$lac"
    PREV_CID="$cid"
}

# ===== FULL SCAN =====
full_scan() {
    header "FULL ENVIRONMENT SCAN"
    init_devices_db
    detect_capabilities

    scan_cells
    scan_diag_imsi
    scan_wifi
    scan_wifi_probes
    scan_bluetooth

    local dev_count=$(wc -l < "$DEVICES_DB" 2>/dev/null)
    dev_count=$((dev_count - 1))
    local imsi_count=$(wc -l < "$IMSI_DB" 2>/dev/null)
    imsi_count=$((imsi_count - 1))
    local probe_count=$(wc -l < "$PROBE_LOG" 2>/dev/null)
    probe_count=$((probe_count - 1))

    info "Scan complete: $dev_count devices, $imsi_count cell/IMSI records, $probe_count WiFi probes"
}

# ===== CLI =====
case "$1" in
    --scan)
        full_scan
        exit 0 ;;

    --imsi|--imsis)
        init_devices_db
        detect_capabilities
        scan_cells
        scan_diag_imsi
        echo ""
        header "IMSI/TMSI/Cell Records"
        if [ -f "$IMSI_DB" ] && [ "$(wc -l < "$IMSI_DB")" -gt 1 ]; then
            printf "%-19s %-10s %-20s %-10s %-12s %s\n" "TIME" "TYPE" "ID" "MCC/MNC" "SOURCE" "EXTRA"
            echo "------------------------------------------------------------------------------------"
            tail -40 "$IMSI_DB" | tail -n +2 | while IFS=',' read -r ts type id mcc src extra; do
                printf "%-19s %-10s %-20s %-10s %-12s %s\n" "$ts" "$type" "$id" "$mcc" "$src" "$extra"
            done
        else
            echo "(none detected)"
            echo ""
            echo "Tips to detect more:"
            echo "  - Start SnoopSnitch app → enable DIAG logging for OTA IMSI capture"
            echo "  - Insert SIM → modem registers, sees more neighbor cells"
            echo "  - Run longer in continuous mode: $0 (no args)"
        fi
        exit 0 ;;

    --wifi|--probes)
        init_devices_db
        detect_capabilities
        scan_wifi
        scan_wifi_probes
        echo ""
        header "WiFi Devices + Probes"
        echo "--- Access Points ---"
        grep "^[^t].*,WIFI," "$DEVICES_DB" 2>/dev/null | tail -20 | while IFS=',' read -r ts type id name rssi extra; do
            printf "%-19s %-18s %-30s %s %s\n" "$ts" "$id" "$name" "$rssi" "$extra"
        done
        echo ""
        echo "--- Probe Requests (devices searching) ---"
        if [ -f "$PROBE_LOG" ] && [ "$(wc -l < "$PROBE_LOG")" -gt 1 ]; then
            tail -20 "$PROBE_LOG" | while IFS=',' read -r ts mac ssid rssi freq; do
                printf "%-19s %-18s searching: %s\n" "$ts" "$mac" "$ssid"
            done
        else
            echo "(none — need WiFi monitor mode: iw phy phy0 interface add mon0 type monitor)"
        fi
        exit 0 ;;

    --bt)
        init_devices_db
        scan_bluetooth
        echo ""
        header "Bluetooth Devices"
        grep ",BT\|,BLE" "$DEVICES_DB" 2>/dev/null | tail -20 | while IFS=',' read -r ts type id name rssi extra; do
            printf "%-19s %-5s %-18s %-15s %s\n" "$ts" "$type" "$id" "$name" "$rssi"
        done
        exit 0 ;;

    --devices)
        if [ -f "$DEVICES_DB" ]; then
            echo "=== All Nearby Devices (last 30) ==="
            printf "%-19s %-8s %-18s %-20s %-6s %s\n" "TIME" "TYPE" "ID" "NAME" "RSSI" "EXTRA"
            echo "------------------------------------------------------------------------------------"
            tail -30 "$DEVICES_DB" | while IFS=',' read -r ts type id name rssi extra; do
                printf "%-19s %-8s %-18s %-20s %-6s %s\n" "$ts" "$type" "$id" "$name" "$rssi" "$extra"
            done
        else
            echo "No devices yet. Run: $0 --scan"
        fi
        exit 0 ;;

    --watch|--add-watch)
        shift
        [ -z "$1" ] && {
            echo "Usage: $0 --add-watch <imsi|mac|ssid|ip> [label] [type]"
            echo "Types: IMSI, BT, WIFI, any"
            echo "Examples:"
            echo "  $0 --add-watch 262011234567890 'Suspect' IMSI"
            echo "  $0 --add-watch AA:BB:CC:DD:EE:FF 'Laptop' BT"
            echo "  $0 --add-watch 'FRITZ!Box 7430' 'Neighbor' WIFI"
            exit 1
        }
        add_to_watchlist "$1" "$2" "$3"
        exit 0 ;;

    --list-watch)
        list_watchlist
        exit 0 ;;

    --status)
        detect_capabilities
        echo ""
        echo "=== Detection Capabilities ==="
        echo "SIM card:          $HAS_SIM"
        echo "/dev/diag:         $HAS_DIAG"
        echo "WiFi monitor:      $HAS_MONITOR"
        echo "SnoopSnitch:       $(pm list packages 2>/dev/null | grep -q snoopsnitch && echo 'installed' || echo 'not installed')"
        echo "tcpdump:           $(which tcpdump 2>/dev/null || echo 'not found')"
        echo ""
        echo "=== Data Files ==="
        for f in "$DEVICES_DB" "$IMSI_DB" "$PROBE_LOG" "$WATCHLIST" "$LOG"; do
            [ -f "$f" ] && echo "$(wc -l < "$f") lines  $f" || echo "        -  $f"
        done
        echo ""
        echo "=== What each source detects ==="
        echo "  Cell towers:      Visible base stations (MCC/MNC/LAC/CID) — works WITHOUT SIM"
        echo "  /dev/diag:        OTA GSM paging with IMSI/TMSI of nearby phones (needs SnoopSnitch)"
        echo "  WiFi scan:        All APs with BSSID/SSID/RSSI/security — always works"
        echo "  WiFi probes:      MAC of EVERY device searching for WiFi — needs monitor mode"
        echo "  Bluetooth:        Nearby BT/BLE devices with MAC — always works"
        echo "  ARP:              Devices on same network — always works"
        echo "  Watchlist:        Alert + triple vibration when a saved ID appears"
        echo ""
        [ "$HAS_SIM" = "no" ] && echo "TIP: Even without SIM, the modem sees emergency cells."
        [ "$HAS_DIAG" = "yes" ] && echo "TIP: Start SnoopSnitch → enable diag logging for IMSI capture."
        [ "$HAS_MONITOR" = "yes" ] && echo "TIP: WiFi probe capture catches ALL nearby smartphones/laptops."
        exit 0 ;;

    --monitor-scan|--monscan)
        init_devices_db
        scan "MONITOR SCAN — WiFi will disconnect for ~15s!"
        echo "This takes wlan0 offline, captures probe requests, then reconnects."
        echo ""

        # Save current connection
        local cur_ssid=$(iw dev wlan0 info 2>/dev/null | grep ssid | awk '{$1=""; print $0}' | sed 's/^ //')

        # Kill all virtual interfaces
        for vif in swlan0 p2p0 wifi-aware0; do
            ip link set "$vif" down 2>/dev/null
        done

        # Switch wlan0 to monitor
        ip link set wlan0 down 2>/dev/null
        sleep 0.5
        iw dev wlan0 set type monitor 2>&1
        ip link set wlan0 up 2>/dev/null
        sleep 0.5

        if iw dev wlan0 info 2>/dev/null | grep -q "type monitor"; then
            scan "Monitor mode active on wlan0 — capturing 15s..."
            # Channel hop: 1, 6, 11 (2.4GHz most common)
            for ch in 1 6 11 1 6 11 1 6 11 1 6 11 1 6 11; do
                iw dev wlan0 set channel "$ch" 2>/dev/null
                timeout 1 tcpdump -i wlan0 -e -s 256 'type mgt subtype probe-req' 2>/dev/null | while read -r line; do
                    local src_mac=$(echo "$line" | grep -oE 'SA:[0-9a-f:]+' | cut -d: -f2-)
                    [ -z "$src_mac" ] && src_mac=$(echo "$line" | grep -oE '[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}' | head -1)
                    local probed_ssid=$(echo "$line" | grep -oE 'Probe Request \([^)]*\)' | sed 's/Probe Request (\(.*\))/\1/')
                    [ -n "$src_mac" ] && {
                        record_probe "$src_mac" "${probed_ssid:-broadcast}" "" "$ch"
                        record_device "PROBE" "$src_mac" "${probed_ssid:-broadcast}" "0" "ch=$ch"
                        scan "  Probe: $src_mac → '${probed_ssid:-*}' (ch $ch)"
                    }
                done
            done
        else
            warn "Could not switch to monitor mode"
        fi

        # Restore managed mode
        ip link set wlan0 down 2>/dev/null
        iw dev wlan0 set type managed 2>/dev/null
        ip link set wlan0 up 2>/dev/null

        # Restart virtual interfaces
        for vif in swlan0 p2p0 wifi-aware0; do
            ip link set "$vif" up 2>/dev/null
        done

        # Reconnect
        sleep 2
        svc wifi disable 2>/dev/null; sleep 1; svc wifi enable 2>/dev/null
        scan "WiFi restored. Reconnecting to '$cur_ssid'..."

        local probe_count=$(wc -l < "$PROBE_LOG" 2>/dev/null); probe_count=$((probe_count - 1))
        info "Captured $probe_count probe requests"

        if [ -f "$PROBE_LOG" ] && [ "$probe_count" -gt 0 ]; then
            echo ""
            header "Captured Probe Requests"
            printf "%-19s %-18s %-25s %s\n" "TIME" "MAC" "SEARCHING FOR" "CH"
            echo "-------------------------------------------------------------------"
            tail -30 "$PROBE_LOG" | while IFS=',' read -r ts mac ssid rssi freq; do
                printf "%-19s %-18s %-25s %s\n" "$ts" "$mac" "$ssid" "$freq"
            done
        fi
        exit 0 ;;

    --help|-h)
        echo "IMSI Catcher Detection + Nearby Device Monitor v2"
        echo ""
        echo "Usage: su -c sh $0 [mode]"
        echo ""
        echo "Scan modes:"
        echo "  (no args)      Continuous monitoring (all sensors, ${INTERVAL}s cycle)"
        echo "  --scan          One-shot full scan (all sensors)"
        echo "  --imsi          Cell towers + IMSI/TMSI scan (works WITHOUT SIM)"
        echo "  --wifi          WiFi APs + probe requests (catches searching devices)"
        echo "  --monitor-scan  FULL probe capture (disconnects WiFi for 15s!)"
        echo "  --bt            Bluetooth nearby devices"
        echo "  --devices       Show all recently detected devices"
        echo "  --status        Show detection capabilities and data files"
        echo ""
        echo "Watchlist:"
        echo "  --add-watch ID [label] [type]   Add to watchlist (triple vibrate on match)"
        echo "  --list-watch                    Show watchlist"
        echo ""
        echo "What gets detected WITHOUT a SIM card:"
        echo "  ✓ Cell towers (emergency scan — MCC/MNC/LAC/CID)"
        echo "  ✓ OTA IMSI/TMSI via /dev/diag (if SnoopSnitch active)"
        echo "  ✓ WiFi APs (BSSID, SSID, signal, encryption)"
        echo "  ✓ WiFi probe requests (MAC of every searching device)"
        echo "  ✓ Bluetooth/BLE devices"
        echo "  ✓ ARP neighbors"
        echo "  ✓ IMSI catcher indicators (downgrade, no encryption, paging)"
        echo ""
        echo "Data: $DATADIR/"
        exit 0 ;;
esac

# ===== CONTINUOUS MONITORING =====
header "IMSI Catcher Detection + Device Monitor v2"
info "Interval: ${INTERVAL}s | Data: $DATADIR/"
init_watchlist
init_devices_db
detect_capabilities
info "Watchlist: $(wc -l < "$WATCHLIST" 2>/dev/null || echo 0) entries"
info "Press Ctrl+C to stop"

PREV_RAT=""
PREV_LAC=""
PREV_CID=""
CYCLE=0

# Initial full scan
full_scan

while true; do
    sleep "$INTERVAL"
    CYCLE=$((CYCLE + 1))

    # IMSI catcher checks every cycle
    check_imsi_catcher

    # Rotate scans to spread load (each runs every ~4 min)
    case $((CYCLE % 8)) in
        0) scan_cells; scan_diag_imsi ;;    # cell + diag
        2) scan_wifi ;;                      # WiFi APs + ARP
        4) scan_bluetooth ;;                 # BT/BLE
        6) scan_wifi_probes ;;               # probe requests
    esac

    # Full scan every 10 min
    [ $((CYCLE % 20)) -eq 0 ] && full_scan

    local dev_c=$(wc -l < "$DEVICES_DB" 2>/dev/null); dev_c=$((dev_c - 1))
    info "Cycle $CYCLE | RAT: ${PREV_RAT:-?} | Devices: $dev_c | SIM: $HAS_SIM"
done
