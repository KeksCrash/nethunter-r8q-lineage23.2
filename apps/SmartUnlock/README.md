# Smart Unlock Neon 1.4.1 – Widget-Fix

Dieses Projekt repariert die frühere v1.4-Projektbasis, ausgehend von den Originalressourcen der vom Nutzer bereitgestellten v1.3-APK. Die drei ursprünglichen Widget-Provider `Widget`, `LargeWidget` und `IconWidget` und ihre originalen `res/xml`-Dateien bleiben erhalten. VersionCode 6.

## Installieren auf CachyOS

Projekt nach `~/nethunter-r8q-lineage23.2/apps/SmartUnlock` kopieren, **die dortige `original-v1.3.apk` behalten**, dann `./build-check-install.sh` starten. Baut mit `--max-workers=$(nproc)`, prüft Signatur und Provider-Metadaten, sichert App-Daten, aktualisiert Benutzer 0 und kontrolliert den AppWidgetManager. Wenn Provider fehlen, wird versucht auf die Original-v1.3 zurückzugehen. Keine Deinstallation, kein Daten-Reset.

**Keine APK ist hier bereits gebaut oder auf dem Gerät getestet worden.** Die Bereitstellung des Quellprojekts ersetzt keine Installation. Für den Build werden Android SDK 35 und Gradle/Android Gradle Plugin benötigt.

## Trust-Monitor

Die bestehende v1.4-Monitorfunktion läuft nur nach Benutzerentsperrung und bei aktivem Smart-Unlock-Profil, prüft regelmäßig den Trust-Zustand und aktualisiert Widgets. Kein automatischer Entsperr- oder erzwungener Trust-Neustart. Android kann die PIN trotz erneuter Trust-Gewährung verlangen.

**Keystore niemals committen.** `~/.android/debug.keystore` bleibt lokal; Build-Skript prüft den Fingerabdruck vor Installation.

## Android-Installation

Smart Unlock Neon v1.4.1 muss auf dem internen Gerätespeicher installiert sein. Bei Installation auf Adoptable Storage (/mnt/expand) werden die Widget-Provider möglicherweise nicht registriert.

Installation: `adb install -r -f --user 0 app/build/outputs/apk/debug/app-debug.apk`

Interner Speicher: `adb shell pm move-package de.nethunter.smartunlock internal`

Prüfung: `adb shell dumpsys appwidget | grep de.nethunter.smartunlock`

Erwartet: Widget, LargeWidget, IconWidget.
