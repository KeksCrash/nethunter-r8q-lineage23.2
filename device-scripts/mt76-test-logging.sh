#!/system/bin/sh
# MT76 USB-Dongle Test Logging
# Starten VOR dem Einstecken des Dongles, läuft im Hintergrund
LOG=/data/local/tmp/mt76_test.log
CRASH_DIR=/data/local/tmp/crash_logs

mkdir -p $CRASH_DIR

# Verbose kernel logging
echo 8 > /proc/sys/kernel/printk

# Snapshot pre-test dmesg
dmesg > $CRASH_DIR/dmesg_before_test.log

# Continuous dmesg logging to persistent storage
dmesg -w > $LOG &
DMESG_PID=$!
echo "Logging PID: $DMESG_PID -> $LOG"
echo "Stecke jetzt den MT76 USB-Dongle ein."
echo "Nach dem Test oder Reboot: Logs in $CRASH_DIR/"

# Trap to save on exit
trap "kill $DMESG_PID 2>/dev/null; dmesg > $CRASH_DIR/dmesg_final.log" EXIT
wait $DMESG_PID
