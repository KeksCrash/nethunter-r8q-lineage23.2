#!/system/bin/sh
# USB hot-plug daemon — polls sysfs for new USB devices and creates
# /dev/bus/usb nodes. Runs in background, started by usbfs.sh.
# sysfs doesn't support inotify and uevent_helper is absent on this
# kernel, so polling is the only reliable mechanism on stock Android.

PIDFILE="/data/local/tmp/.usb-hotplug.pid"
KNOWN="/data/local/tmp/.usb-known"

create_node() {
    dev="$1"
    [ -e "$dev/busnum" ] || return
    [ -e "$dev/dev" ] || return
    busnum=$(cat "$dev/busnum")
    devnum=$(cat "$dev/devnum")
    major=$(cat "$dev/dev" | cut -d: -f1)
    minor=$(cat "$dev/dev" | cut -d: -f2)
    busdir=$(printf "%03d" $busnum)
    devfile=$(printf "%03d" $devnum)
    mkdir -p "/dev/bus/usb/$busdir"
    if [ ! -e "/dev/bus/usb/$busdir/$devfile" ]; then
        mknod "/dev/bus/usb/$busdir/$devfile" c $major $minor
        chmod 0666 "/dev/bus/usb/$busdir/$devfile"
        log -t usb-hotplug "Created /dev/bus/usb/$busdir/$devfile"
    fi
}

# Snapshot current devices
ls /sys/bus/usb/devices/ 2>/dev/null | sort > "$KNOWN"

while true; do
    sleep 2
    CURRENT=$(ls /sys/bus/usb/devices/ 2>/dev/null | sort)
    NEW=$(echo "$CURRENT" | comm -23 - "$KNOWN" 2>/dev/null)
    if [ -n "$NEW" ]; then
        for entry in $NEW; do
            dev="/sys/bus/usb/devices/$entry"
            [ -e "$dev/busnum" ] && create_node "$dev"
        done
        echo "$CURRENT" > "$KNOWN"
    fi
done
