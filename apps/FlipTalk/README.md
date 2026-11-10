# FlipTalk — Android-App (Alpha-Quellprojekt)

Eine einzelne Android-App für TikTok-Live-Events und direkte BLE-Ausgabe an die vorhandene Flipper-Zero-App **FlipTok Live**.

## Build
Android Studio: Projekt öffnen, Gradle synchronisieren, APK bauen. Benötigt Android SDK 35, Java 17 und Internet für Gradle/Chaquopy-Pakete. `app/build.gradle` nutzt Chaquopy für die eingebettete Python-Bibliothek TikTokLive. **Ein APK wurde hier nicht gebaut oder auf dem S20 FE getestet.**

## Nutzung
1. Auf dem Flipper **FlipTok Live** öffnen (Anzeige `Waiting for connection`).
2. FlipTalk auf Android öffnen und Bluetooth-Berechtigungen erlauben.
3. **FlipTok-Geräte suchen** tippen, Gerät `FlipTok XXXX` wird automatisch verbunden.
4. TikTok-Namen eingeben und **Livestream starten** tippen.

## Grenzen der Alpha
- BLE-Protokoll und UUIDs aus https://github.com/kamylwnb/FlipTok_Live_Chat ; Paketlänge 83 Bytes.
- TikTokLive-Version ist festgelegt, aber die Android/Chaquopy-Kompatibilität und Event-API sind noch **nicht** durch einen Build validiert. Fehler beim Build oder Live-Login sind möglich.
- Die Android-BLE-Übertragung von 83 Bytes setzt eine ausreichende MTU voraus. Es fehlt ein robuster Retry bei MTU-/GATT-Problemen.
- Kein automatischer Katalog sämtlicher aktuell live sendender TikTok-Nutzer. Letzter eingegebener Nutzer wird gespeichert.
- Aktuell keine Foreground-Service-Unterstützung: Android kann die App im Hintergrund anhalten.
- Kein Release-APK, keine Signierung, keine Hardwaretests.
