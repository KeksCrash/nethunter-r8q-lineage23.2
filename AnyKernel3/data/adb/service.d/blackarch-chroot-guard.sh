#!/system/bin/sh
# blackarch-chroot-guard.sh - Magisk service.d boot script for the BlackArch chroot.
# Deploy to /data/adb/service.d/ and chmod +x it. Fully standalone.

[ -f /data/local/nhsystem/blackarch.conf ] && . /data/local/nhsystem/blackarch.conf

LOCAL_CHROOT="/data/local/nhsystem/blackarch-arm64"
MNT="$LOCAL_CHROOT"
if [ -n "$SD_UUID" ]; then
    SD_CHROOT="/mnt/expand/${SD_UUID}/blackarch-arm64"
fi
LOG="/data/local/nhsystem/blackarch-guard.log"

mkdir -p /data/local/nhsystem 2>/dev/null
log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOG"; }

log "guard start (SD_UUID='${SD_UUID}')"

if [ -n "$SD_UUID" ]; then
    # Wait for device unlock (up to 5 min)
    tries=0
    while [ $tries -lt 60 ]; do
        if [ -d /data/data/de.nethunter.blackarch ] || [ -d /data/local/nhsystem ]; then
            break
        fi
        tries=$((tries + 1))
        sleep 5
    done
    log "unlock wait finished after $tries tries"

    # Wait for the SD rootfs (up to 7.5 min)
    tries=0
    while [ $tries -lt 90 ]; do
        [ -d "$SD_CHROOT/bin" ] && break
        tries=$((tries + 1))
        sleep 5
    done
    log "SD wait finished after $tries tries"
else
    log "SD_UUID empty, using local storage only"
fi

# Clean stale state. The local path is both the bind target and the local rootfs dir.
NH_LINK="$MNT"
if [ -L "$NH_LINK" ]; then
    rm -f "$NH_LINK"
    log "removed stale symlink $NH_LINK"
elif mountpoint -q "$MNT" 2>/dev/null; then
    umount -l "$MNT" 2>/dev/null
    log "unmounted stale mount $MNT"
fi

# Choose source: SD -> local fallback -> stub
if [ -n "$SD_UUID" ] && [ -d "$SD_CHROOT/bin" ]; then
    CHROOT_SRC="$SD_CHROOT"
    log "using SD rootfs $SD_CHROOT"
elif [ -d "$LOCAL_CHROOT/bin" ]; then
    CHROOT_SRC="$LOCAL_CHROOT"
    log "using local rootfs $LOCAL_CHROOT"
else
    CHROOT_SRC="$LOCAL_CHROOT"
    mkdir -p "$CHROOT_SRC/bin"
    log "no rootfs found, created stub at $CHROOT_SRC"
fi

mkdir -p "$MNT"
if [ "$CHROOT_SRC" = "$MNT" ]; then
    # Self-bind is pointless; local rootfs is already in place.
    log "source equals mount point, no bind needed"
elif mount --bind "$CHROOT_SRC" "$MNT"; then
    log "bind-mounted $CHROOT_SRC -> $MNT"
else
    log "ERROR: bind mount $CHROOT_SRC -> $MNT failed"
    exit 1
fi

log "guard done"
exit 0
