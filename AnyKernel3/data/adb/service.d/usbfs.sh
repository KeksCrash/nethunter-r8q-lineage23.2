#!/system/bin/sh
# Setup /dev/bus/usb + fix NetHunter chroot bind-mount after boot
# Android lacks udev and CONFIG_USB_DEVICEFS, so we create nodes manually.
# Also fixes stale chroot bind-mount caused by service.d running before
# expanded storage fscrypt keys are loaded (screen unlock).
#
# Run from: Magisk service.d (boot) AND manually after chroot start.
# Starts a background inotifyd watcher so hot-plugged USB devices
# get their /dev/bus/usb nodes automatically.

NH_ROOT="/data/local/nhsystem/kali-arm64"
NH_EXPAND="/mnt/expand/54ee4888-8f46-4b0b-9d5b-d9443ce0e170/kali-arm64"
HOTPLUG_HANDLER="/data/adb/service.d/usb-hotplug.sh"

# --- 1. Create /dev/bus/usb structure ---
# SM-G781B quirk: /sys/bus/usb/devices/ is empty even with devices connected.
# The real device tree lives under the xHCI platform device path.
XHCI_BASE="/sys/devices/platform/soc/a600000.ssusb/a600000.dwc3/xhci-hcd.8.auto"
mkdir -p /dev/bus/usb/001

create_node() {
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
    [ -e "/dev/bus/usb/$busdir/$devfile" ] || mknod "/dev/bus/usb/$busdir/$devfile" c $major $minor
    chmod 0666 "/dev/bus/usb/$busdir/$devfile"
}

for dev in /sys/bus/usb/devices/*; do
    create_node "$dev"
done

if [ -d "$XHCI_BASE" ]; then
    find "$XHCI_BASE" -maxdepth 5 -name busnum 2>/dev/null | while read bfile; do
        create_node "$(dirname "$bfile")"
    done
fi

# --- 2. Fix stale chroot bind-mount ---
if [ -d "$NH_EXPAND/usr" ]; then
    if [ ! -d "$NH_ROOT/usr" ]; then
        umount "$NH_ROOT" 2>/dev/null
        mount --bind "$NH_EXPAND" "$NH_ROOT"
        log -t usbfs "Rebound stale chroot: $NH_EXPAND -> $NH_ROOT"
    fi
fi

# --- 3. Bind /dev/bus/usb into chroot ---
if [ -d "$NH_ROOT/usr/bin" ]; then
    mkdir -p "$NH_ROOT/dev/bus/usb"
    mountpoint -q "$NH_ROOT/dev/bus/usb" || mount --bind /dev/bus/usb "$NH_ROOT/dev/bus/usb"
fi

# --- 4. Start USB hot-plug polling daemon ---
# sysfs doesn't support inotify and uevent_helper is absent on this
# kernel, so a lightweight polling daemon watches for new USB devices.
HOTPLUG_PID="/data/local/tmp/.usb-hotplug.pid"
if [ -f "$HOTPLUG_PID" ] && kill -0 "$(cat "$HOTPLUG_PID")" 2>/dev/null; then
    log -t usbfs "USB hot-plug daemon already running (PID $(cat "$HOTPLUG_PID"))"
else
    sh "$HOTPLUG_HANDLER" &
    echo $! > "$HOTPLUG_PID"
    log -t usbfs "Started USB hot-plug daemon (PID $!)"
fi
