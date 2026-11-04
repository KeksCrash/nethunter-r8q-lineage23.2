# Kali Lock 2.6

Neonrote Oberfläche auf schwarzem Hintergrund. Reduzierte Oberfläche mit Entsperren, Schlüssel und Einstellungen. Paket-ID bleibt
`de.nethunter.flipperunified`; vorhandene Registrierung und verschlüsseltes Passwort
bleiben bei einem signaturgleichen Update erhalten. Kein NFC-UID-Unlock.

## Bauen und installieren

```sh
bash apps/KaliLock/build.sh install
adb install -r apps/KaliLock/build/install/KaliLock.apk
```

`setup.sh` baut die App neben IMSI Catcher automatisch, sobald das Android SDK
verfügbar ist. Kein automatisches Flashen oder Installieren auf dem Gerät.
Build benötigt JDK 17, Android SDK Plattform 35 und Build-Tools ab 35, zip.
Gradle-Projekt ist für Android Studio ebenfalls enthalten.

Der lokale SDK-Build signiert standardmäßig mit `~/.android/debug.keystore`, damit
Updates zur bisherigen Debug-App passen. Dies ist eine Installations-APK, keine
mit einem privaten Produktionsschlüssel signierte Store-Veröffentlichung.
Eigener Schlüssel: `KALILOCK_KEYSTORE`, `KALILOCK_KEY_ALIAS`,
`KALILOCK_STORE_PASS`, `KALILOCK_KEY_PASS`. Keine Schlüssel ins Repo aufnehmen.
Bei anderer Signatur nicht deinstallieren: Datenverlust! Passenden Schlüssel nutzen.

## Bisherige Debug-Version

`debug-v2.5/` enthält die unveränderte bisherige Oberfläche, Quellen und die
ursprüngliche APK. `bash build.sh debug` baut sie erneut. Beide Varianten verwenden
absichtlich dieselbe Paket-ID und werden als Update/Regressionstest eingesetzt;
sie sind nicht parallel installierbar. Die Original-APK wird nicht neu signiert.

## NFC bei ausgeschaltetem Display

Zwei zusammengehörige Patches waren im Hardwaretest nötig:

1. `screenoff-v4.js`: NfcService.applyScreenState behandelt OFF_LOCKED als
   ON_LOCKED, damit der vorhandene NFC-Unlock-Pollingpfad aktiv bleibt.
2. `dispatch-live.js`: Der Dispatcher prüft den physischen Displayzustand separat.
   Nur innerhalb dispatchTag wird OFF_LOCKED dem ON_LOCKED-Callback zugeordnet.
   FIDO-Signatur und Android-Sperrzustand werden nicht manipuliert.

Die JS-Dateien dokumentieren den getesteten Live-Stand, sie starten nicht von selbst.
Hardware: SM-G781B, Android 16/API36, Kali Lock 2.5; mehrmals screenInteractive=false,
gültige neue FIDO-Signatur, LOCK_UNLOCK_CONFIRMED und unabhängig deviceLocked=0,
Display ON bestätigt. `tryUnlock` liefert bei dieser App bewusst false, obwohl sie
anschließend über die FIDO-geschützte Passwort-Eingabe entsperrt.

Der native ROM-Port ist `nfc-patches/apply.py` und begrenzt die Service-Zuordnung
zusätzlich auf registrierte Unlock-Handler. Gegen den passenden NFC-Quellbaum prüfen:

```sh
python3 apps/KaliLock/nfc-patches/apply.py /pfad/zu/lineage --check --diff
python3 apps/KaliLock/nfc-patches/apply.py /pfad/zu/lineage
```

Danach das ROM einschließlich `com.android.nfcservices` mit den passenden
ROM-/APEX-Schlüsseln bauen. Vorhandene Originale werden gesichert; unbekannte
Quellvarianten werden abgelehnt. Der Quellpatch erweitert den Screen-off-Callback
auf vorhandene NFC-Unlock-Handler. Das kann NFC-Aktivität und Akkuverbrauch erhöhen;
es ist keine auf Kali Lock beschränkte Systemberechtigung.

Dieses Repository ist ein **Kernel-Repo**. Ein Kernel-ZIP baut/ersetzt keine
Android-NFC-APEX. `KALILOCK_ROM_SOURCE=/pfad/zu/lineage ./setup.sh` wendet die
Quellpatches vor dem Build an; der vollständige ROM-Build muss anschließend in
jenem LineageOS-Baum erfolgen. Ohne diese Variable werden die ROM-Patches nur
mitgeliefert. Keine APEX-Binärpatches und kein permanenter Frida-Autostart.

## Einrichtung

Schlüssel registrieren → Signatur testen → Android-Passwort speichern →
Passwort-Tastatur freigeben → Automatik einschalten → NFC-Dienst starten.
Der vorhandene Schlüssel kann umbenannt werden. Eine andere technische RP-ID gilt
nur für neue Registrierungen. Nach gültiger FIDO-Prüfung gilt 18 Sekunden Cooldown,
auch wenn das Gerät inzwischen manuell erneut gesperrt wurde. Zum Bildschirm-aus-
Test Flipper entfernen, Wartezeit abwarten, neu anhalten und Power-Taste unberührt lassen.

## Prüfung am 04.11.2026

Installations- und Legacy-Debug-Variante mit dem SDK-Skript gebaut und mit
apksigner verifiziert; Signaturzertifikat stimmt mit der ursprünglichen APK
überein. Gradle assembleDebug zusätzlich erfolgreich. APK 2.6 als signaturgleiches
Update auf SM-G781B installiert; bestehende Schlüssel-/Passwort-Konfiguration
blieb erhalten. Die neue Oberfläche wurde auf dem Gerät visuell geprüft.
ROM-Quellpatch: Anwendbarkeit und Idempotenz gegen AOSP android16-release sowie
gegen den aus der installierten NFC-APK extrahierten Code geprüft. Unbekannte
Varianten werden vor Schreibzugriff abgelehnt. Kein vollständiger LineageOS-/
NFC-APEX-Build und kein ROM-Flash in diesem Arbeitsschritt.

Originalquellen für den mitgelieferten Referenz-Diff:
- https://android.googlesource.com/platform/packages/modules/Nfc/+/refs/heads/android16-release/NfcNci/src/com/android/nfc/NfcService.java
- https://android.googlesource.com/platform/packages/modules/Nfc/+/refs/heads/android16-release/NfcNci/src/com/android/nfc/NfcDispatcher.java

Der Installer nutzt `adb install --no-incremental -r`, falls inkrementelle
Installation auf Adopted Storage mit MEDIA_UNAVAILABLE scheitert.

Fertige neue APK: `install/KaliLock-2.6.apk`. Originale Debug-APK: `debug-v2.5/KaliLock-2.5-debug.apk`. Installationshelfer: `bash apps/KaliLock/install.sh ADB-SERIENNUMMER`.
