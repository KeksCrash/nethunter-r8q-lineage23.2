#!/usr/bin/env zsh
set -e
set -o pipefail
cd -- "${0:A:h}"
serial=${ANDROID_SERIAL:-$(adb get-serialno)}
a=(adb -s "$serial")
[[ "$("${a[@]}" shell -T -n getprop ro.product.device)" == r8q ]] || exit 1
base=/data/local/tmp/meddl-soundboard
"${a[@]}" install --no-incremental -r Meddl-Soundboard.apk
"${a[@]}" shell -T -n "umask 077; mkdir -p $base; chmod 700 $base"
if ! "${a[@]}" shell -T -n "test -s $base/token"; then
  openssl rand -hex 32 | "${a[@]}" shell -T "umask 077; cat > $base/token"
fi
secret=$("${a[@]}" shell -T -n "cat $base/token")
[[ ${#secret} == 64 && "$secret" != *[!0-9a-f]* ]] || exit 1
provision=$("${a[@]}" shell -T -n am broadcast --user 0 -n de.local.meddl/.ProvisionReceiver --es token "$secret")
[[ "$provision" == *'result=1'* ]] || { print -u2 'Einrichtung der App fehlgeschlagen'; exit 1; }
unset secret provision
"${a[@]}" shell -T -n cmd appops set de.local.meddl SYSTEM_ALERT_WINDOW allow
"${a[@]}" shell -T -n "cd $base; if test -s pid; then old=\$(cat pid); if kill -0 \"\$old\" 2>/dev/null && tr '\\000' ' ' < /proc/\$old/cmdline | grep -q 'BoardDaemon serve'; then kill \"\$old\"; fi; fi"
"${a[@]}" push BoardDaemon.jar device-start.sh "$base/"
"${a[@]}" shell -T -n "chmod 444 $base/BoardDaemon.jar; chmod 700 $base/device-start.sh; rm -f $base/meddl-soundboard.sh"
if [[ "${1:-}" == probe ]]; then
  "${a[@]}" shell -T -n "sh $base/device-start.sh 2000"
else
  "${a[@]}" shell -T -n "sh $base/device-start.sh"
fi
sleep 1
"${a[@]}" shell -T -n "CLASSPATH=$base/BoardDaemon.jar app_process /system/bin BoardDaemon status"
if [[ "${1:-}" != background ]]; then
  "${a[@]}" shell -T -n am start -n de.local.meddl/.BoardActivity
fi
