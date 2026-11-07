# Direct Boot — implementation and device status

2026-11-04: Kali Lock **2.8.6**, versionCode **286**, is built and installed.
The implementation is present, but first unlock after reboot is **not yet
hardware-validated**. No phone reboot was performed during installation.

## Implemented

- Prefer reusing the saved normal credential, retaining its original RP and public
  key; this sends no makeCredential request. Its hmac-secret support must be tested.
  Alternatively, explicitly create one separate nonresident ES256 credential under
  RP `boot.kalilock.local`. Existing normal credential and CE password are retained.
- CTAP2 protocol-1 hmac-secret with fresh ECDH and challenge, stable random salt,
  signed extension validation, explicit non-UV assertion policy and user presence.
  The observed Flipper advertises hmac-secret, protocols 1/2 and
  makeCredUvNotRqd. Authenticators requiring UV on every operation are rejected.
- Domain-separated HKDF and AES-256-GCM wrapping of the password, binding version,
  owner Android user 0, non-UV policy, RP, credential ID/public key and salt.
  No hmac output, derived decryption key or plaintext password is persisted.
- The DE record contains public credential metadata, counter, salt, nonce,
  authenticated ciphertext and enablement state. No copy of the old Keystore
  key or locally decryptable CE password ciphertext is placed there.
- A durable public registration journal is saved immediately after creation, before
  any derivation. A separate NFC contact binds the password. Lost creation responses
  block automatic re-creation; retries reuse recorded credentials.
- Provisioning leaves the wrapped record disabled. A separate scan must reproduce the
  secret, authenticate/decrypt the envelope and compare with the existing CE
  password before activation. It pre-enables the Direct-Boot-aware IME.
- Boot receiver, foreground NFC service and password IME are Direct-Boot-aware.
  The locked path avoids CE preferences. While enabled, this same hmac-backed
  path handles later locks too; failures never fall back to local-only decryption.
- NFC transactions expire/cancel, have single-use password authorization and are
  revoked on disable/clear/stop. Short state locks protect activation/revocation;
  NFC and root operations do not run under those locks.
- IME delivery is restricted to a locked system credential editor with this IME
  selected. Delayed submission checks validity. Teardown cancels callbacks.
  Previous IME ID is recoverable from DE; a manual keyboard is available if
  automatic recovery fails.

## Verified locally/on device

- APK built and signature verification passed.
- 32 host crypto/CBOR checks and 2,000 randomized malformed-input parser cases.
- 33 host tests of the actual enrollment controller using simulated Android storage
  and authenticator transport: partial creation, retry, lost response, persisted
  recovery, password rebinding and normal-credential reuse without a new key.
- 12 transaction deadline/cancellation/single-use host checks.
- Independent read-only source review found no remaining critical issue in its
  final scan. This is not a security certification or a hardware reboot test.
- App moved to internal storage: `/data/app/`, data `/data/user/0/`.
  Android's force-allow-on-external setting remains unchanged. The installer now
  requests internal installation and checks/moves the result if necessary.
- UID remains 10225; its Magisk root grant works. WRITE_SECURE_SETTINGS remains
  granted. The foreground NFC service was restarted without opening another app.
- The existing screen-off hooks remain in NFC PID 2530 in the current session.
  Approved module `/data/adb/modules/kalilock-nfc-boot` restores them on future
  boots/NFC process restarts. That module has not been reboot-tested.

## Pending physical steps

In Kali Lock → Einstellungen:

1. Prefer `Vorhandenen NFC-Schlüssel für Boot verwenden · kein neuer Eintrag`.
   This reuses known metadata and sends no credential creation command.
   Alternatively, explicitly choose a new boot credential. Existing unknown
   creations from 2.8 must be reviewed rather than retried blindly.
2. Select `Vorhandenen Boot-Schlüssel mit Passwort verbinden`, then scan.
   Remove the Flipper after the step. Failure retains the known credential.
