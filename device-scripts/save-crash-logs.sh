#!/system/bin/sh
# Save crash logs from previous boot (pstore + last_kmsg)
# Deploy to /data/adb/post-fs-data.d/ for automatic execution
CRASH_DIR=/data/local/tmp/crash_logs
TS=$(date +%Y%m%d_%H%M%S)
mkdir -p $CRASH_DIR
[ -f /sys/fs/pstore/console-ramoops-0 ] && cp /sys/fs/pstore/console-ramoops-0 $CRASH_DIR/ramoops_$TS.log
[ -f /proc/last_kmsg ] && cp /proc/last_kmsg $CRASH_DIR/last_kmsg_$TS.log
