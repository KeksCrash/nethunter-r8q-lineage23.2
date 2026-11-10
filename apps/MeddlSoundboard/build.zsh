#!/usr/bin/env zsh
set -e
cd -- "${0:A:h}"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
tools="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
tmp=$(mktemp -d)
trap 'rm -rf -- "$tmp"' EXIT
for clip in metall-leute etzela a-frosch halt-stopp halt-stopp-variante alles-bleibt; do
  [[ -s "assets/$clip.pcm" ]] || { print -u2 "Fehlt: assets/$clip.pcm (siehe README.md)"; exit 1; }
done
mkdir "$tmp/app" "$tmp/daemon" "$tmp/dex"
javac --release 17 -cp "$platform" -d "$tmp/app" src/de/local/meddl/*.java
"$tools/d8" --min-api 36 --lib "$platform" --output "$tmp/dex" "$tmp"/app/de/local/meddl/*.class
"$tools/aapt2" compile --dir res -o "$tmp/resources.zip"
"$tools/aapt2" link -I "$platform" --manifest AndroidManifest.xml -A assets \
  -o "$tmp/unsigned.apk" "$tmp/resources.zip"
(cd "$tmp/dex" && zip -q "$tmp/unsigned.apk" classes.dex)
"$tools/zipalign" -p -f 4 "$tmp/unsigned.apk" "$tmp/aligned.apk"
if [[ ! -f meddl-local.p12 ]]; then
  (umask 077; keytool -genkeypair -noprompt -keystore meddl-local.p12 -storepass local-android-signing \
    -alias meddl -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Meddl Soundboard Local')
fi
"$tools/apksigner" sign --ks meddl-local.p12 --ks-pass pass:local-android-signing \
  --out Meddl-Soundboard.apk "$tmp/aligned.apk"
"$tools/apksigner" verify Meddl-Soundboard.apk
javac --release 17 -cp "$platform" -d "$tmp/daemon" BoardDaemon.java PhoneMic.java AudioBridge.java
"$tools/d8" --min-api 36 --lib "$platform" --output "$tmp/BoardDaemon.jar" "$tmp"/daemon/*.class
mv "$tmp/BoardDaemon.jar" BoardDaemon.jar