3. Select `Vorhandenen Boot-Schlüssel testen / aktivieren`, then scan again.
   Confirm event `BOOT_SECRET_ROUNDTRIP_CONFIRMED`.
4. Check an ordinary lock/unlock using the newly enabled path first; then arrange
   an actual reboot test before any manual password entry. Verify boot-time root,
   hooks, NFC, IME and actual `UserManager.isUserUnlocked()` transition.

Until step 4 succeeds, do not claim first-unlock support is proven. A physical
authenticator is needed for steps 1–4; these cannot be completed from ADB alone.

The password remains the Android credential: NFC supplies it to the normal
keyguard after cryptographic authorization. This does not turn a FIDO signature
into an Android Synthetic Password credential, and cannot protect against
already running root malware intercepting a legitimate unlock.

Disabling the Magisk module prevents later injections. Already eternalized hooks
remain until the NFC process exits or the phone reboots. No secrets are in this
repository.

## 2.8 failure diagnosis and 2.8.2 correction

Device exit-info recorded three ANRs on 2026-11-04 around 15:30–15:32.
The captured main-thread stack blocks in NfcAdapter.enableReaderMode →
INfcAdapter.setReaderMode. The enrollment event log separately confirms three
successful makeCredential operations followed by Transceive failed before
the credential was persisted; subsequent tests reported BOOT_NOT_PROVISIONED.

2.8.2 returns immediately from the reader callback, moves NFC I/O and registration
off main, bounds individual APDUs to 3 seconds and asynchronously closes cancelled
tag sessions. It avoids overlapping actions and restarts the wanted watcher after
completion/cancellation. Status/errors survive redraw.

A legacy interrupted-creation marker stops further blind creation. It cannot
reconstruct credential IDs lost by 2.8, and does not delete unknown Flipper entries.
Password updates retain public key metadata and only invalidate/recreate the
encrypted binding. The password dialog confirms repeated input and explicitly
distinguishes storing an existing Android password from changing it in Android
settings, and from the authenticator-wide Flipper PIN.

2.8.2 was installed internally and launched successfully (80-ms reported startup).
No new physical NFC transaction or first-unlock-after-reboot success is claimed.

## 2.8.3 one-action workflow

The explicit `NFC einrichten & prüfen` action resumes the known credential,
wraps the saved password, then automatically waits for tag removal and arms the
independent verification step. Transport retries are bounded to three failures;
cancellation or any incomplete/failed cryptographic check cannot report success.
No new credential creation command is issued by this workflow. NFC presence-check
interval is 1000 ms during app reader mode. ADB can observe the fresh
AUTO_SETUP_VERIFIED event; the app itself does not reboot the phone.

16 additional host sequencing/retry/cancellation/false-success tests pass.
The real phone workflow was started by ADB UI automation; its result is pending
until a physical authenticator completes the exchanges.

## 2.8.4 NFC timing correction

Live events show BOOT_KEY_AGREEMENT_OK followed by BOOT_HMAC_ASSERTION_BEGIN,
then TagLostException after approximately 3.4 seconds on repeated attempts.
The previous 3000-ms explicit timeout was below the native reported negotiated
4944-ms (x2) frame wait and is a plausible cause, not proof of physical removal.
The timeout is now at least 15000 ms, preserving larger negotiated values up to
30000 ms. Cancellation/transaction checks remain unchanged, and timed-out unlock
callbacks explicitly close their tag sessions. Errors record elapsed and configured
timeouts without APDU contents or secrets.

98 targeted host checks plus 2000 malformed-input samples pass. Version 2.8.4 was
installed internally and the combined test restarted. Physical HMAC completion
and first unlock after reboot remain unproven until their events are observed.

## 2.8.5 APDU framing experiment

The 15-second timeout correction did NOT resolve the problem: live calls still
timed out at 15004–15008 ms after successful key agreement and before any HMAC
response. Timeout-only repair is therefore not claimed successful.

