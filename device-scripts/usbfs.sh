#!/system/bin/sh
# Setup /dev/bus/usb + fix NetHunter chroot bind-mount after boot
# Android lacks udev and CONFIG_USB_DEVICEFS, so we create nodes manually.
# Also fixes stale chroot bind-mount caused by service.d running before
# expanded storage fscrypt keys are loaded (screen unlock).
#
# Run from: Magisk service.d (boot) AND manually after chroot start.

NH_ROOT="/data/local/nhsystem/kali-arm64"
NH_EXPAND="/mnt/expand/54ee4888-8f46-4b0b-9d5b-d9443ce0e170/kali-arm64"

# --- 1. Create /dev/bus/usb structure ---
mkdir -p /dev/bus/usb/001

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
    [ -e "/dev/bus/usb/$busdir/$devfile" ] || mknod "/dev/bus/usb/$busdir/$devfile" c $major $minor
    chmod 0666 "/dev/bus/usb/$busdir/$devfile"
done

# --- 2. Fix stale chroot bind-mount ---
# At boot, chroot-guard bind-mounts before fscrypt keys load → empty mount.
# Detect: mount exists but content is missing. Rebind from expanded storage.
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
