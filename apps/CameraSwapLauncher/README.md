# KARI Kamera-Swap

Der KARI-Starter öffnet die installierte DroidRooter-App DRVCAM
(`com.droidrooter.virtualcamera`) über ihren Android-Launcher-Einstieg.
Beim Fehlen der Kamera-App zeigt er einen Hinweis. DRVCAM wird separat vom
Hersteller bezogen. Der Starter enthält weder Kamera-Hooks noch den DRVCAM-APK.

- [Offizielle DRVCAM-Veröffentlichungen](https://github.com/Xposed-Modules-Repo/com.droidrooter.virtualcamera/releases)

Mit Zsh, JDK 17, Android SDK Platform 36 und Build Tools 36.0.0 bauen und installieren:

```zsh
zsh build.zsh
adb install --no-incremental -r Kari-Kamera-Swap.apk
```

Den beim ersten Build angelegten Schlüssel `kari-camera-local.p12` für Updates
lokal aufbewahren.
