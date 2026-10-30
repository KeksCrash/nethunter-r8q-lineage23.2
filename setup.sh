#!/bin/bash
# Setup script for NetHunter r8q kernel build environment
# Downloads all required toolchains and dependencies

set -e

DIR="$(readlink -f .)"
PARENT_DIR="$(readlink -f "${DIR}/..")"

echo "=== NetHunter r8q Kernel Build Setup ==="
echo "Source: $DIR"
echo "Toolchains will be installed to: $PARENT_DIR"
echo ""

# 1. Android Clang (r547379)
CLANG_DIR="$PARENT_DIR/clang"
if [ -x "$CLANG_DIR/bin/clang" ]; then
    echo "[OK] Clang already installed at $CLANG_DIR"
else
    echo "[*] Downloading Android Clang r547379..."
    mkdir -p "$CLANG_DIR"
    CLANG_URL="https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+archive/refs/heads/main/clang-r547379.tar.gz"
    # Try the prebuilt archive first, fall back to GitHub mirror
    if ! curl -fSL "$CLANG_URL" | tar xz -C "$CLANG_DIR" 2>/dev/null; then
        echo "[*] Trying alternative: cloning from AOSP prebuilts..."
        git clone --depth=1 --single-branch \
            https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86 \
            /tmp/clang-prebuilts 2>/dev/null || true
        if [ -d "/tmp/clang-prebuilts/clang-r547379" ]; then
            cp -a /tmp/clang-prebuilts/clang-r547379/* "$CLANG_DIR/"
            rm -rf /tmp/clang-prebuilts
        else
            echo "[!] Could not download clang r547379 automatically."
            echo "    Please download it manually and extract to: $CLANG_DIR"
            echo "    Source: https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+/refs/heads/main/clang-r547379/"
        fi
    fi
fi

# 2. GCC 4.9 Cross Compiler (aarch64-linux-android)
GCC_DIR="$PARENT_DIR/aarch64-linux-android-4.9"
if [ -x "$GCC_DIR/bin/aarch64-linux-android-gcc" ]; then
    echo "[OK] GCC 4.9 already installed at $GCC_DIR"
else
    echo "[*] Downloading GCC 4.9 aarch64 cross-compiler..."
    git clone --depth=1 \
        https://android.googlesource.com/platform/prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9 \
        "$GCC_DIR" 2>&1 | tail -1
    echo "[OK] GCC 4.9 installed"
fi

# 3. Build output directory
mkdir -p "$PARENT_DIR/out"
mkdir -p "$PARENT_DIR/build/r8q/modules"

# 4. AnyKernel3 (already in source tree, copy to parent if missing)
if [ ! -d "$PARENT_DIR/AnyKernel3" ]; then
    echo "[*] Copying AnyKernel3 template to $PARENT_DIR/AnyKernel3"
    cp -rf "$DIR/AnyKernel3" "$PARENT_DIR/AnyKernel3"
fi

# 5. Host dependencies check
echo ""
echo "=== Checking host dependencies ==="
MISSING=""
for cmd in make gcc flex bison bc libssl-dev; do
    case "$cmd" in
        libssl-dev)
            if ! dpkg -l libssl-dev &>/dev/null && ! rpm -q openssl-devel &>/dev/null; then
                MISSING="$MISSING $cmd"
            else
                echo "[OK] $cmd"
            fi
            ;;
        *)
            if command -v "$cmd" &>/dev/null; then
                echo "[OK] $cmd ($(command -v "$cmd"))"
            else
                MISSING="$MISSING $cmd"
            fi
            ;;
    esac
done

if [ -n "$MISSING" ]; then
    echo ""
    echo "[!] Missing host packages:$MISSING"
    echo "    Install with: sudo apt install -y$MISSING (Debian/Ubuntu)"
    echo "    Or: sudo pacman -S$MISSING (Arch)"
    exit 1
fi

# 6. Android SDK check (for IMSI Catcher App)
echo ""
echo "=== Checking Android SDK ==="
ANDROID_SDK="$HOME/Android/Sdk"
ANDROID_BT=""
if [ -d "$ANDROID_SDK/build-tools" ]; then
    ANDROID_BT=$(ls -d "$ANDROID_SDK/build-tools"/*/ 2>/dev/null | sort -V | tail -1)
    if [ -n "$ANDROID_BT" ] && [ -x "${ANDROID_BT}aapt2" ]; then
        echo "[OK] Android SDK build-tools: $ANDROID_BT"
    else
        ANDROID_BT=""
    fi
fi
if [ -z "$ANDROID_BT" ]; then
    echo "[!] Android SDK not found — IMSI Catcher app will NOT be built"
    echo "    Install: https://developer.android.com/studio#command-line-tools-only"
fi

# 7. Build kernel
echo ""
echo "=== Building kernel ==="
bash "$DIR/build.sh"
KERNEL_ZIP="$PARENT_DIR/build/r8q/Nethunter_r8q_v1.0.zip"

# 8. Build IMSI Catcher App (if SDK available)
APK_PATH=""
if [ -n "$ANDROID_BT" ]; then
    echo ""
    echo "=== Building IMSI Catcher App ==="
    (cd "$DIR/apps/ImsiCatcher" && bash build.sh)
    APK_PATH="$DIR/apps/ImsiCatcher/build/ImsiCatcher.apk"
fi

# 9. Summary
echo ""
echo "========================================="
echo "=== Build complete ==="
echo "========================================="
echo ""
echo "Kernel ZIP:  $KERNEL_ZIP"
if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
    echo "IMSI App:    $APK_PATH"
fi
echo ""
echo "=== Deploy to device ==="
echo "  # Flash kernel (via TWRP or ADB sideload):"
echo "  adb sideload $KERNEL_ZIP"
echo ""
echo "  # Install IMSI Catcher App:"
if [ -n "$APK_PATH" ]; then
    echo "  adb install $APK_PATH"
else
    echo "  (build Android SDK first, then re-run setup.sh)"
fi
echo ""
echo "  # Push Magisk modules:"
echo "  adb push $DIR/magisk-modules/diag-fix /data/adb/modules/diag-fix"
echo "  adb push $DIR/magisk-modules/mt76-firmware /data/adb/modules/mt76-firmware"
echo ""
echo "  # Push device scripts (included in kernel ZIP, but can be updated separately):"
echo "  adb push $DIR/device-scripts/usbfs.sh /data/adb/service.d/"
echo "  adb push $DIR/device-scripts/usb-hotplug.sh /data/adb/service.d/"
echo "  adb push $DIR/device-scripts/setup-ap.sh /data/adb/service.d/"
echo "  adb shell mkdir -p /data/local/bin"
echo "  adb push $DIR/device-scripts/imsi-catcher-detect.sh /data/local/bin/"
echo ""
echo "  # Update NetHunter Evil Twin configs:"
echo "  adb push $DIR/device-scripts/nh-configs/hostapd-karma.conf /sdcard/nh_files/configs/"
echo "  adb push $DIR/device-scripts/nh-configs/iptables.conf /sdcard/nh_files/configs/"
echo "  adb push $DIR/device-scripts/nh-configs/dnsmasq.conf /sdcard/nh_files/configs/"
echo ""
echo "  # Grant su access (after install, check uid with: stat -c %u /data/user/0/<pkg>/):"
echo "  adb shell 'su -c \"magisk --sqlite \\\"INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES(<UID>,2,0,1,0)\\\"\"'"
