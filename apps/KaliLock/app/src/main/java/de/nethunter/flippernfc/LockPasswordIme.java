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
   KeyguardManager guard=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
   boolean locked=guard!=null&&guard.isDeviceLocked();
   boolean selected=PinAuto.imeSelected(LockPasswordIme.this);
   trusted=trusted&&locked&&selected;
   // Crucial: do not consume the one-use secret when Android presents another editor first.
   if(!(isPassword&&trusted)){debug("EDITOR_REJECTED pkg="+pkg+" type="+(info==null?0:info.inputType)+" locked="+locked+" selected="+selected);}
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
         if(PinAuto.deliveryStillValid()&&PinAuto.imeSelected(LockPasswordIme.this)&&active==editor && ("com.android.systemui".equals(target)||"com.android.keyguard".equals(target))){
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
 @Override public void onStartInput(EditorInfo attribute,boolean restarting){super.onStartInput(attribute,restarting);debug("ON_START_INPUT pkg="+(attribute==null?"":attribute.packageName));handler.removeCallbacks(tryCommit);handler.post(tryCommit);}
 @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);handler.removeCallbacks(tryCommit);handler.post(tryCommit);}
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);PinAuto.cancelPendingImePassword();super.onDestroy();}
 private boolean upper=false,symbols=false;
 private void manualKey(String text){PinAuto.cancelPendingImePassword();InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.commitText(text,1);}
 private void manualAction(int key){PinAuto.cancelPendingImePassword();InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));}}
 private Button key(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->action.run());return b;}
 private void refreshManual(){setInputView(onCreateInputView());}
 @Override public View onCreateInputView(){
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(6,4,6,6);layout.setBackgroundColor(Color.rgb(6,14,23));
  TextView title=new TextView(this);title.setText("Kari Nethunter · NFC oder manuelle Eingabe");title.setTextColor(Color.CYAN);layout.addView(title);
  String[] rows=symbols?new String[]{"1234567890","!@#$%^&*()","-_=+[]{}",";:,.?/\\'\""}:new String[]{"1234567890","qwertyuiop","asdfghjkl","zxcvbnm"};
  for(String row:rows){LinearLayout line=new LinearLayout(this);layout.addView(line);for(int i=0;i<row.length();i++){String ch=String.valueOf(row.charAt(i));if(upper&&!symbols)ch=ch.toUpperCase(java.util.Locale.ROOT);final String text=ch;line.addView(key(text,()->manualKey(text)),new LinearLayout.LayoutParams(0,(int)(46*getResources().getDisplayMetrics().density),1));}}
  LinearLayout controls=new LinearLayout(this);layout.addView(controls);
  controls.addView(key("⇧",()->{upper=!upper;refreshManual();}),new LinearLayout.LayoutParams(0,(int)(48*getResources().getDisplayMetrics().density),1));
  controls.addView(key(symbols?"ABC":"#+=",()->{symbols=!symbols;refreshManual();}),new LinearLayout.LayoutParams(0,(int)(48*getResources().getDisplayMetrics().density),1));
  controls.addView(key("Leer",()->manualKey(" ")),new LinearLayout.LayoutParams(0,(int)(48*getResources().getDisplayMetrics().density),2));
  controls.addView(key("⌫",()->manualAction(KeyEvent.KEYCODE_DEL)),new LinearLayout.LayoutParams(0,(int)(48*getResources().getDisplayMetrics().density),1));
  controls.addView(key("↵",()->manualAction(KeyEvent.KEYCODE_ENTER)),new LinearLayout.LayoutParams(0,(int)(48*getResources().getDisplayMetrics().density),1));
  layout.addView(key("Normale Tastatur wiederherstellen",()->new Thread(()->PinAuto.recoverIme(this),"KaliLock-ManualImeRecovery").start()));
  return layout;
 }
}
