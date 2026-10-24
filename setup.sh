#!/usr/bin/env bash
set -euo pipefail

# NetHunter r8q Kernel Build Environment Setup
# Samsung Galaxy S20 FE (r8q) - LineageOS 23.2 - Kernel 4.19.325
#
# Supports: Ubuntu/Debian, Arch/Manjaro, Fedora
# Usage: ./setup.sh [--docker-only]

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[+]${NC} $*"; }
warn()  { echo -e "${YELLOW}[!]${NC} $*"; }
error() { echo -e "${RED}[-]${NC} $*"; exit 1; }

DOCKER_ONLY=false
[[ "${1:-}" == "--docker-only" ]] && DOCKER_ONLY=true

# ── Detect distro ──────────────────────────────────────────────
detect_distro() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        echo "$ID"
    else
        error "Cannot detect distro"
    fi
}

DISTRO=$(detect_distro)
info "Detected distro: $DISTRO"

# ── Docker (all distros) ──────────────────────────────────────
install_docker() {
    if command -v docker &>/dev/null; then
        info "Docker already installed: $(docker --version)"
        return
    fi
    info "Installing Docker..."
    case "$DISTRO" in
        ubuntu|debian)
            sudo apt-get update
            sudo apt-get install -y docker.io docker-compose-v2
            ;;
        arch|manjaro|cachyos)
            sudo pacman -S --noconfirm docker docker-compose
            ;;
        fedora)
            sudo dnf install -y docker docker-compose
            ;;
        *)
            warn "Unknown distro for Docker install, trying snap..."
            sudo snap install docker
            ;;
    esac
    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER"
    warn "Added $USER to docker group — log out and back in for it to take effect"
}

# ── Build dependencies (native, non-Docker) ──────────────────
install_build_deps() {
    info "Installing kernel build dependencies..."
    case "$DISTRO" in
        ubuntu|debian)
            sudo apt-get update
            sudo apt-get install -y --no-install-recommends \
                clang-18 lld-18 llvm-18 \
                gcc-aarch64-linux-gnu binutils-aarch64-linux-gnu \
                gcc-arm-linux-gnueabihf binutils-arm-linux-gnueabihf \
                build-essential bc bison flex libssl-dev libelf-dev \
                libncurses-dev make perl python3 python-is-python3 \
                device-tree-compiler cpio rsync git zip unzip file \
                xz-utils ca-certificates dwarves kmod zstd lz4 \
                adb fastboot
            # Symlink clang-18 tools
            for x in clang clang++ ld.lld llvm-ar llvm-nm llvm-objcopy \
                     llvm-objdump llvm-strip llvm-readelf; do
                sudo ln -sf "/usr/bin/${x}-18" "/usr/local/bin/${x}" 2>/dev/null || true
            done
            ;;
        arch|manjaro|cachyos)
            sudo pacman -S --noconfirm \
                clang lld llvm \
                aarch64-linux-gnu-gcc aarch64-linux-gnu-binutils \
                arm-none-eabi-gcc arm-none-eabi-binutils \
                base-devel bc bison flex openssl libelf ncurses \
                make perl python dtc cpio rsync git zip unzip file \
                xz ca-certificates pahole kmod zstd lz4 \
                android-tools
            ;;
        fedora)
            sudo dnf install -y \
                clang lld llvm \
                gcc-aarch64-linux-gnu binutils-aarch64-linux-gnu \
                gcc-arm-linux-gnu binutils-arm-linux-gnu \
                gcc gcc-c++ bc bison flex openssl-devel elfutils-libelf-devel \
                ncurses-devel make perl python3 dtc cpio rsync git zip unzip file \
                xz ca-certificates dwarves kmod zstd lz4 \
                android-tools
            ;;
        *)
            error "Unsupported distro: $DISTRO — use --docker-only or install deps manually"
            ;;
    esac
}

# ── ADB/Flashing tools ───────────────────────────────────────
install_flash_tools() {
    info "Installing flashing tools..."
    case "$DISTRO" in
        ubuntu|debian)
            sudo apt-get install -y adb fastboot heimdall-flash
            ;;
        arch|manjaro|cachyos)
            sudo pacman -S --noconfirm android-tools heimdall
            ;;
        fedora)
            sudo dnf install -y android-tools heimdall
            ;;
    esac
}

# ── Build the Docker image ────────────────────────────────────
build_docker_image() {
    local dockerfile="los23.2-nethunter/Dockerfile.kernel-builder"
    if [ ! -f "$dockerfile" ]; then
        error "Dockerfile not found at $dockerfile"
    fi
    info "Building Docker image r8q-nethunter-lineage23.2-build:clang18..."
    docker build -t r8q-nethunter-lineage23.2-build:clang18 -f "$dockerfile" .
    info "Docker image built successfully"
}

# ── Main ──────────────────────────────────────────────────────
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  NetHunter r8q Kernel Build Setup                          ║"
    echo "║  Samsung Galaxy S20 FE · LineageOS 23.2 · Kernel 4.19.325  ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    install_docker

    if [ "$DOCKER_ONLY" = true ]; then
        info "Docker-only mode — skipping native build deps"
    else
        install_build_deps
        install_flash_tools
    fi

    build_docker_image

    echo ""
    info "Setup complete!"
    echo ""
    echo "  Build the kernel:"
    echo "    docker run --rm -v \$(pwd)/los23.2-nethunter/source:/w/source \\"
    echo "                    -v \$(pwd)/los23.2-nethunter/out:/w/out \\"
    echo "                    -w /w/source \\"
    echo "                    r8q-nethunter-lineage23.2-build:clang18 \\"
    echo "                    make O=/w/out ARCH=arm64 LLVM=1 LLVM_IAS=1 \\"
    echo "                    lineageos-r8q_defconfig && \\"
    echo "                    make O=/w/out ARCH=arm64 LLVM=1 LLVM_IAS=1 -j\$(nproc)"
    echo ""
    echo "  Output: los23.2-nethunter/out/arch/arm64/boot/Image"
    echo ""
}

main "$@"
