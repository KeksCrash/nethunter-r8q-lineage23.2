#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")"
PKG=de.nethunter.smartunlock
APK=app/build/outputs/apk/debug/app-debug.apk
ORIGINAL="$HOME/nethunter-r8q-lineage23.2/apps/SmartUnlock/original-v1.3.apk"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
AAPT="$(command -v aapt || true)"
SIGNER="$(command -v apksigner || true)"
if [[ -z "$AAPT" ]]; then AAPT="$SDK/build-tools/35.0.0/aapt"; fi
if [[ -z "$SIGNER" ]]; then SIGNER="$SDK/build-tools/35.0.0/apksigner"; fi
[[ -x "$AAPT" && -x "$SIGNER" ]] || { echo 'FEHLER: aapt/apksigner fehlen'; exit 1; }
[[ -f "$HOME/.android/debug.keystore" ]] || { echo 'FEHLER: Debug-Keystore nicht gefunden'; exit 1; }
echo '=== BUILD ==='
gradle --no-daemon :app:assembleDebug --max-workers="$(nproc)"
[[ -s "$APK" ]] || { echo 'FEHLER: APK nicht erzeugt'; exit 1; }
echo '=== VERSION UND PROVIDER IN APK ==='
"$AAPT" dump badging "$APK" | head -4
"$AAPT" dump xmltree "$APK" AndroidManifest.xml > /tmp/r8q-smartunlock-manifest-check.txt
for P in Widget LargeWidget IconWidget; do
  grep -Fq "de.nethunter.smartunlock.$P" /tmp/r8q-smartunlock-manifest-check.txt || { echo "FEHLER: $P fehlt im Manifest"; exit 1; }
done
[[ $(grep -Fc '"android.appwidget.provider"' /tmp/r8q-smartunlock-manifest-check.txt) -eq 3 ]] || { echo 'FEHLER: Nicht genau 3 AppWidget-Metadaten'; exit 1; }
for P in widget_small widget_large widget_icon; do
  "$AAPT" dump xmltree "$APK" "res/xml/$P.xml" | grep -q 'appwidget-provider' || { echo "FEHLER: $P XML ungültig"; exit 1; }
done
EXPECTED=6ed2e4621e4787a17095a27e28f09f62551a81e3a5bd475b11509babe022c82e
GOT=$("$SIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d '\r' | head -1)
[[ "$GOT" == "$EXPECTED" ]] || { echo "FEHLER: Signatur stimmt nicht überein: $GOT"; exit 1; }
echo '=== GERAET ==='
adb wait-for-device
[[ "$(adb shell getprop ro.product.device | tr -d '\r')" == 'r8q' ]] || { echo 'FEHLER: Nicht R8Q'; exit 1; }
CURRENT=$(adb shell pm list packages --user 0 | tr -d '\r')
grep -Fxq "package:$PKG" <<< "$CURRENT" || { echo 'FEHLER: Original-App nicht für User 0 installiert; Abbruch statt Datenverlust'; exit 1; }
echo '=== AKTUELLE APP-DATEN SICHERN (ADB ROOT ERFORDERLICH) ==='
adb root >/dev/null
adb wait-for-device
DATA=$(adb shell dumpsys package "$PKG" | sed -n 's/^[[:space:]]*dataDir=//p' | head -1 | tr -d '\r')
case "$DATA" in /data/user/0/de.nethunter.smartunlock|/mnt/expand/*/user/0/de.nethunter.smartunlock) ;; *) echo 'FEHLER: unerwarteter Datenpfad'; exit 1;; esac
BACKUP="$HOME/smartunlock-v14-before-update-$(date +%Y%m%d-%H%M%S).tar"
adb exec-out tar -cf - -C "${DATA%/*}" "${DATA##*/}" > "$BACKUP"
[[ -s "$BACKUP" ]] && tar -tf "$BACKUP" >/dev/null || { echo 'FEHLER: Backup ungültig'; exit 1; }
echo "Backup: $BACKUP"
echo '=== UPDATE (OHNE DEINSTALLATION) ==='
adb install -r --user 0 "$APK"
echo '=== APPWIDGET-SYSTEMSTATUS ==='
PROVIDERS=$(adb shell dumpsys appwidget)
FOUND=0
for P in Widget LargeWidget IconWidget; do
  if grep -Fq "cmp:ComponentInfo{$PKG/$PKG.$P}" <<< "$PROVIDERS"; then echo "OK: $P"; FOUND=$((FOUND+1)); else echo "FEHLT: $P"; fi
done
if [[ "$FOUND" -ne 3 ]]; then
  echo 'WARNUNG: Provider nach Update unvollständig. App-Daten nicht löschen.'
  if [[ -f "$ORIGINAL" ]]; then
    echo '=== ROLLBACK ORIGINAL v1.3 ==='
    adb install -r -d --user 0 "$ORIGINAL" || echo 'ROLLBACK FEHLGESCHLAGEN: Originalversion manuell installieren'
  fi
  exit 2
fi
echo '=== FERTIG: 3/3 WIDGET PROVIDER REGISTRIERT ==='
echo 'Falls Widgets vom Launcher entfernt wurden, bitte neu zum Homescreen hinzufügen.'
