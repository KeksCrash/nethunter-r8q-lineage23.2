#!/system/bin/sh
# Qualcomm Hastings: bounded, reversible UART/HCI tests; run as Android root.
C=/data/local/nhsystem/kali-arm64
B=/data/local/tmp/btpower-android
LOG=/data/local/tmp/hastings-fulltest.log
: > "$LOG"
exec >> "$LOG" 2>&1
START=$(date +%s)
ORIG=$(settings get global bluetooth_on)
MOUNTED=0
PID=""
ACTIVE=0
say(){ echo "[$(date '+%H:%M:%S')] $*"; }
stop_attach(){
  if [ -n "$PID" ]; then
    kill "$PID" 2>/dev/null || true
    n=0
    while kill -0 "$PID" 2>/dev/null && [ "$n" -lt 6 ]; do sleep 1; n=$((n+1)); done
    if kill -0 "$PID" 2>/dev/null; then kill -9 "$PID" 2>/dev/null || true; fi
    wait "$PID" 2>/dev/null || true
    PID=""
  fi
}
cleanup(){
  say '=== CLEANUP ==='
  stop_attach
  if [ "$MOUNTED" = 1 ]; then
    n=0
    while [ "$n" -lt 4 ]; do
      if umount "$C/dev"; then say DEV_BIND_UNMOUNTED; MOUNTED=0; break; fi
      sleep 1; n=$((n+1))
    done
    [ "$MOUNTED" = 0 ] || say 'WARNING: DEV_BIND_STILL_MOUNTED (no lazy unmount)'
  fi
  if [ "$ACTIVE" = 1 ] && [ -x "$B" ]; then "$B" 0 || true; fi
  if [ "$ORIG" = 1 ]; then svc bluetooth enable; else svc bluetooth disable; fi
  sleep 2
  say "ANDROID_FINAL=$(settings get global bluetooth_on)"
  say 'FINAL_MOUNTS:'
  grep " $C/dev " /proc/mounts || true
  say 'FINAL_UART_OWNER:'
  P=$(pidof android.hardware.bluetooth@1.0-service-qti)
  [ -z "$P" ] || ls -l /proc/$P/fd 2>/dev/null | grep ttyHS0 || true
  say "TOTAL_SECONDS=$(($(date +%s)-START))"
}
trap cleanup EXIT
trap 'say INTERRUPTED; exit 130' INT TERM HUP
say '=== BASELINE ==='
id; uname -a
say "SOC=$(getprop vendor.qcom.bluetooth.soc)"
say "FW=$(getprop vendor.bluetooth_fw_ver)"
say "NV=$(getprop vendor.bluetooth_nv_ver)"
say "ANDROID_ORIGINAL=$ORIG"
say '=== CHROOT_STORAGE_AND_DEV ==='
ls -ld "$C" "$C/dev" /dev/ttyHS0 /dev/btpower 2>&1
cat /proc/mounts | grep -E " $C | $C/dev |/mnt/media_rw/|/storage/" | head -30
say '=== KERNEL_CONFIG ==='
zcat /proc/config.gz 2>/dev/null | grep -E '^CONFIG_(BT_HCIUART|BT_QCA|SERIAL_DEV_BUS|BT_HCIUART_QCA|BT_HCIUART_H4)' || true
say '=== ANDROID_OFF ==='
svc bluetooth disable || exit 1
sleep 3
P=$(pidof android.hardware.bluetooth@1.0-service-qti)
if [ -n "$P" ] && ls -l /proc/$P/fd 2>/dev/null | grep -q ttyHS0; then say UART_BUSY_ABORT; exit 2; fi
[ -x "$B" ] || { say POWER_BINARY_MISSING; exit 3; }
[ -x "$C/usr/bin/btattach" ] || { say BTATTACH_MISSING; exit 4; }
[ -c /dev/ttyHS0 ] || { say UART_MISSING; exit 5; }
say '=== BIND_REAL_ANDROID_DEV ==='
# Do not touch SD mounts; bind only /dev. Unmount only the layer created here.
mount --bind /dev "$C/dev" || exit 6
MOUNTED=1
say '=== CHROOT_UART_OPEN ==='
if [ -x "$C/usr/bin/python3" ]; then
  timeout 5 chroot "$C" /usr/bin/python3 -c 'import os; f=os.open("/dev/ttyHS0",os.O_RDWR|os.O_NOCTTY|os.O_NONBLOCK); print("CHROOT_UART_OPEN_OK"); os.close(f)' || say CHROOT_UART_OPEN_FAILED
fi
for PROTO in h4 qca; do
  say "=== TEST_PROTOCOL_$PROTO ==="
  "$B" 0 || true
  sleep 1
  "$B" 1 || { say POWER_ON_FAILED; continue; }
  ACTIVE=1
  sleep 2
  chroot "$C" /usr/bin/btattach -B /dev/ttyHS0 -P "$PROTO" -S 115200 > "/data/local/tmp/hastings-$PROTO-attach.log" 2>&1 &
  PID=$!
  sleep 4
  say "ATTACH_PID=$PID"
  say 'HCI_SYSFS:'; ls -l /sys/class/bluetooth
  say 'HCI_BEFORE:'
  timeout 4 chroot "$C" /usr/bin/hciconfig -a || true
  if [ -e /sys/class/bluetooth/hci0 ]; then
    say 'HCI_UP (8-second limit):'
    timeout -k 2 8 chroot "$C" /usr/bin/hciconfig hci0 up 2>&1; RC=$?; RC=$?
    say "HCI_UP_EXIT=$RC"
    say 'HCI_AFTER:'
    timeout 4 chroot "$C" /usr/bin/hciconfig -a || true
    say 'HCI_INFORMATION:'
    if [ -x "$C/usr/bin/btmgmt" ]; then timeout -k 2 5 chroot "$C" /usr/bin/btmgmt info || true; fi
    if [ -x "$C/usr/bin/hcitool" ]; then timeout -k 2 5 chroot "$C" /usr/bin/hcitool -i hci0 dev || true; fi
  else
    say NO_HCI0
  fi
  say 'ATTACH_OUTPUT:'; cat "/data/local/tmp/hastings-$PROTO-attach.log"
  say 'KERNEL_BLUETOOTH_LOG:'; dmesg | grep -Ei 'hci0|hci_uart|Bluetooth:|ttyHS0|qca|hastings' | tail -50
  stop_attach
  sleep 2
  say 'HCI_AFTER_DETACH:'; ls /sys/class/bluetooth
  "$B" 0 || true
  ACTIVE=0
  sleep 2
done
say '=== RESULT ==='
say 'If RX=0 and no valid controller features, firmware/IBS/baud init remains unresolved.'
