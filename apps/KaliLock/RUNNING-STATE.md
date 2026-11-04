# Abgleich mit dem laufenden S20 FE — 04.11.2026

Dieser Stand ergänzt Kali Lock Commit `1f91ad6bd969` um die bereits lokal
vorhandenen Bluetooth-Kernelanpassungen und die Wiederherstellung der aktuell
verwendeten NFC-Live-Hooks. Kein Kernel-/ROM-Flash wurde durchgeführt.

## Laufender Kernel und Bluetooth

- Samsung SM-G781B / r8q, Android 16 / API 36.
- Kernel: `4.19.325-cip124-st8-Nethunter_r8q_v1.0`, Build #8 vom 31.10.2026.
- Laufender Kernel meldet `clang version 23.1.4`.
- `CONFIG_SERIAL_DEV_BUS=y`, `CONFIG_BT_HCIUART=y`,
  `CONFIG_BT_HCIUART_H4=y`, `CONFIG_BT_HCIUART_QCA=y` sind per
  `/proc/config.gz` bestätigt und stehen nun im versionierten wirus_defconfig.
- Android-Bluetooth ist aktiviert; `vendor.qcom.bluetooth.soc=hastings`.
- `hci_qca.c`: `qca_setup()` gibt bei fehlendem `hu->serdev` früh `-ENODEV`
  zurück, bevor der Zeiger dereferenziert wird. Das verhindert diesen konkreten
  Absturzpfad. Es implementiert **keine** funktionierende Hastings-Initialisierung
  über generisches TTY-`btattach`. Ob dieser C-Fix bereits im installierten
  Kernel-Binary steckt, wurde nicht binär nachgewiesen.

Prüfung: `make O=/tmp/kalilock-runtime-kconfig ARCH=arm64 HOSTCC=gcc CC=clang
LD=ld.lld CLANG_TRIPLE=aarch64-linux-gnu- LLVM=1 LLVM_IAS=1 wirus_defconfig`
war erfolgreich. Gegen die laufende Konfiguration bleiben mit Clang 23.1.4
nur drei Shadow-Call-Stack-Support-/Sichtbarkeitsunterschiede:
`ARCH_SUPPORTS_SHADOW_CALL_STACK`, `CC_HAVE_SHADOW_CALL_STACK`,
`SHADOW_CALL_STACK` (auf dem Handy deaktiviert). Die übrigen erfassten
CONFIG-Werte stimmen überein. Kein vollständiger Kernel-Neubau; keine Garantie
für ein bitgleiches Kernel-Binary.

## Kali Lock wie aktuell betrieben

App 2.6, VersionCode 260, Paket `de.nethunter.flipperunified`; Installations-APK
und originale Debug-2.5-Version liegen unter `apps/KaliLock/`.
Vorhandene Registrierung und verschlüsseltes Android-Passwort blieben beim Update
erhalten. Der NFC-Dienst wurde nach dem Update aus der App neu gestartet.

Screen-off-NFC funktioniert aktuell mit **zwei laufenden Frida-Hooks**. Die
Original-NFC-APEX wurde nicht ersetzt. Frida 17.25.2; der bereits vorhandene
Root-Server liegt auf dem Gerät unter `/data/local/tmp/kalilock-frida-server`.
SHA256 dieses Servers:
`7bb6219131819c11e5fe53b3e32fae0b8d1ff4c2a9c35c1a549d2703ea0f3b9b`.
Der Server selbst (ca. 85 MB) wird nicht als Binary ins Kernel-Repo kopiert.

### Live-Sitzung nach Neustart wiederherstellen

1. Gleiche Frida-Version für Host-CLI und Android-arm64-Server bereitstellen;
   den bekannten Root-Server auf dem Gerät starten. Keine öffentliche
   Netzwerkfreigabe/Autostart-Einrichtung durch diese Skripte.
2. Vorhandene Frida-Sitzungen beenden, bevor ein gemeinsamer Starter benutzt wird.
   Die noch laufenden Sitzungen wurden in dieser Arbeit bewusst nicht ersetzt.
3. Mit der Seriennummer aus `adb devices -l` prüfen und starten:

```sh
bash apps/KaliLock/nfc-patches/start-live.sh --check ADB-SERIENNUMMER
bash apps/KaliLock/nfc-patches/start-live.sh ADB-SERIENNUMMER
```

`FRIDA_BIN` kann den Host-CLI-Pfad setzen. Der Starter lädt `screenoff-v4.js`
und `dispatch-live.js` zusammen; beendet wird mit Strg+C. Bei NFC-Prozess- oder
Handyneustart neu anhängen. `--check` lädt keine Hooks; es prüft CLI, Gerät,
Quelldateien und NFC-PID, nicht die Funktionsfähigkeit des Root-Servers.
Kali Lock öffnen und NFC-Dienst starten. Automatik setzt weiterhin gespeicherten
Schlüssel, gültige FIDO-Signatur und lokal eingerichtetes Passwort voraus.
Keine Passwörter, Schlüssel oder persönlichen SharedPreferences ins Git kopieren.

### Dauerhafter ROM-Weg

`apps/KaliLock/nfc-patches/apply.py` enthält den entsprechenden Quellport.
`KALILOCK_ROM_SOURCE=/pfad/zu/lineage ./setup.sh` wendet ihn auf den separaten
ROM-Baum an. Der Kernel-Build allein baut **keine NFC-APEX**. Anschließend das
vollständige ROM/NFC-Modul mit den passenden Schlüsseln neu bauen und prüfen.
Ein solcher ROM-Neubau ist mit diesem Commit nicht durchgeführt oder ersetzt.

## Alte lokale Diagnosen

`tools/bt-hastings/` enthält außerdem ältere Testquellen und Crash-/Gerätelogs.
Für das normale laufende Android-Bluetooth werden die experimentellen Tests nicht
benötigt. Sie schalten Bluetooth um und testen UART-Zugriff; deshalb werden sie
nicht automatisch ausgeführt. Die alten Logs, Sicherungskopien und Dumps bleiben
lokal und gehören nicht zum Installations-/Build-Stand.
