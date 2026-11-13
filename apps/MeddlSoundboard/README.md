# Meddl-Soundboard

Android-App für das S20 FE mit lokaler Medienwiedergabe, schwebenden Tasten und
Mikrofon-Mix. Die KARI-Launcher-Ressource ist seit Version 1.5 enthalten.

## Build und Installation

Voraussetzungen sind Zsh, JDK 17, Android SDK Platform 36 und Build Tools 36.0.0.
Die sechs eingebauten Clips liegen lokal als Mono-PCM mit 48 kHz und 16 Bit Little
Endian unter `assets/`. Kopiere das vorhandene `assets/` aus dem lokalen
Soundboard-Projekt. `build.zsh` nennt fehlende Dateien. Die Clip-Dateien,
der Signaturschlüssel und generierte APKs bleiben lokal.

Für ein Update einer bereits installierten App die ursprüngliche
`meddl-local.p12` vor dem Build in dieses Verzeichnis kopieren. Beim Erstbuild
legt das Skript einen neuen lokalen Schlüssel an.

```zsh
zsh build.zsh
ANDROID_SERIAL=<ADB-Gerät> zsh install.zsh
```

`install.zsh` installiert die APK und startet den lokalen Audiobrückendienst
auf einem per ADB verbundenen r8q. Nach einem Telefon-Neustart startet dieser
Befehl den bereits installierten Dienst erneut:

```zsh
adb shell -T -n 'sh /data/local/tmp/meddl-soundboard/device-start.sh'
```

Bei aktivem Dienst kann das Soundboard ohne laufenden PC verwendet werden.
Die systemweite Mikrofonroute oder die Beschränkung auf TikTok und die
Optionen für Handy-Mikrofon und eigene Wiedergabe werden in der App gewählt.
Eigene Clips können per Android-Dateiauswahl oder unterstütztem Instant-Button-Link
importiert werden. `pc-audio.zsh` ist der optionale PC-Eingang für PipeWire.

Version 1.6 gruppiert Sounds in bearbeitbaren Kategorien. Langes Drücken
öffnet das Menü zum Verschieben oder Entfernen, auch für mitgelieferte
Sounds. Die App akzeptiert MP3 und erkennt identische PCM-Daten beim Import.
Optionale Zusatzclips werden aus dem lokalen `assets/` geladen, wenn sie
vorhanden sind. Details zu den lokalen Audioclips stehen im Arbeitsprojekt.
