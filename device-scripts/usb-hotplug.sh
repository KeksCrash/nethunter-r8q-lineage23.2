#!/system/bin/sh
# USB hot-plug daemon — polls sysfs every 2s and creates/fixes
# /dev/bus/usb device nodes. Idempotent (skips existing nodes).
# Started by usbfs.sh at boot, runs in background.
#
# SM-G781B quirk: /sys/bus/usb/devices/ is empty even with devices
# connected. The real device tree lives under the xHCI platform device.
# We scan both paths so this works on any kernel.

XHCI_BASE="/sys/devices/platform/soc/a600000.ssusb/a600000.dwc3/xhci-hcd.8.auto"

scan_and_create() {
    d="$1"
    [ -e "$d/busnum" ] || return
    [ -e "$d/dev" ] || return
    [ -e "$d/devnum" ] || return
    busnum=$(cat "$d/busnum")
    devnum=$(cat "$d/devnum")
    major=$(cat "$d/dev" | cut -d: -f1)
    minor=$(cat "$d/dev" | cut -d: -f2)
    busdir=$(printf "%03d" $busnum)
    devfile=$(printf "%03d" $devnum)
    mkdir -p "/dev/bus/usb/$busdir"
    if [ ! -e "/dev/bus/usb/$busdir/$devfile" ]; then
        mknod "/dev/bus/usb/$busdir/$devfile" c $major $minor
        chmod 0666 "/dev/bus/usb/$busdir/$devfile"
        log -t usb-hotplug "Created /dev/bus/usb/$busdir/$devfile"
    else
        chmod 0666 "/dev/bus/usb/$busdir/$devfile"
    fi
}

while true; do
    # Path 1: standard sysfs bus (works on most kernels)
    for dev in /sys/bus/usb/devices/*; do
        scan_and_create "$dev"
    done

    # Path 2: direct device tree (SM-G781B quirk — bus symlinks missing)
    if [ -d "$XHCI_BASE" ]; then
        find "$XHCI_BASE" -maxdepth 5 -name busnum 2>/dev/null | while read bfile; do
            scan_and_create "$(dirname "$bfile")"
        done
    fi

    sleep 2
done
