'use strict';
// KaliLock v4: single Java hook, no NativeNfcManager hooks.
// For OFF_LOCKED (2), execute the existing system NFC screen transition as ON_LOCKED (4).
// WARNING: this makes NFC services see an ON_LOCKED state while physical display is off.
Java.perform(function () {
  try {
    const NfcService = Java.use('com.android.nfc.NfcService');
    const original = NfcService.applyScreenState.overload('int');
    let changed = 0;
    original.implementation = function (state) {
      if (state === 2) {
        changed++;
        if (changed <= 12 || changed % 25 === 0) {
          console.log('[KALILOCK] V4_OFF_LOCKED_TO_ON_LOCKED: 2 -> 4; call #' + changed);
        }
        return original.call(this, 4);
      }
      return original.call(this, state);
    };
    console.log('[KALILOCK] V4_HOOK_ACTIVE: NfcService.applyScreenState(int); NO_NATIVE_HOOK');
  } catch (e) {
    console.log('[KALILOCK] V4_HOOK_FAILED: ' + (e.stack || e));
  }
});
