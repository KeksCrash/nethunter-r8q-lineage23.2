# ZeroFIDO NFC transport fix

Die mitgelieferte `zerofido.fap` wurde auf Flipper BND installiert und durch
Rücklesen geprüft. Zusammen mit KaliLock 2.8.8 funktionierte die erste
Android-Entsperrung nach einem Neustart am 07.11.2026.

ISO-DEP sammelt vollständige APDU-Pakete in einem eigenen Puffer. Die
CTAP-Kommandoverkettung sammelt deren Nutzdaten separat. Der bisherige gemeinsame
Zustand führte beim zweiten Teil der 267-Byte-hmac-secret-Anfrage zu ISO7816_6700.

Vollständige Ausgangsquelle mit GPL-3.0-Lizenz
[Momentum-Firmware0, Commit ef4f82613a92ed1212d422b08a6df7f33e46bd9d](https://github.com/KeksCrash/Momentum-Firmware0/tree/ef4f82613a92ed1212d422b08a6df7f33e46bd9d/applications/external/zerofido).
Der zugehörige Quellpatch ist `zerofido-nfc-chaining.patch`.

Im passenden Momentum-Firmware-Checkout bauen

```sh
git apply /pfad/zu/zerofido-nfc-chaining.patch
FBT_NO_SYNC=1 ./fbt fap_zerofido
```

Die Ausgabe liegt unter `build/f7-firmware-C/.extapps/zerofido.fap`.
Die getestete FAP nutzt Firmware-API 87.1. Vor einem Austausch die bisherige FAP
sichern und ZeroFIDO beenden. Ziel auf dem getesteten Flipper ist
`/ext/apps/BSU/zerofido.fap`.

Die drei korrigierten Quelldateien sind auch in
`~/1Apictures/zerofido_sync/applications/external/zerofido/src/transport/`
übernommen. `~/1Apictures/update2.sh` ruft in seinen Firmware-Abläufen bereits
`run_from_zerofido_sync` auf und übernimmt dadurch diese Version beim nächsten
Build. Die Sync-Dateien wurden mit dem getesteten Build abgeglichen,
`bash -n update2.sh` besteht.
