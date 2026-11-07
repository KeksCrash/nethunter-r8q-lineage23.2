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
    // Registration can happen after the controller's initial screen-state update.
    // addNfcUnlockHandler refreshes routing but leaves the native polling bit stale.
    function refreshPolling() {
      Java.scheduleOnMainThread(function () {
        try {
          const service = NfcService.getInstance();
          const manager = Java.cast(service.mNfcUnlockManager.value,
            Java.use('com.android.nfc.NfcUnlockManager'));
          if (!manager.isLockscreenPollingEnabled()) return;
          const state = service.mScreenState.value;
          if (state !== 2 && state !== 4) return;
          const handler = Java.cast(service.mHandler.value, Java.use('android.os.Handler'));
          // MSG_APPLY_SCREEN_STATE = 16 on this device's Android NFC service.
          const message = Java.use('android.os.Message').obtain(handler, 16,
            Java.use('java.lang.Integer').valueOf(4));
          handler.sendMessage(message);
          console.log('[KALILOCK] BOOT_LOCKED_POLLING_REFRESH');
        } catch (e) {
          console.log('[KALILOCK] BOOT_POLLING_REFRESH_FAILED: ' + e);
        }
      });
    }
    const adapterService = Java.use('com.android.nfc.NfcService$NfcAdapterService');
    const add = adapterService.addNfcUnlockHandler.overload(
      'android.nfc.INfcUnlockHandler', '[I');
    add.implementation = function (handler, technologies) {
      const result = add.call(this, handler, technologies);
      refreshPolling();
      return result;
    };
    refreshPolling();
  } catch (e) {
    console.log('[KALILOCK] V4_HOOK_FAILED: ' + (e.stack || e));
  }
});