A request fixture matching the observed 32-byte credential ID produces a 267-byte
CTAP payload. ZeroFIDO source explicitly accepts ISO-7816 CLA-bit command chaining.
2.8.5 sends short APDUs with at most 180 body bytes (186 total), reassembling the
exact unchanged CTAP body at the authenticator. Intermediate nonempty responses
are rejected; cryptographic checks and no-new-credential workflow are unchanged.
1571 host APDU boundary/reassembly checks pass, in addition to the previous 98
checks and 2000 malformed-input samples.

2.8.5 installed internally. At attempted test restart, secure keyguard was showing.
No secure unlock was bypassed, no successful hardware HMAC result was observed,
and no reboot was executed.

## Latest physical test and 2.8.6 framing adjustment

Normal NFC unlock was confirmed again (LOCK_UNLOCK_CONFIRMED at epoch
1793807807360). This was the legacy unlock path, not proof of boot enrollment.
A subsequent 2.8.5 setup attempt reached the two-part HMAC request but returned
ISO7816_6700 (wrong length), so no boot envelope was stored.

2.8.6 preserves the original successful extended-Lc/extended-Le format within
each small APDU, with CLA chaining for larger messages. Each frame is <=189 bytes.
A failed exchange reports its frame index and length, without payloads.
1946 APDU boundary/reassembly host checks pass, plus 98 prior checks and 2000
malformed-input samples. Installed internally; combined physical test restarted.
No reboot or successful first-unlock claim until the required evidence is observed.

## 2026-11-07 boot scan and 2.8.8

The corrected ZeroFIDO FAP was installed on USB Flipper BND and verified by
reading it back. Its ISO-DEP receive-chain state/buffer is now independent of
the CTAP APDU command-chain state/buffer. The 267-byte HMAC request completes.

A physical scan while user 0 was still RUNNING_LOCKED recorded
BOOT_FIDO_AND_DECRYPTION_CONFIRMED and BOOT_PASSWORD_SENT=true. Actual unlock
failed because LockPasswordIme rejected the SystemUI password editor. The old
rejection event did not identify which trust condition failed.

The native NFC log also confirmed stale startup polling state: Java had a
registered handler and polling mask 7, but the controller retained screen mask
4. Reapplying MSG_APPLY_SCREEN_STATE updated the controller to mask 20. The
user explicitly approved persisting this refresh in the existing root module.
The previous hooks.js is backed up there as hooks.js.before-polling-fix.
The source hook now refreshes at injection and after unlock-handler registration.
apply.py includes the corresponding source change and passes an idempotence check.

KaliLock 2.8.8 queries InputMethodManager.getCurrentInputMethodInfo on API 34+
when checking the selected IME, including restoration. This avoids using the
persisted secure setting as runtime authority during Direct Boot. Rejection
events now include locked/selected booleans for further hardware diagnosis.
All existing host checks pass. Installed versionName is
2.8.8-kalilock-directboot-ime. First unlock after a fresh reboot with this version was subsequently hardware-validated.

## Hardware success on 2026-11-07

KaliLock 2.8.8 and the approved persistent NFC hook passed the reboot test.
User 0 was observed RUNNING_LOCKED before the scan. The NFC transaction recorded
verified HMAC output, password decryption, PASSWORD_EDITOR_READY,
LOCK_PASSWORD_IME_COMMITTED and LOCK_SUBMIT_ENTER_ONCE. Android
LockSettingsService then unlocked user 0. UserController recorded
RUNNING_LOCKED -> RUNNING_UNLOCKING -> RUNNING_UNLOCKED at 05:34:30 CET.
The user confirmed the successful unlock. The ordinary keyboard was selected
afterwards.

The additional direct-handler variant proposed while this test was still running
was never installed. The successfully tested hook retains Java.scheduleOnMainThread.
Console output after the short-lived injector exits is insufficient evidence
that its scheduled callback failed.
