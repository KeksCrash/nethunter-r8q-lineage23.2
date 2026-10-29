#!/system/bin/sh
# Create /dev/bus/usb device node structure for libusb/lsusb
# Android does not create these by default (no udev, CONFIG_USB_DEVICEFS removed)
# Runs as Magisk service.d script at boot + can be called manually

mkdir -p /dev/bus/usb/001

# Populate device nodes from sysfs (USB host devices present when OTG connected)
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

# Bind-mount into NetHunter chroot if active
NH_ROOT="/data/local/nhsystem/kali-arm64"
if [ -d "$NH_ROOT/usr/bin" ]; then
    mkdir -p "$NH_ROOT/dev/bus/usb"
    mountpoint -q "$NH_ROOT/dev/bus/usb" || mount --bind /dev/bus/usb "$NH_ROOT/dev/bus/usb"
fi
