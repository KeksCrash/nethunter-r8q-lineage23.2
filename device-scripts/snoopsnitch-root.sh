#!/system/bin/sh
export PATH=/product/bin:/system/bin:/system/xbin:$PATH
LOG=/data/local/tmp/snoopsnitch-root.log
echo "Fix gestartet: $(date)" >> "$LOG"
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
UID_SNOOP=$(pm list packages -U | sed -n "s/^package:de\.srlabs\.snoopsnitch uid://p")
[ -n "$UID_SNOOP" ] || exit 1
magisk --sqlite "INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES ($UID_SNOOP,2,0,1,1);"
echo "Fix ausgeführt für UID $UID_SNOOP" >> "$LOG"
