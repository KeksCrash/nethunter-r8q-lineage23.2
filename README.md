# NetHunter Kernel for Samsung Galaxy S20 FE (r8q) — LineageOS 23.2

Custom kernel build for running [Kali NetHunter](https://www.kali.org/docs/nethunter/) on the Samsung Galaxy S20 FE (`r8q`) with LineageOS 23.2 (Android 15).

## Kernel Info

- **Base**: Samsung SM-A526B/SM-G780F kernel `4.19.325`
- **Branch**: LineageOS 23.2 port from [LineageOS/android_kernel_samsung_r8q](https://github.com/AaronFeng753/android_kernel_samsung_r8q) (`lineage-23.2`)
- **Toolchain**: Clang 18 (`LLVM=1 LLVM_IAS=1`)
- **Defconfig**: `lineageos-r8q_defconfig`

## NetHunter Features (all enabled)

| Feature | Config | Status |
|---------|--------|--------|
| WiFi Monitor/Injection | `CFG80211=y`, `MAC80211=y`, `WIRELESS_EXT=y` | ✅ Built-in |
| External WiFi (RTL8812AU) | `88XXau` driver | ✅ Built-in |
| External WiFi (RTL8188EUS) | `RTL8188EU=m` | ✅ Module |
| External WiFi (RTL88x2BU) | `88x2bu` driver | ✅ Built-in |
| External WiFi (MT76x2U) | `MT76x2U`, `MT76x0U`, `MT7601U` | ✅ Built-in |
| External WiFi (Atheros) | `ath9k_htc`, `ath6kl` | ✅ Built-in |
| USB HID Gadget | `USB_CONFIGFS_F_HID=y` | ✅ Built-in |
| CAN-Bus | `CAN=y`, `CAN_RAW=y`, `CAN_VCAN=y`, `CAN_SLCAN=y` | ✅ Built-in |
| CAN USB Adapters | `CAN_GS_USB=y`, `CAN_PEAK_USB=y`, etc. | ✅ Built-in |
| DVB/SDR | `DVB_CORE=y`, `DVB_RTL2832_SDR=y` | ✅ Built-in |
| USB ACM (Serial) | `USB_ACM=y` | ✅ Built-in |
| NTFS Read/Write | `NTFS_RW=y` | ✅ Built-in |
| BPF/eBPF | `BPF=y`, `BPF_SYSCALL=y`, `BPF_JIT=y` | ✅ Built-in |
| Namespaces | `NAMESPACES=y`, `NET_NS=y` | ✅ Built-in |
| Bluetooth HCI USB | `BT_HCIBTUSB=y` | ✅ Built-in |
| TUN/TAP | `TUN=y` | ✅ Built-in |
| Bridge | `BRIDGE=y` | ✅ Built-in |

## Internal WiFi Monitor Mode

The internal QCA6390 uses the Qualcomm `qcacld-3.0` fullMAC driver. Monitor mode works via `con_mode=4` but **disables normal WiFi** (no internet while scanning):

```bash
# Enable monitor mode (kills WiFi/internet)
echo 4 | sudo tee /sys/module/wlan/parameters/con_mode
sudo ip link set wlan0 up
sudo airodump-ng wlan0

# Disable monitor mode (restore WiFi)
echo 0 | sudo tee /sys/module/wlan/parameters/con_mode
# Toggle WiFi off/on in Android settings
```

For simultaneous internet + monitor mode, use an **external USB WiFi adapter**.

## Build Fixes Applied (23.0 → 23.2 port)

1. `mmap_sem` → `mmap_lock` in `mm/memory.c` and `drivers/gator/gator_cookies.c`
2. `timer_delete_sync` compat shim in `include/linux/timer.h`
3. Realtek out-of-tree driver Makefile fixes (`EXTRA_CFLAGS` → `ccflags-y`)
4. RTL8188EUS function name mismatches fixed
5. `BPF_PRELOAD` disabled (incompatible with 4.19)
6. `dib7000p.c` self-assign warning fix

## Quick Start

```bash
# 1. Install dependencies
chmod +x setup.sh
./setup.sh              # full setup (Docker + native tools)
# or
./setup.sh --docker-only  # Docker only

# 2. Build the kernel
docker run --rm \
    -v $(pwd)/los23.2-nethunter/source:/w/source \
    -v $(pwd)/los23.2-nethunter/out:/w/out \
    -w /w/source \
    r8q-nethunter-lineage23.2-build:clang18 \
    sh -c "make O=/w/out ARCH=arm64 LLVM=1 LLVM_IAS=1 lineageos-r8q_defconfig && \
           make O=/w/out ARCH=arm64 LLVM=1 LLVM_IAS=1 -j\$(nproc)"

# 3. Kernel image at:
ls -lh los23.2-nethunter/out/arch/arm64/boot/Image
```

## Directory Structure

```
├── setup.sh                    # Dependency installer (Ubuntu/Arch/Fedora + Docker)
├── backup/                     # Boot image backups
│   ├── nethunter-magisk-working-boot.img   # Working NetHunter+Magisk boot (23.0 kernel)
│   ├── boot-los23.2-stock.img              # Stock LOS 23.2 boot
│   ├── lineage23-magisk-working-boot.img   # LOS 23.0 + Magisk boot
│   └── lineage23-original-boot.img         # Original LOS 23.0 boot
├── docker-result/              # Built kernel ZIPs
│   ├── Nethunter_r8q_LOS23.2_v1.0.zip     # 23.2 NetHunter kernel (AnyKernel3)
│   ├── Nethunter_r8q_v1.0.zip             # 23.0 NetHunter kernel (AnyKernel3)
│   └── nethunter.sha256
├── los23.2-nethunter/
│   ├── Dockerfile.kernel-builder           # Build environment Dockerfile
│   ├── build-nethunter-features.log        # Final successful build log
│   ├── official-23.0-to-23.2.patch         # Port patch (23.0 → 23.2)
│   ├── config-before-*                     # Config snapshots
│   └── source/                             # Kernel source tree (4.19.325)
│       └── arch/arm64/configs/lineageos-r8q_defconfig
└── README.md
```

## Device Info

- **Device**: Samsung Galaxy S20 FE (SM-G780F)
- **Codename**: `r8q`
- **SoC**: Snapdragon 865 (SM8250)
- **WiFi**: Qualcomm QCA6390 (qcacld-3.0)
- **Boot partition**: `/dev/block/sda23`
- **ROM**: LineageOS 23.2 (Android 15, SDK 36)

## License

Kernel source: GPL-2.0 (as per upstream Linux kernel)
