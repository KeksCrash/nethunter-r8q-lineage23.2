package de.nethunter.flippernfc;
import android.content.*;
import android.app.KeyguardManager;
import android.os.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Optional and experimental: locally stored Android password, cryptographically gated by CTAP2 assertion. */
final class PinAuto {
 private static final String PREF="pin_automation_v14",ALIAS="flipper_unified_pin_v14";
 static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("enabled",false);}
 static boolean configured(Context c){return c.getSharedPreferences(PREF,0).contains("ciphertext");}
 static void save(Context c,String pin)throws Exception{
  if(pin==null || pin.isEmpty() || pin.codePointCount(0,pin.length())>256)throw new IllegalArgumentException("Passwort muss 1–256 Zeichen lang sein");
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(ALIAS)){
   KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());kg.generateKey();}
  SecretKey key=(SecretKey)ks.getKey(ALIAS,null);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);byte[] ct=cipher.doFinal(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  c.getSharedPreferences(PREF,0).edit().putString("nonce",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("ciphertext",Base64.encodeToString(ct,Base64.NO_WRAP)).putBoolean("enabled",false).commit();
 }
 static void toggle(Context c,boolean on){if(on&&!configured(c))throw new IllegalStateException("Android-PIN noch nicht gespeichert");c.getSharedPreferences(PREF,0).edit().putBoolean("enabled",on).commit();}
 static void clear(Context c){c.getSharedPreferences(PREF,0).edit().clear().commit();try{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);k.deleteEntry(ALIAS);}catch(Exception ignored){}}
 private static String previousIme;
 private static final android.os.Handler restoreHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 private static final String IME="de.nethunter.flipperunified/de.nethunter.flippernfc.LockPasswordIme";
 private static String currentIme(Context c){return android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);}
 static synchronized void restoreIme(Context c){
  if(previousIme!=null){
   try{if(IME.equals(currentIme(c))){switchIme(previousIme);NfcWatchService.log(c,"IME_RESTORED");}}catch(Exception e){NfcWatchService.log(c,"IME_RESTORE_FAILED "+e.getClass().getSimpleName());}
   previousIme=null;
  }
 }
 private static void switchIme(String component){
  if(component==null || !component.matches("[A-Za-z0-9._$]+/[A-Za-z0-9._$]+"))throw new IllegalArgumentException("INVALID_IME_COMPONENT");
  try{java.lang.Process process=new ProcessBuilder("su","-c","ime set "+component).redirectErrorStream(true).start();
   if(process.waitFor()!=0)throw new IllegalStateException("IME_SET_FAILED");}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("IME_SWITCH_INTERRUPTED",e);}
  catch(java.io.IOException e){throw new IllegalStateException("IME_SWITCH_IO",e);}
 }
 private static synchronized void selectImeAfterFido(Context c){
  if(IME.equals(currentIme(c)))return;
  previousIme=currentIme(c);
  if(previousIme==null)throw new IllegalStateException("CURRENT_IME_UNKNOWN");
  switchIme(IME);
  NfcWatchService.log(c,"IME_SWITCH_REQUESTED_AFTER_FIDO");
  restoreHandler.postDelayed(()->restoreIme(c.getApplicationContext()),14000);
 }
 private static final android.os.Handler bouncerHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 // The NFC handler can run while Android is DOZING. Wake the real display and
 // wait for an interactive keyguard before opening its credential editor.
 private static void runRootInput(String command) throws Exception {
  java.lang.Process proc=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
  boolean done=proc.waitFor(7,java.util.concurrent.TimeUnit.SECONDS);
  if(!done){proc.destroyForcibly();throw new IllegalStateException("ROOT_INPUT_TIMEOUT");}
  if(proc.exitValue()!=0)throw new IllegalStateException("ROOT_INPUT_FAILED_"+proc.exitValue());
 }
 private static void wakeScreenAndWait(Context c) throws Exception {
  PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
  if(pm==null)throw new IllegalStateException("POWER_SERVICE_MISSING");
  NfcWatchService.log(c,"LOCK_WAKE_BEGIN interactive="+pm.isInteractive());
  if(!pm.isInteractive())runRootInput("input keyevent 224"); // KEYCODE_WAKEUP, never unlocks
  long end=SystemClock.elapsedRealtime()+4500;
  while(!pm.isInteractive() && SystemClock.elapsedRealtime()<end)Thread.sleep(150);
  if(!pm.isInteractive())throw new IllegalStateException("LOCK_WAKE_TIMEOUT_NOT_INTERACTIVE");
  NfcWatchService.log(c,"LOCK_WAKE_INTERACTIVE");
  // Give SystemUI enough time to replace the doze surface with the real lockscreen.
  Thread.sleep(650);
 }
 private static void showCredentialBouncer(Context c) throws Exception {
  PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
  if(pm==null||!pm.isInteractive())throw new IllegalStateException("LOCK_BOUNCER_DISPLAY_NOT_INTERACTIVE");
  android.graphics.Point size=new android.graphics.Point();
  android.view.WindowManager wm=(android.view.WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
  if(wm==null)throw new IllegalStateException("WINDOW_SERVICE_MISSING");
  wm.getDefaultDisplay().getRealSize(size);
  int x=size.x/2,from=(int)(size.y*.83f),to=(int)(size.y*.25f);
  runRootInput("input swipe "+x+" "+from+" "+x+" "+to+" 270");
  NfcWatchService.log(c,"LOCK_BOUNCER_SWIPE_REQUESTED (editor not yet confirmed)");
 }
 private static String pendingImePassword;
 private static long pendingImeUntil;
 static synchronized boolean hasPendingImePassword(){return pendingImePassword!=null && android.os.SystemClock.elapsedRealtime()<=pendingImeUntil;}
 static synchronized String takePendingImePassword(){
  if(pendingImePassword==null || android.os.SystemClock.elapsedRealtime()>pendingImeUntil){pendingImePassword=null;return null;}
  String value=pendingImePassword;pendingImePassword=null;return value;
 }
 static synchronized void cancelPendingImePassword(){pendingImePassword=null;}
 private static synchronized void queueImePassword(String value){pendingImePassword=value;pendingImeUntil=android.os.SystemClock.elapsedRealtime()+15000;}
 private static boolean imeSelected(Context c){return IME.equals(currentIme(c));}
 static boolean unlockAfterValidatedAssertion(Context c)throws Exception {
  if(!enabled(c)||!CtapFido.verified(c))return false;
  KeyguardManager km=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);if(km==null||!km.isDeviceLocked())return false;
  android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  SecretKey key=(SecretKey)ks.getKey(ALIAS,null);if(key==null)throw new IllegalStateException("Keystore-Schlüssel fehlt");
  Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(p.getString("nonce",""),Base64.NO_WRAP)));
  byte[] pinBytes=aes.doFinal(Base64.decode(p.getString("ciphertext",""),Base64.NO_WRAP));String pin=new String(pinBytes,java.nio.charset.StandardCharsets.UTF_8);java.util.Arrays.fill(pinBytes,(byte)0);
  // A verified CTAP2 assertion is mandatory. No password goes to a shell.
  // Keep CPU awake while the display transitions from doze to interactive.
  PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
  if(pm==null)throw new IllegalStateException("POWER_SERVICE_MISSING");
  PowerManager.WakeLock cpu=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"KaliLock:NfcUnlockTransition");
  cpu.acquire(12000);
  try {
   wakeScreenAndWait(c);
   KeyguardManager guard=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);
   if(guard==null||!guard.isDeviceLocked()){NfcWatchService.log(c,"LOCK_ALREADY_UNLOCKED_AFTER_WAKE");return false;}
   // Select the trusted IME while no password is queued, then show the OS bouncer.
   if(!imeSelected(c))selectImeAfterFido(c);
   queueImePassword(pin);
   NfcWatchService.log(c,"LOCK_PASSWORD_QUEUED_15S_AFTER_FIDO_AND_WAKE");
   try {showCredentialBouncer(c);} catch(Exception ex){cancelPendingImePassword();restoreIme(c);throw ex;}
   final Context app=c.getApplicationContext();
   // One retry only if the trusted system password editor has not appeared yet.
   bouncerHandler.postDelayed(()->{
    if(hasPendingImePassword()){
     KeyguardManager kg=(KeyguardManager)app.getSystemService(Context.KEYGUARD_SERVICE);
     if(kg!=null&&kg.isDeviceLocked())new Thread(()->{
      try{showCredentialBouncer(app);NfcWatchService.log(app,"LOCK_BOUNCER_RETRY_AFTER_DELAY");}
      catch(Exception ex){NfcWatchService.log(app,"LOCK_BOUNCER_RETRY_FAILED "+ex.getClass().getSimpleName());}
     },"KaliLock-BouncerRetry").start();
    }
   },2500);
  } finally {if(cpu.isHeld())cpu.release();}
  return true;
 }
}
