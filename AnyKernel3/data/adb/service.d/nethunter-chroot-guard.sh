#!/system/bin/sh
# NetHunter Chroot Guard — ensures stable boot with or without SD card
# Placed in /data/adb/service.d/ (runs as root after boot)
# Uses bind-mount instead of symlink so /proc/mounts shows the app's expected path

SD_CHROOT="/mnt/expand/54ee4888-8f46-4b0b-9d5b-d9443ce0e170/kali-arm64"
LOCAL_CHROOT="/data/local/nhsystem/kali-arm64.local"
NH_LINK="/data/local/nhsystem/kali-arm64"
NH_PKG="com.offsec.nethunter"
LOG="/data/local/nhsystem/chroot-guard.log"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOG"; }

log "=== Chroot Guard started ==="

# Wait for device unlock (SD won't decrypt until then)
TRIES=0
while [ $TRIES -lt 60 ]; do
    if [ -d "/data/data/$NH_PKG" ]; then
        break
    fi
    TRIES=$((TRIES + 1))
    sleep 5
done

# Give vold time to mount + fsck the SD (can take 5+ min for large f2fs)
TRIES=0
SD_FOUND=false
while [ $TRIES -lt 90 ]; do
    if [ -d "$SD_CHROOT/bin" ]; then
        SD_FOUND=true
        break
    fi
    TRIES=$((TRIES + 1))
    sleep 5
done

# Clean up old symlink or stale bind mount
if [ -L "$NH_LINK" ]; then
    rm -f "$NH_LINK"
elif mountpoint -q "$NH_LINK" 2>/dev/null; then
    umount "$NH_LINK" 2>/dev/null
fi

CHROOT_SRC=""
if [ "$SD_FOUND" = "true" ]; then
    CHROOT_SRC="$SD_CHROOT"
    log "SD chroot found: $SD_CHROOT"
elif [ -d "$LOCAL_CHROOT/bin" ]; then
    CHROOT_SRC="$LOCAL_CHROOT"
    log "SD not found, local fallback: $LOCAL_CHROOT"
else
    mkdir -p "$LOCAL_CHROOT/bin"
    CHROOT_SRC="$LOCAL_CHROOT"
    log "WARNING: No chroot found, created stub at $LOCAL_CHROOT"
fi

mkdir -p "$NH_LINK"
mount --bind "$CHROOT_SRC" "$NH_LINK"
log "Bind-mounted $CHROOT_SRC -> $NH_LINK"

# Ensure kalifs symlink is consistent
rm -f /data/local/nhsystem/kalifs
ln -s /data/local/nhsystem/kali-arm64 /data/local/nhsystem/kalifs

log "Chroot guard complete. Mount: $(mountpoint -q $NH_LINK && echo 'active' || echo 'failed')"
