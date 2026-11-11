package de.nethunter.smartunlock;
import android.content.Context;import android.os.Looper;import java.lang.reflect.Method;import java.util.UUID;
public final class RootBridge {
 static Object invoke(Object o,String method,Class<?>[] types,Object... args)throws Exception{return o.getClass().getMethod(method,types).invoke(o,args);}
 static String name(Object p)throws Exception{return (String)invoke(p,"getName",new Class<?>[0]);}
 static UUID uuid(Object p)throws Exception{return (UUID)invoke(p,"getUuid",new Class<?>[0]);}
 static String trustState(){try {Process p=new ProcessBuilder("/system/bin/dumpsys","trust").redirectErrorStream(true).start();String s=new String(p.getInputStream().readAllBytes(),"UTF-8");p.waitFor();for(String line:s.split("\n"))if(line.contains("trustState=TRUSTED"))return "TRUSTED";else if(line.contains("trustState=UNTRUSTED"))return "UNTRUSTED";}catch(Exception ignored){}return "UNKNOWN";}
 public static void main(String[] args){try {
  if(Looper.myLooper()==null)Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);Context context=(Context)at.getMethod("getSystemContext").invoke(thread);
  Class<?> pm=Class.forName("lineageos.app.ProfileManager");Object manager=pm.getMethod("getInstance",Context.class).invoke(null,context);
  Object current=invoke(manager,"getActiveProfile",new Class<?>[0]);Object[] profiles=(Object[])invoke(manager,"getProfiles",new Class<?>[0]);
  if(profiles==null){System.out.println("ERROR: Profile nicht verfügbar");return;}
  Object unlock=null,normal=null;StringBuilder all=new StringBuilder();for(Object p:profiles){String n=name(p);all.append(n).append(" | ");if(n.equalsIgnoreCase("Smart Unlock"))unlock=p;if(n.equalsIgnoreCase("Standard")||n.equalsIgnoreCase("Default")||n.equalsIgnoreCase("Normal"))normal=p;}
  if(args.length>0&&args[0].equals("toggle")){
   if(unlock==null||normal==null){System.out.println("ERROR: Benötigte Profile fehlen; "+all);return;}
   boolean active=current!=null&&uuid(current).equals(uuid(unlock));Object next=active?normal:unlock;
   invoke(manager,"setActiveProfile",new Class<?>[]{UUID.class},uuid(next));current=invoke(manager,"getActiveProfile",new Class<?>[0]);System.out.println("CHANGED: "+name(current));
  }
  System.out.println("PROFILE: "+(current==null?"(kein Profil)":name(current)));System.out.println("AVAILABLE: "+all);System.out.println("TRUST: "+trustState());
 }catch(Throwable t){System.out.println("ERROR: "+t);t.printStackTrace(System.out);}}
}
