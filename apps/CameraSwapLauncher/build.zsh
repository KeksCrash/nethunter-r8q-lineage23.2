#!/usr/bin/env zsh
set -e
cd -- "${0:A:h}"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
tools="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
tmp=$(mktemp -d)
trap 'rm -rf -- "$tmp"' EXIT
mkdir "$tmp/classes" "$tmp/dex"
javac --release 17 -cp "$platform" -d "$tmp/classes" src/de/local/karicamera/LauncherActivity.java
"$tools/d8" --min-api 26 --lib "$platform" --output "$tmp/dex" \
  "$tmp/classes/de/local/karicamera/LauncherActivity.class"
"$tools/aapt2" compile --dir res -o "$tmp/resources.zip"
"$tools/aapt2" link -I "$platform" --manifest AndroidManifest.xml -o "$tmp/unsigned.apk" "$tmp/resources.zip"
(cd "$tmp/dex" && zip -q "$tmp/unsigned.apk" classes.dex)
"$tools/zipalign" -p -f 4 "$tmp/unsigned.apk" "$tmp/aligned.apk"
if [[ ! -f kari-camera-local.p12 ]]; then
  (umask 077; keytool -genkeypair -noprompt -keystore kari-camera-local.p12 -storepass local-android-signing \
    -alias kari-camera -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=KARI Camera Launcher Local')
fi
"$tools/apksigner" sign --ks kari-camera-local.p12 --ks-pass pass:local-android-signing \
  --out Kari-Kamera-Swap.apk "$tmp/aligned.apk"
"$tools/apksigner" verify Kari-Kamera-Swap.apk
