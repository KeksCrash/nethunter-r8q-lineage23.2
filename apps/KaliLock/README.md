# Kali Lock 2.8.8

Neonrote Oberfläche auf schwarzem Hintergrund. Reduzierte Oberfläche mit Entsperren, Schlüssel und Einstellungen. Paket-ID bleibt
`de.nethunter.flipperunified`; vorhandene Registrierung und verschlüsseltes Passwort
bleiben bei einem signaturgleichen Update erhalten. Kein NFC-UID-Unlock.

## Erste Entsperrung nach einem Neustart

`NFC einrichten & prüfen` bietet einen durchgehenden Ablauf.
Das vorhandene Android-Passwort und die bekannte NFC-Registrierung werden verwendet;
es wird dabei kein neuer Flipper-Eintrag erstellt. Passwortbindung, unabhängiger Test
und begrenzte Wiederholungen erfolgen automatisch. Zwischen NFC-Kontakten fordert
die App zum Entfernen und erneuten Anhalten auf und wartet über NfcAdapter.ignore
auf die Entfernung. Fehler bewirken keine automatische Aktivierung/Neustartfreigabe.

Der bekannte Schlüssel muss hmac-secret unterstützen.

Am 07.11.2026 wurde die erste Entsperrung nach einem echten Neustart auf dem
SM-G781B mit Android 16 erfolgreich geprüft. KaliLock protokollierte nach dem
NFC-Scan FIDO-Prüfung, Passwortentschlüsselung, Eingabe und Enter. Android wechselte
um 05:34:30 CET von RUNNING_LOCKED zu RUNNING_UNLOCKED. Der Nutzer bestätigte den
Erfolg. Version 2.8.8 fragt die zur Laufzeit ausgewählte Tastatur ab, die vor der
ersten Entsperrung vom gespeicherten Standard abweichen kann.

Die benötigte ZeroFIDO-App und ihr Quellpatch liegen unter
[flipper/](flipper/README.md).

Aktueller Implementierungs-/Teststand und Einschränkungen:
[DIRECT-BOOT-STATUS.md](DIRECT-BOOT-STATUS.md).

Host-Prüfungen nach dem SDK-Build: `bash apps/KaliLock/tests/run.sh`.
Sie prüfen Protokoll-1-Vektoren, Signatur-/Envelope-Manipulation, CBOR-Parser und
Transaktionsablauf; sie simulieren kein vollständiges Android-Gerät.

## Bauen und installieren

```sh
bash apps/KaliLock/build.sh install
bash apps/KaliLock/install.sh ADB-SERIENNUMMER
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

Die JS-Dateien enthalten die getesteten Hooks. Der Boot-Hook aktualisiert zudem
die native Polling-Freigabe nach Registrierung eines Unlock-Handlers.
Die Vorlage unter `nfc-patches/boot-module/` startet den Injector nach dem Booten
und nach einem Neustart des NFC-Prozesses. Sie benötigt zusätzlich einen passenden
`frida-inject` und eine gebündelte `hooks.js` mit Frida-Java-Bridge sowie beiden
JS-Quelldateien. Diese Laufzeitdateien sind in der Modulvorlage separat
bereitzustellen.
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
mitgeliefert. Keine APEX-Binärpatches. Historischer Stand 2.6 ohne Frida-Autostart; der aktuelle
lokal freigegebene Boot-Modul-Stand steht in DIRECT-BOOT-STATUS.md.

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

Fertige neue APK: `install/KaliLock-2.8.8.apk`. Originale Debug-APK: `debug-v2.5/KaliLock-2.5-debug.apk`. Installationshelfer: `bash apps/KaliLock/install.sh ADB-SERIENNUMMER`.

Der abgeglichene Handy-Iststand einschließlich Bluetooth-Kerneloptionen und gemeinsamem Live-Starter ist in [RUNNING-STATE.md](RUNNING-STATE.md) dokumentiert.
