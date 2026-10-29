#!/system/bin/sh
# Called by inotifyd when a new entry appears in /sys/bus/usb/devices/
# Args from inotifyd: $1=events $2=watched-dir $3=filename
# Only act on real USB devices (numeric prefix), not interfaces (e.g. 1-1:1.0)

EVENT="$1"
DIR="$2"
FILE="$3"

case "$FILE" in
    [0-9]-[0-9]*) ;;  # e.g. 1-1, 2-1.3
    *) exit 0 ;;       # skip interfaces, hubs named differently
esac

DEV="$DIR/$FILE"

sleep 1

[ -e "$DEV/busnum" ] || exit 0
[ -e "$DEV/dev" ] || exit 0

busnum=$(cat "$DEV/busnum")
devnum=$(cat "$DEV/devnum")
major=$(cat "$DEV/dev" | cut -d: -f1)
minor=$(cat "$DEV/dev" | cut -d: -f2)
busdir=$(printf "%03d" $busnum)
devfile=$(printf "%03d" $devnum)

mkdir -p "/dev/bus/usb/$busdir"
[ -e "/dev/bus/usb/$busdir/$devfile" ] || mknod "/dev/bus/usb/$busdir/$devfile" c $major $minor
chmod 0666 "/dev/bus/usb/$busdir/$devfile"

NH_ROOT="/data/local/nhsystem/kali-arm64"
if mountpoint -q "$NH_ROOT/dev/bus/usb" 2>/dev/null; then
    : # bind-mount already active, new node visible automatically
else
    if [ -d "$NH_ROOT/usr/bin" ]; then
        mkdir -p "$NH_ROOT/dev/bus/usb"
        mount --bind /dev/bus/usb "$NH_ROOT/dev/bus/usb"
    fi
fi

log -t usb-hotplug "Created /dev/bus/usb/$busdir/$devfile for $FILE"
