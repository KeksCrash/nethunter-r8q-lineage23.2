#!/usr/bin/env bash
# Standalone SDK build; Gradle is optional. Never stores signing keys in the repo.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
VARIANT="${1:-install}"
case "$VARIANT" in
 install) SOURCE=app/src/main; VERSION_CODE=260; VERSION_NAME=2.6-kalilock; NAME=KaliLock ;;
 debug) SOURCE=debug-v2.5/src/main; VERSION_CODE=250; VERSION_NAME=2.5-kalilock-verify-submit; NAME=KaliLock-2.5-debug ;;
 *) echo 'Aufruf: build.sh [install|debug]' >&2; exit 2 ;;
esac
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="${ANDROID_BUILD_TOOLS:-$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)}"
PLATFORM="$SDK/platforms/android-35/android.jar"
for tool in aapt2 d8 zipalign apksigner; do test -x "$BT/$tool" || { echo "Build-Tool fehlt: $BT/$tool" >&2; exit 1; }; done
test -f "$PLATFORM"
BUILD="$PWD/build/$VARIANT"
# Only generated files for this variant are removed.
rm -rf -- "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/res"
"$BT/aapt2" compile --dir "$SOURCE/res" -o "$BUILD/res/"
sed 's/<manifest /<manifest package="de.nethunter.flipperunified" /' "$SOURCE/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
"$BT/aapt2" link -o "$BUILD/unsigned.apk" -I "$PLATFORM" --manifest "$BUILD/AndroidManifest.xml" --java "$BUILD/gen" --min-sdk-version 28 --target-sdk-version 28 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --auto-add-overlay "$BUILD/res/"*.flat
find "$SOURCE/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 -classpath "$PLATFORM" -d "$BUILD/classes" @"$BUILD/sources.txt"
mapfile -d '' CLASSES < <(find "$BUILD/classes" -name '*.class' -print0)
"$BT/d8" --release --min-api 28 --lib "$PLATFORM" --output "$BUILD" "${CLASSES[@]}"
(cd "$BUILD" && zip -q unsigned.apk classes*.dex)
"$BT/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
KEYSTORE="${KALILOCK_KEYSTORE:-$HOME/.android/debug.keystore}"
ALIAS="${KALILOCK_KEY_ALIAS:-androiddebugkey}"
if [[ ! -f "$KEYSTORE" ]]; then
 if [[ -n "${KALILOCK_KEYSTORE:-}" ]]; then echo 'Angegebener Signaturschlüssel fehlt' >&2; exit 1; fi
 mkdir -p -- "$(dirname -- "$KEYSTORE")"
 keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
export KALILOCK_STORE_PASS="${KALILOCK_STORE_PASS:-android}"
export KALILOCK_KEY_PASS="${KALILOCK_KEY_PASS:-$KALILOCK_STORE_PASS}"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass env:KALILOCK_STORE_PASS --key-pass env:KALILOCK_KEY_PASS --out "$BUILD/$NAME.apk" "$BUILD/aligned.apk"
"$BT/apksigner" verify "$BUILD/$NAME.apk"
sha256sum "$BUILD/$NAME.apk"
