#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/adb/kalilock-nfc-boot.log
umask 077
exec >>"$LOG" 2>&1
echo "BOOT_RECOVERY_START $(date)"
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 3; done
last_pid=
attempts=0
while [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ]; do
    nfc_pid=$(pidof com.android.nfc)
    case "$nfc_pid" in
        ''|*[!0-9]*) sleep 15; continue ;;
    esac
    if [ "$last_pid" != "$nfc_pid" ]; then
        last_pid=$nfc_pid
        attempts=0
    fi
    if [ "$attempts" -lt 3 ]; then
        attempts=$((attempts + 1))
        echo "HOOK_ATTEMPT pid=$nfc_pid attempt=$attempts $(date)"
        if timeout 60 "$MODDIR/frida-inject" -p "$nfc_pid" -s "$MODDIR/hooks.js" -e; then
            attempts=3
            echo "INJECTOR_COMPLETED pid=$nfc_pid"
        else
            echo "INJECTOR_FAILED pid=$nfc_pid"
        fi
    fi
    sleep 15
done
