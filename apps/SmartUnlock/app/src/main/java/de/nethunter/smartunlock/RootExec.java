package de.nethunter.smartunlock;
import android.content.Context;import android.os.Handler;import android.os.Looper;
import java.io.ByteArrayOutputStream;import java.io.InputStream;import java.util.concurrent.*;
final class RootExec {
 interface Done{void result(String text);}
 static void run(Context c,String action,Done cb){new Thread(()->{String s=runSync(c,action);new Handler(Looper.getMainLooper()).post(()->cb.result(s));},"smartunlock-root").start();}
 static String runSync(Context c,String action){
  Process p=null;ExecutorService pool=Executors.newSingleThreadExecutor();
  try {
   String apk=c.getApplicationInfo().sourceDir;
   String cmd="CLASSPATH="+apk+":/system/framework/org.lineageos.platform.jar /system/bin/app_process /system/bin de.nethunter.smartunlock.RootBridge "+action;
   p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();final Process process=p;
   Future<String> f=pool.submit(()->{try(InputStream in=process.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1){if(out.size()<32768)out.write(buf,0,Math.min(n,32768-out.size()));}return out.toString("UTF-8");}});
   String out=f.get(15,TimeUnit.SECONDS);if(!p.waitFor(2,TimeUnit.SECONDS))p.destroyForcibly();return out;
  }catch(Exception e){if(p!=null)p.destroyForcibly();return "ERROR: "+e.getMessage();}finally{pool.shutdownNow();}
 }
}
