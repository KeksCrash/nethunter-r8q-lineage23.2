#!/usr/bin/env zsh
set -e
set -o pipefail
serial=${ANDROID_SERIAL:-$(adb get-serialno)}
a=(adb -s "$serial")
[[ "$("${a[@]}" shell -T -n getprop ro.product.device)" == r8q ]] || exit 1
parecord --device=TikTok_KI.monitor --raw --format=s16le --rate=48000 --channels=1 --latency-msec=25 |
  "${a[@]}" shell -T 'CLASSPATH=/data/local/tmp/meddl-soundboard/BoardDaemon.jar app_process /system/bin BoardDaemon pcm'
