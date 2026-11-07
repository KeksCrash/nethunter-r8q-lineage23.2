package de.nethunter.flippernfc;
import android.app.*;
import android.content.*;
import android.nfc.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
/** Android hidden NFC unlock callback can detect NFC tags on a locked screen. */
public class NfcWatchService extends Service {
 private volatile boolean destroyed;private volatile Tag handlingTag;
 private final ExecutorService nfcRegistration=Executors.newSingleThreadExecutor();
 private NfcAdapter adapter;private Object callback;private Method remove;private BroadcastReceiver adapterReceiver;private final Object handlerGuard=new Object();private final ExecutorService executor=Executors.newSingleThreadExecutor();private volatile long lastSeen=0;private volatile long unlockCooldownUntil=0;
 static synchronized void log(Context c,String msg){Context target=DirectBootUnlock.userUnlocked(c)?c:DirectBootUnlock.device(c);android.content.SharedPreferences p=target.getSharedPreferences("events",0);String old=p.getString("history","");p.edit().putString("history",(System.currentTimeMillis()+" "+msg+"\n"+old).substring(0,Math.min(5000,(System.currentTimeMillis()+" "+msg+"\n"+old).length()))).apply();}
 @Override public void onCreate(){super.onCreate(); NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("nfc","Flipper NFC",NotificationManager.IMPORTANCE_LOW));Notification n=new Notification.Builder(this,"nfc").setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("Flipper NFC bereit").setContentText("Tag-Erkennung am Sperrbildschirm").build();startForeground(42,n);new Thread(()->PinAuto.recoverIme(this),"KaliLock-ImeRecovery").start();adapter=NfcAdapter.getDefaultAdapter(this);registerUnlock();
  adapterReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
   if(!NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(intent.getAction()))return;
   int state=intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,NfcAdapter.STATE_OFF);
   if(state==NfcAdapter.STATE_ON){log(NfcWatchService.this,"NFC_ADAPTER_ON_REREGISTER");registerUnlock();}
   else if(state==NfcAdapter.STATE_OFF){log(NfcWatchService.this,"NFC_ADAPTER_OFF");}
  }};
  registerReceiver(adapterReceiver,new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
 }
 private void registerUnlock(){if(!destroyed&&!nfcRegistration.isShutdown())nfcRegistration.execute(this::registerUnlockNow);}
 private void registerUnlockNow(){synchronized(handlerGuard){if(destroyed)return;try{
  if(adapter==null||!adapter.isEnabled()){log(this,"LOCKED_READER_WAITING_NFC_ON");return;}
  // Avoid duplicate registrations across adapter restarts.
  if(callback!=null&&remove!=null){try{remove.invoke(adapter,callback);}catch(Exception ignored){}}
  callback=null;remove=null;
  Class<?> iface=Class.forName("android.nfc.NfcAdapter$NfcUnlockHandler");callback=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,m,args)->{
  // Java dynamic proxies receive Object methods too. Returning null for hashCode caused
  // NPE in NFC service HashMap during addNfcUnlockHandler registration.
  if("hashCode".equals(m.getName()))return System.identityHashCode(proxy);
  if("equals".equals(m.getName()))return proxy==(args==null||args.length==0?null:args[0]);
  if("toString".equals(m.getName()))return "KaliLockNfcUnlockHandler@"+Integer.toHexString(System.identityHashCode(proxy));
  if("onUnlockAttempted".equals(m.getName())&&args!=null&&args.length>0&&args[0] instanceof Tag){
   if(destroyed)return false;Tag tag=(Tag)args[0];long now=SystemClock.elapsedRealtime();
   // Feedback is independent of FIDO validation and throttled to one pulse per scan.
   if(now-lastSeen>=1500){NfcFeedback.vibrateOnDetection(this);log(this,"LOCKED_NFC_DETECTED screenInteractive="+((PowerManager)getSystemService(POWER_SERVICE)).isInteractive());}
   if(now<unlockCooldownUntil){log(this,"LOCKED_STAGE SKIP_COOLDOWN_AFTER_VALIDATED_FIDO");return false;}
   if(now-lastSeen>=1500){
    lastSeen=now;
    // Android may invalidate the Tag handle as soon as onUnlockAttempted returns.
    // Keep this callback alive while the FIDO operation is in progress; bounded wait.
    // Experimental: Android's NFC service may itself forbid IsoDep operations during this callback.
    log(this,"LOCKED_NFC_CALLBACK_BEGIN tech="+java.util.Arrays.toString(tag.getTechList()));
    UnlockTransaction transaction=new UnlockTransaction();
    Future<?> work;try{work=executor.submit(()->{UnlockTransaction.enter(transaction);try{handle(tag);}finally{UnlockTransaction.leave();}});}catch(RejectedExecutionException cancelled){transaction.cancel();return false;}
    try{work.get(20,TimeUnit.SECONDS);log(this,"LOCKED_NFC_CALLBACK_END");}
    catch(TimeoutException e){transaction.cancel();work.cancel(true);CtapFido.Session.cancelTag(tag);log(this,"LOCKED_NFC_CALLBACK_TIMEOUT_20S; NFC-Dienst moeglicherweise nicht kompatibel");}
    catch(InterruptedException e){Thread.currentThread().interrupt();transaction.cancel();work.cancel(true);CtapFido.Session.cancelTag(tag);log(this,"LOCKED_NFC_CALLBACK_INTERRUPTED");}
    catch(ExecutionException e){log(this,"LOCKED_NFC_CALLBACK_FAILED "+CrashRecorder.detail(e));}
   }return false;}
  if(m.getReturnType()==boolean.class)return false;
  if(m.getReturnType()==int.class)return 0;
  return null;
 });Method add=NfcAdapter.class.getMethod("addNfcUnlockHandler",iface,String[].class);boolean ok=(Boolean)add.invoke(adapter,callback,new String[]{"android.nfc.tech.NfcA","android.nfc.tech.NfcB","android.nfc.tech.IsoDep","android.nfc.tech.NfcF"});remove=NfcAdapter.class.getMethod("removeNfcUnlockHandler",iface);log(this,"LOCKED_READER="+ok+"; Callback gibt false zurück; entsperrt Android nicht direkt");}catch(Exception e){log(this,"LOCKED_READER_FAILED "+CrashRecorder.detail(e));}}}
 private void handle(Tag t){handlingTag=t;try{
  if(!DirectBootUnlock.userUnlocked(this)||DirectBootUnlock.enabled(this)){
   if(DirectBootUnlock.enabled(this)&&DirectBootUnlock.wanted(this)&&android.nfc.tech.IsoDep.get(t)!=null){
    try{boolean sent=DirectBootUnlock.unlock(this,t,msg->log(this,"BOOT_FIDO "+msg));if(sent)unlockCooldownUntil=SystemClock.elapsedRealtime()+18000;log(this,"BOOT_PASSWORD_SENT="+sent+" (unlock pending)");}
    catch(Exception ex){log(this,"BOOT_UNLOCK_FAILED "+ex.getClass().getSimpleName()+": "+ex.getMessage());}
   }return;
  }
  boolean auto=PinAuto.enabled(this),paired=CtapFido.paired(this),priorVerified=CtapFido.verified(this),iso=android.nfc.tech.IsoDep.get(t)!=null;
  log(this,"LOCKED_STAGE tag auto="+auto+" paired="+paired+" priorVerified="+priorVerified+" isoDep="+iso);
  if(auto&&paired&&iso){try{
   log(this,"LOCKED_STAGE assertion_begin");
   CtapFido.verify(this,t,msg->log(this,"LOCKED_FIDO "+msg));
   UnlockTransaction.current().markVerified();
   log(this,"LOCKED_STAGE assertion_ok");
   // Ignore subsequent scans while the bouncer opens and the IME delivers the one-use secret.
   unlockCooldownUntil=SystemClock.elapsedRealtime()+18000;
   boolean result=PinAuto.unlockAfterValidatedAssertion(this);
   log(this,"LOCKED_PIN_ATTEMPT sent="+result+" (does not confirm unlock)");
  }catch(Throwable ex){String detail=CrashRecorder.detail(ex);
   if(detail.contains("BasicTagTechnology.connect")||detail.contains("IsoDep.connect"))log(this,"LOCKED_NFC_PLATFORM_BLOCK: IsoDep.connect failed inside system lock callback; app-only changes may not fix");
   log(this,"LOCKED_FIDO_AUTH_FAILED "+detail);
  }return;}
  boolean match=TagIdentity.matches(this,t);log(this,"LOCKED_TAG_SEEN match="+match+" (UID cannot authorize unlock)");
  // UID recognition does not authorize unlock; haptic feedback already handled on discovery.
 }catch(Throwable e){log(this,"LOCKED_TAG_ERROR "+CrashRecorder.detail(e));}finally{handlingTag=null;}}
 @Override public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
 @Override public void onDestroy(){
  destroyed=true;UnlockTransaction.cancelAll();PinAuto.cancelPendingImePassword();CtapFido.Session.cancelTag(handlingTag);
  try{if(adapterReceiver!=null)unregisterReceiver(adapterReceiver);}catch(Exception ignored){}
  nfcRegistration.execute(()->{synchronized(handlerGuard){try{if(adapter!=null&&callback!=null&&remove!=null)remove.invoke(adapter,callback);}catch(Exception ignored){}callback=null;remove=null;}});
  nfcRegistration.shutdown();executor.shutdownNow();super.onDestroy();
 }
 @Override public IBinder onBind(Intent i){return null;}
}
