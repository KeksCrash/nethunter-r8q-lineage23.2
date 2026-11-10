#!/system/bin/sh
set -e
base=/data/local/tmp/meddl-soundboard
cd "$base"
if [ -f pid ]; then
    old=$(cat pid)
    if kill -0 "$old" 2>/dev/null && tr '\000' ' ' <"/proc/$old/cmdline" | grep -q 'BoardDaemon serve'; then
        exit 0
    fi
fi
target=${1:-$(cmd package list packages -U --user 0 com.zhiliaoapp.musically |
    awk '$1 == "package:com.zhiliaoapp.musically" {sub(/^uid:/, "", $2); print $2}')}
case "$target" in ''|*[!0-9]*) exit 1 ;; esac
CLASSPATH="$base/BoardDaemon.jar" toybox nohup app_process /system/bin BoardDaemon serve "$target"     < /dev/null >> "$base/daemon.log" 2>&1 &
echo "$!" > pid
