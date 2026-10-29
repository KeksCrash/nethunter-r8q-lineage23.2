#!/bin/bash
set -e

SDK="$HOME/Android/Sdk"
BT="$SDK/build-tools/36.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
PKG="de.nethunter.imsicatcher"

SRC="app/src/main"
BUILD="build"
GEN="$BUILD/gen"
CLASSES="$BUILD/classes"
APK_UNSIGNED="$BUILD/ImsiCatcher-unsigned.apk"
APK_ALIGNED="$BUILD/ImsiCatcher-aligned.apk"
APK_SIGNED="$BUILD/ImsiCatcher.apk"

rm -rf "$BUILD"
mkdir -p "$GEN" "$CLASSES" "$BUILD/res-compiled"

echo "=== Compile resources ==="
"$BT/aapt2" compile --dir "$SRC/res" -o "$BUILD/res-compiled/"

echo "=== Link resources ==="
"$BT/aapt2" link \
    -o "$APK_UNSIGNED" \
    -I "$PLATFORM" \
    --manifest "$SRC/AndroidManifest.xml" \
    --java "$GEN" \
    --auto-add-overlay \
    "$BUILD/res-compiled/"*.flat

echo "=== Compile Java ==="
find "$SRC/java" "$GEN" -name '*.java' > "$BUILD/sources.txt"
javac -source 11 -target 11 \
    -classpath "$PLATFORM" \
    -d "$CLASSES" \
    @"$BUILD/sources.txt"

echo "=== DEX ==="
"$BT/d8" --release --output "$BUILD" \
    --lib "$PLATFORM" \
    $(find "$CLASSES" -name '*.class')

echo "=== Package ==="
# Add dex to APK
cp "$APK_UNSIGNED" "$BUILD/tmp.apk"
cd "$BUILD"
zip -j tmp.apk classes.dex
cd ..

# Add assets
cd "$SRC"
zip -r "../../../$BUILD/tmp.apk" assets/
cd "../../../"

mv "$BUILD/tmp.apk" "$APK_UNSIGNED"

echo "=== Align ==="
"$BT/zipalign" -f 4 "$APK_UNSIGNED" "$APK_ALIGNED"

echo "=== Sign ==="
# Generate debug keystore if needed
KEYSTORE="$BUILD/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias debug \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Debug,O=NetHunter,C=DE"
fi

"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias debug \
    --out "$APK_SIGNED" \
    "$APK_ALIGNED"

echo "=== Done ==="
ls -la "$APK_SIGNED"
echo "Install: adb install $APK_SIGNED"
