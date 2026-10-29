#!/system/bin/sh
# USB hot-plug daemon — polls sysfs every 2s and creates missing
# /dev/bus/usb device nodes. Idempotent (skips existing nodes).
# Started by usbfs.sh at boot, runs in background.

while true; do
    for dev in /sys/bus/usb/devices/[0-9]*; do
        [ -e "$dev/busnum" ] || continue
        [ -e "$dev/dev" ] || continue
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
    done
    sleep 2
done
