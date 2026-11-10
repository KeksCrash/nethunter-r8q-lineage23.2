# KameraSchalter DE 0.2

Kostenloses, deutschsprachiges Android-Steuerprogramm ohne Login.

**WICHTIG:** Dies ist kein eigenständiges Kameraspoofing-Modul. Es verwendet das bereits installierte CamSwap-LSPosed-Modul und ändert dessen dokumentierten Konfigurationsschlüssel `use_original_camera` in `/sdcard/DCIM/Camera1/cs_config.json`. Der Hook muss aktiv sein. Die Änderung wird durch erneutes Lesen überprüft, jedoch nicht die Live-Übernahme in TikTok. Während eines laufenden Co-Host-LIVE ist der Wechsel **nicht garantiert**. Die App verändert TikTok nicht und startet es nicht neu.

## Funktionen
- Deutsche Oberfläche
- Echte/virtuelle Kamera auswählen
- Zwei schwebende Schalter über TikTok
- Root-Schreibzugriff auf CamSwap-Konfiguration
- Ohne Login, ohne Abo, ohne Tracking

## Build auf CachyOS
Benötigt: JDK 17, Android SDK mit Plattform 35 und Gradle 8.9 oder Android Studio. Build-Befehl im Projektverzeichnis: `gradle assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Installieren mit `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Technische Einschränkungen
- Prozessübergreifende Hooks verbleiben im bestehenden CamSwap-Modul; App ist nur Steueroberfläche.
- Der Code basiert auf dem veröffentlichten Konfigurationsformat. Ob CamSwap einen bereits laufenden Video-Stream bei einer externen Dateiänderung aktualisiert, ist **ungeprüft**.
- `su` und die Schreibrechte für den Konfigurationspfad müssen vorhanden sein.
- Es werden keine TikTok-App-Daten gelöscht.

Version 0.2 ergänzt das KARI-Launcher-Icon. Die Kamerasteuerung verwendet weiterhin die CamSwap-Konfiguration.
