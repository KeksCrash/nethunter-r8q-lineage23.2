package de.nethunter.smartunlock;
import android.app.*;import android.content.*;import android.os.*;import java.util.concurrent.*;
/** Read-only LineageOS trust monitoring. Does not force grantTrust, bypass PIN, or switch profiles. */
public final class TrustMonitor extends Service {
 static final String PREFS="trust_monitor",UNLOCKED="user_present",CHANNEL="trust_monitor_v14";static final int NOTICE=1440;
 private ScheduledExecutorService worker;private volatile boolean busy;
 static boolean ready(Context c){UserManager u=(UserManager)c.getSystemService(USER_SERVICE);return c.getSharedPreferences(PREFS,0).getBoolean(UNLOCKED,false)&&u!=null&&u.isUserUnlocked();}
 static void setUnlocked(Context c,boolean value){c.getSharedPreferences(PREFS,0).edit().putBoolean(UNLOCKED,value).apply();}
 static void sync(Context c,State s){if(!s.error&&s.on&&ready(c)){try{c.startForegroundService(new Intent(c,TrustMonitor.class));}catch(Exception e){android.util.Log.w("SmartUnlock","Monitor start deferred",e);}}else{try{c.stopService(new Intent(c,TrustMonitor.class));}catch(Exception ignored){}}}
 @Override public void onCreate(){super.onCreate();NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);m.createNotificationChannel(new NotificationChannel(CHANNEL,"Smart Unlock Status",NotificationManager.IMPORTANCE_LOW));Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Smart Unlock aktiv").setContentText("Überwacht den LineageOS-Vertrauensstatus").setOngoing(true).build();startForeground(NOTICE,n);}
 @Override public int onStartCommand(Intent intent,int flags,int startId){if(!ready(this)){stopSelf();return START_NOT_STICKY;}if(worker==null){worker=Executors.newSingleThreadScheduledExecutor();worker.scheduleWithFixedDelay(()->{if(busy)return;busy=true;try{String raw=RootExec.runSync(getApplicationContext(),"status");State s=State.parse(raw);new Handler(Looper.getMainLooper()).post(()->{Widget.update(getApplicationContext(),s);if(s.error||!s.on)stopSelf();});}finally{busy=false;}},0,60,TimeUnit.SECONDS);}return START_STICKY;}
 @Override public void onDestroy(){if(worker!=null){worker.shutdownNow();worker=null;}super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
