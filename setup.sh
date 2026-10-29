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
fi

echo ""
echo "=== Setup complete ==="
echo "To build: cd $DIR && bash build.sh"
echo ""
echo "Build produces:"
echo "  Kernel:  $PARENT_DIR/out/arch/arm64/boot/Image.gz-dtb"
echo "  ZIP:     $PARENT_DIR/build/r8q/Nethunter_r8q_v1.0.zip"
