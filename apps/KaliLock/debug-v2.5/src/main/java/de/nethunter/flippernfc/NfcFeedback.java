package de.nethunter.flippernfc;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** User-configurable haptic feedback for NFC discovery; never an unlock authorization. */
final class NfcFeedback {
 private static final String PREF="nfc_feedback_v22";
 private static final String KEY="vibration_enabled";
 private NfcFeedback() {}
 static boolean enabled(Context context) {
  return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(KEY,true);
 }
 static void setEnabled(Context context,boolean enabled) {
  context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(KEY,enabled).apply();
 }
 static void vibrateOnDetection(Context context) {
  if(!enabled(context))return;
  Vibrator vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
  if(vibrator==null||!vibrator.hasVibrator())return;
  try {
   if(Build.VERSION.SDK_INT>=26) vibrator.vibrate(VibrationEffect.createOneShot(80,VibrationEffect.DEFAULT_AMPLITUDE));
   else vibrator.vibrate(80);
  }catch(RuntimeException ex){NfcWatchService.log(context,"NFC_VIBRATION_FAILED "+ex.getClass().getSimpleName());}
 }
}
