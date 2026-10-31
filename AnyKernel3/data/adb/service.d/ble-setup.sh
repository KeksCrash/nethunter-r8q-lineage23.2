#!/system/bin/sh
# BLE permissions for NetHunter chroot
# Ensures /dev/vhci and /dev/uhid are accessible, and BT is unblocked

sleep 10

chmod 666 /dev/vhci 2>/dev/null
chmod 666 /dev/uhid 2>/dev/null
rfkill unblock bluetooth 2>/dev/null
