package de.nethunter.flippernfc;
import android.content.*;
/** Stores only exception summaries, without credentials, PINs, stack dumps or NFC APDUs. */
final class CrashRecorder {
 private static volatile boolean installed=false;
 static synchronized void install(Context c){if(installed)return;installed=true;Context ctx=c.getApplicationContext();Thread.UncaughtExceptionHandler old=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((t,e)->{try{String summary="Thread="+t.getName()+" "+e.getClass().getSimpleName()+" "+String.valueOf(e.getMessage());ctx.getSharedPreferences("crash_v15",0).edit().putString("last",summary).commit();NfcWatchService.log(ctx,"UNCAUGHT_EXCEPTION "+summary);}catch(Throwable ignored){}if(old!=null)old.uncaughtException(t,e);});}
 static String lastCrash(Context c){return c.getSharedPreferences("crash_v15",0).getString("last","Keiner protokolliert");}
 static String detail(Throwable e){
  StringBuilder b=new StringBuilder();int depth=0;
  while(e!=null&&depth++<5){b.append(e.getClass().getName()).append('(').append(e.getMessage()==null?"no-message":e.getMessage()).append(')');StackTraceElement[] trace=e.getStackTrace();for(int j=0;j<Math.min(3,trace.length);j++)b.append(" @").append(trace[j].toString());e=e.getCause();if(e!=null)b.append(" -> ");}
  return b.toString();
 }
}
