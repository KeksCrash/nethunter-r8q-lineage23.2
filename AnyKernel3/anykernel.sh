# AnyKernel3 Ramdisk Mod Script
# osm0sis @ xda-developers

## AnyKernel setup
# begin properties
properties() { '
kernel.string=r8q kernel by Svirusx @ xda-developers
do.devicecheck=1
do.modules=0
do.systemless=1
do.cleanup=1
do.cleanuponabort=0
device.name1=r8q
device.name2=
device.name3=
device.name4=
device.name5=
supported.versions=
supported.patchlevels=
'; } # end properties

# shell variables
block=/dev/block/platform/soc/1d84000.ufshc/by-name/boot;
is_slot_device=0;
ramdisk_compression=auto;


## AnyKernel methods (DO NOT CHANGE)
# import patching functions/variables - see for reference
. tools/ak3-core.sh;


## NetHunter additions

SYSTEM="/system";
SYSTEM_ROOT="/system_root";
VENDOR="/vendor";

setperm() {
	find "$3" -type d -exec chmod "$1" {} \;
	find "$3" -type f -exec chmod "$2" {} \;
}

install() {
	setperm "$2" "$3" "$home$1";
	if [ "$4" ]; then
		cp -r "$home$1" "$(dirname "$4")/";
		return;
	fi;
	cp -r "$home$1" "$(dirname "$1")/";
}

[ -d $home/vendor/firmware_mnt/image ] && {
	install "/vendor/firmware_mnt/image" 0755 0644 "$VENDOR/firmware_mnt/image";
}

[ -d $home/system/etc/init.d ] && {
	install "/system/etc/init.d" 0755 0755 "$SYSTEM/etc/init.d";
}

[ -d $home/system/lib ] && {
	install "/system/lib" 0755 0644 "$SYSTEM/lib";
}

[ -d $home/system/lib64 ] && {
	install "/system/lib64" 0755 0644 "$SYSTEM/lib64";
}

[ -d $home/system/bin ] && {
	install "/system/bin" 0755 0755 "$SYSTEM/bin";
}

[ -d $home/system/xbin ] && {
	install "/system/xbin" 0755 0755 "$SYSTEM/xbin";
}

[ -d $home/data/local ] && {
	install "/data/local" 0755 0644;
}

## Magisk service.d scripts (USB hotplug, AP setup)
[ -d $home/data/adb/service.d ] && {
	ui_print "Installing Magisk service.d scripts...";
	mkdir -p /data/adb/service.d;
	cp -f $home/data/adb/service.d/usbfs.sh /data/adb/service.d/;
	cp -f $home/data/adb/service.d/usb-hotplug.sh /data/adb/service.d/;
	cp -f $home/data/adb/service.d/setup-ap.sh /data/adb/service.d/;
	chmod 755 /data/adb/service.d/usbfs.sh;
	chmod 755 /data/adb/service.d/usb-hotplug.sh;
	chmod 755 /data/adb/service.d/setup-ap.sh;
}

## NetHunter configs (Evil Twin / Mana / DHCP)
NH_ROOT="/data/local/nhsystem/kali-arm64";
[ -d $home/nethunter/configs ] && {
	ui_print "Installing NetHunter Evil Twin configs...";
	mkdir -p /sdcard/nh_files/configs;
	cp -f $home/nethunter/configs/hostapd-karma.conf /sdcard/nh_files/configs/;
	cp -f $home/nethunter/configs/iptables.conf /sdcard/nh_files/configs/;
	cp -f $home/nethunter/configs/dnsmasq.conf /sdcard/nh_files/configs/;
	chmod 644 /sdcard/nh_files/configs/hostapd-karma.conf;
	chmod 755 /sdcard/nh_files/configs/iptables.conf;
	chmod 644 /sdcard/nh_files/configs/dnsmasq.conf;
	# Chroot configs (only if chroot exists)
	if [ -d "$NH_ROOT/etc/mana-toolkit" ]; then
		cp -f $home/nethunter/configs/dnsmasq-dhcpd.conf $NH_ROOT/etc/mana-toolkit/;
		cp -f $home/nethunter/configs/start-nat-simple-lollipop.sh $NH_ROOT/usr/share/mana-toolkit/run-mana/;
		chmod 644 $NH_ROOT/etc/mana-toolkit/dnsmasq-dhcpd.conf;
		chmod 755 $NH_ROOT/usr/share/mana-toolkit/run-mana/start-nat-simple-lollipop.sh;
	fi;
}

## Chroot fixes (lsusb wrapper for no-udev environment)
[ -d $home/nethunter/chroot-fixes ] && [ -d "$NH_ROOT/usr/bin" ] && {
	ui_print "Installing chroot fixes (lsusb)...";
	# Backup original libusb-based lsusb (broken in chroot — needs udev)
	[ -f "$NH_ROOT/usr/bin/lsusb" ] && [ ! -f "$NH_ROOT/usr/bin/lsusb.orig" ] && \
		mv $NH_ROOT/usr/bin/lsusb $NH_ROOT/usr/bin/lsusb.orig;
	cp -f $home/nethunter/chroot-fixes/lsusb $NH_ROOT/usr/bin/lsusb;
	chmod 755 $NH_ROOT/usr/bin/lsusb;
	mkdir -p $NH_ROOT/usr/local/bin;
	cp -f $home/nethunter/chroot-fixes/lsusb $NH_ROOT/usr/local/bin/lsusb;
	chmod 755 $NH_ROOT/usr/local/bin/lsusb;
}

[ -d $home/ramdisk-patch ] && {
	setperm "0755" "0750" "$home/ramdisk-patch";
        chown root:shell $home/ramdisk-patch/*;
	cp -rp $home/ramdisk-patch/* "$SYSTEM_ROOT/";
}

if [ ! "$(grep /init.nethunter.rc $SYSTEM_ROOT/init.rc)" ]; then
  insert_after_last "$SYSTEM_ROOT/init.rc" "import .*\.rc" "import /init.nethunter.rc";
fi;

if [ ! "$(grep /dev/hidg* $SYSTEM_ROOT/ueventd.rc)" ]; then
  insert_after_last "$SYSTEM_ROOT/ueventd.rc" "/dev/kgsl.*root.*root" "# HID driver\n/dev/hidg* 0666 root root";
fi;

ui_print "Applying additional anykernel installation patches";
for p in $(find ak_patches/ -type f); do
  ui_print "Applying $p";
  . $p;
done

## End NetHunter additions

## AnyKernel file attributes
# set permissions/ownership for included ramdisk files
chmod -R 750 $ramdisk/*;
chown -R root:root $ramdisk/*;


## AnyKernel install
dump_boot;

# begin ramdisk changes

if [ -d $ramdisk/.backup ]; then
  patch_cmdline "skip_override" "skip_override";
else
  patch_cmdline "skip_override" "";
fi;

patch_cmdline "printk.synchronous" "printk.synchronous=1";

# end ramdisk changes

write_boot;
## end install

