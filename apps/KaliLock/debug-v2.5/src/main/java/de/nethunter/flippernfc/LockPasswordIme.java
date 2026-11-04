package de.nethunter.flippernfc;
import android.inputmethodservice.InputMethodService;
import android.content.*;
import android.app.KeyguardManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.graphics.Color;
/** Local IME: sends a pending, 15-second, one-use password only to the visible system lock screen. */
public class LockPasswordIme extends InputMethodService {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private long lastDiagnostic=0;
 private void debug(String s){long now=SystemClock.elapsedRealtime();if(now-lastDiagnostic>=1500){lastDiagnostic=now;NfcWatchService.log(this,"IME_STAGE "+s);}}
 private final Runnable tryCommit=new Runnable(){@Override public void run(){
  InputConnection ic=getCurrentInputConnection();
  if(ic!=null && PinAuto.hasPendingImePassword()){
   EditorInfo info=getCurrentInputEditorInfo();
   int variation=info==null?0:(info.inputType & android.text.InputType.TYPE_MASK_VARIATION);
   boolean isPassword=variation==android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD || variation==android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation==android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation==android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
   String pkg=info==null?"":info.packageName;
   boolean trusted="com.android.systemui".equals(pkg)||"com.android.keyguard".equals(pkg);
   // Crucial: do not consume the one-use secret when Android presents another editor first.
   if(!(isPassword&&trusted)){debug("EDITOR_REJECTED pkg="+pkg+" type="+(info==null?0:info.inputType));}
   if(isPassword&&trusted){
    NfcWatchService.log(LockPasswordIme.this,"IME_STAGE PASSWORD_EDITOR_READY pkg="+pkg);
    String secret=PinAuto.takePendingImePassword();if(secret!=null){
     try{
      boolean committed=ic.commitText(secret,1);
      NfcWatchService.log(LockPasswordIme.this, committed ? "LOCK_PASSWORD_IME_COMMITTED (unlock pending)" : "LOCK_PASSWORD_IME_COMMIT_REJECTED");
      if(committed){
       // IME_ACTION_DONE can be accepted without actually submitting the Keyguard password.
       // Wait for the system to validate before trying exactly ONE Enter key pair.
       final InputConnection editor=ic;
       handler.postDelayed(()->{
        try{
         KeyguardManager kg=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
         if(kg!=null && !kg.isDeviceLocked()){
          NfcWatchService.log(LockPasswordIme.this,"LOCK_UNLOCK_CONFIRMED");
          PinAuto.restoreIme(LockPasswordIme.this);return;
         }
         EditorInfo current=getCurrentInputEditorInfo();
         InputConnection active=getCurrentInputConnection();
         String target=current==null?"":current.packageName;
         if(active==editor && ("com.android.systemui".equals(target)||"com.android.keyguard".equals(target))){
          boolean down=active.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
          boolean up=active.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
          NfcWatchService.log(LockPasswordIme.this,"LOCK_SUBMIT_ENTER_ONCE down="+down+" up="+up);
         }else NfcWatchService.log(LockPasswordIme.this,"LOCK_SUBMIT_SKIP_EDITOR_CHANGED");
        }catch(Throwable e){NfcWatchService.log(LockPasswordIme.this,"LOCK_SUBMIT_ERROR "+e.getClass().getSimpleName());}
       },950);
       handler.postDelayed(()->{
        try{
         KeyguardManager kg=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
         NfcWatchService.log(LockPasswordIme.this,kg!=null&&!kg.isDeviceLocked()?"LOCK_UNLOCK_CONFIRMED":"LOCK_STILL_LOCKED_AFTER_SUBMIT");
        }catch(Throwable e){NfcWatchService.log(LockPasswordIme.this,"LOCK_VERIFY_ERROR "+e.getClass().getSimpleName());}
        PinAuto.restoreIme(LockPasswordIme.this);
       },3200);
      }else PinAuto.restoreIme(LockPasswordIme.this);
     }catch(Throwable ex){NfcWatchService.log(LockPasswordIme.this,"LOCK_PASSWORD_IME_FAILED "+CrashRecorder.detail(ex));}
    }
   }
  }else if(PinAuto.hasPendingImePassword()){debug("NO_INPUT_CONNECTION");}
  handler.postDelayed(this,500);
 }};
 @Override public void onCreate(){super.onCreate();handler.postDelayed(tryCommit,400);}
 @Override public void onStartInput(EditorInfo attribute,boolean restarting){super.onStartInput(attribute,restarting);debug("ON_START_INPUT pkg="+(attribute==null?"":attribute.packageName));handler.post(tryCommit);}
 @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);handler.post(tryCommit);}
 @Override public void onDestroy(){handler.removeCallbacks(tryCommit);super.onDestroy();}
 @Override public View onCreateInputView(){TextView t=new TextView(this);t.setText("🔒 Kali Lock – NFC-Freigabe nur nach FIDO-Signatur");t.setTextSize(16);t.setPadding(18,20,18,20);t.setBackgroundColor(Color.rgb(6,14,23));t.setTextColor(Color.CYAN);return t;}
}
