package de.nethunter.flippernfc;
import android.content.*;import java.util.*;
public final class EnrollmentRecoveryTest {
 static int passed;static void check(boolean b){if(!b)throw new AssertionError();passed++;}
 interface Checked{void run()throws Exception;}
 static void fails(Checked r)throws Exception{try{r.run();}catch(java.io.IOException|java.security.GeneralSecurityException expected){passed++;return;}throw new AssertionError("Expected failure");}
 static final class Memory extends android.content.Context{
  final Map<String,Prefs> files=new HashMap<>();
  public SharedPreferences getSharedPreferences(String name,int mode){return files.computeIfAbsent(name,n->new Prefs());}
 }
 static final class Prefs implements SharedPreferences{
  final Map<String,Object> data=new HashMap<>();
  public Map<String,?> getAll(){return new HashMap<>(data);}
  public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
  @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return (Set<String>)data.getOrDefault(k,d);}
  public int getInt(String k,int d){return (Integer)data.getOrDefault(k,d);}
  public long getLong(String k,long d){return (Long)data.getOrDefault(k,d);}
  public float getFloat(String k,float d){return (Float)data.getOrDefault(k,d);}
  public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}
  public boolean contains(String k){return data.containsKey(k);}
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
  public Editor edit(){return new Editor(){
   final Map<String,Object> changes=new HashMap<>();boolean clear;
   public Editor putString(String k,String v){changes.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){changes.put(k,v);return this;}
   public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putLong(String k,long v){changes.put(k,v);return this;}
   public Editor putFloat(String k,float v){changes.put(k,v);return this;}public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}
   public Editor remove(String k){changes.put(k,null);return this;}public Editor clear(){clear=true;return this;}
   public boolean commit(){if(clear)data.clear();for(var e:changes.entrySet())if(e.getValue()==null)data.remove(e.getKey());else data.put(e.getKey(),e.getValue());return true;}public void apply(){commit();}
  };}
 }
 public static void main(String[] args)throws Exception{
  Memory c=new Memory();CtapFido.Event log=s->{};
  DirectBootUnlock.provision(c,null,log);
  check(CtapFido.creates==1);check(CtapFido.derivations==0);check(DirectBootUnlock.hasPending(c));check(!DirectBootUnlock.configured(c));
  CtapFido.failDerive=true;fails(()->DirectBootUnlock.provision(c,null,log));fails(()->DirectBootUnlock.provision(c,null,log));
  check(CtapFido.creates==1);check(DirectBootUnlock.hasPending(c));
  CtapFido.failDerive=false;DirectBootUnlock.provision(c,null,log);
  check(CtapFido.creates==1);check(DirectBootUnlock.configured(c));check(!DirectBootUnlock.enabled(c));check(!DirectBootUnlock.hasPending(c));
  DirectBootUnlock.provision(c,null,log);check(CtapFido.creates==1);
  DirectBootUnlock.testAndEnable(c,null,log);check(DirectBootUnlock.enabled(c));
  DirectBootUnlock.passwordChanged(c);check(DirectBootUnlock.hasPending(c));check(!DirectBootUnlock.configured(c));
  DirectBootUnlock.provision(c,null,log);check(CtapFido.creates==1);check(DirectBootUnlock.configured(c));
  Memory lost=new Memory();CtapFido.failCreate=true;fails(()->DirectBootUnlock.provision(lost,null,log));
  int count=CtapFido.creates;check(DirectBootUnlock.creationUncertain(lost));
  fails(()->DirectBootUnlock.provision(lost,null,log));check(CtapFido.creates==count);
  // Simulated app restart: a fresh Context with the same durable preferences.
  Memory reopened=new Memory();reopened.files.putAll(lost.files);fails(()->DirectBootUnlock.provision(reopened,null,log));check(CtapFido.creates==count);
  Memory legacy=new Memory();legacy.getSharedPreferences("events",0).edit().putString("history","BOOT_CREDENTIAL_CREATED").commit();
  DirectBootUnlock.migrateInterruptedLegacyCreation(legacy);check(DirectBootUnlock.creationUncertain(legacy));
  DirectBootUnlock.acknowledgeUncertainCreation(legacy);check(!DirectBootUnlock.creationUncertain(legacy));
  Memory reuse=new Memory();CtapFido.failCreate=false;int beforeReuse=CtapFido.creates;
  reuse.getSharedPreferences(CtapFido.PREF,0).edit().putString("cred",CtapFido.b64(CtapFido.CRED.id)).putString("x",CtapFido.b64(CtapFido.CRED.x)).putString("y",CtapFido.b64(CtapFido.CRED.y)).putString("rpId",CtapFido.DEFAULT_RP).commit();
  DirectBootUnlock.useNormalCredential(reuse);check(CtapFido.creates==beforeReuse);check(DirectBootUnlock.hasPending(reuse));
  DirectBootUnlock.provision(reuse,null,log);check(CtapFido.creates==beforeReuse);check(DirectBootUnlock.configured(reuse));check(CtapFido.DEFAULT_RP.equals(DirectBootUnlock.prefs(reuse).getString("rp","")));
  DirectBootUnlock.testAndEnable(reuse,null,log);check(DirectBootUnlock.enabled(reuse));check(CtapFido.creates==beforeReuse);
  System.out.println("PASS "+passed+" persisted enrollment/retry/password-change/response-loss checks");
 }
}
