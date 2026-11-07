package de.nethunter.flippernfc;

import android.app.KeyguardManager;
import android.content.*;
import android.nfc.Tag;
import android.os.UserManager;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;

/** DE stores only public metadata and a password wrapped by a fresh token-derived secret. */
final class DirectBootUnlock {
 private static final String PREF="boot_hmac_v1";
 private static final String PENDING="boot_pending_v1";
 private static SharedPreferences pending(Context c){return device(c).getSharedPreferences(PENDING,0);}
 static boolean hasPending(Context c){SharedPreferences p=pending(c);return p.contains("cred")&&p.contains("x")&&p.contains("y");}
 static boolean creationUncertain(Context c){return !hasPending(c)&&pending(c).getBoolean("creation_started",false);}
 static void migrateInterruptedLegacyCreation(Context c){synchronized(STATE_LOCK){
  SharedPreferences p=pending(c);if(p.getBoolean("legacy_checked",false))return;
  boolean lost=!configured(c)&&!hasPending(c)&&c.getSharedPreferences("events",0).getString("history","").contains("BOOT_CREDENTIAL_CREATED");
  SharedPreferences.Editor edit=p.edit().putBoolean("legacy_checked",true);
  if(lost)edit.putBoolean("creation_started",true);
  edit.commit();
 }}
 static void acknowledgeUncertainCreation(Context c){synchronized(STATE_LOCK){requireNoPending(c);UnlockTransaction.cancelAll();GENERATION.incrementAndGet();pending(c).edit().clear().putBoolean("legacy_checked",true).commit();}}
 private static void requireNoPending(Context c){if(configured(c)||hasPending(c))throw new IllegalStateException("Ein registrierter Schluessel ist vorhanden und wird nicht verworfen");}

 private static final Object STATE_LOCK=new Object();
 private static final java.util.concurrent.atomic.AtomicLong GENERATION=new java.util.concurrent.atomic.AtomicLong();
 private static void ownerOnly()throws GeneralSecurityException {require(android.os.Process.myUid()/100000==0,"BOOT_OWNER_USER_ONLY");}
 static Context device(Context c){return c.createDeviceProtectedStorageContext();}
 static boolean userUnlocked(Context c){UserManager u=(UserManager)c.getSystemService(Context.USER_SERVICE);return u!=null&&u.isUserUnlocked();}
 static SharedPreferences prefs(Context c){return device(c).getSharedPreferences(PREF,0);}
 static boolean configured(Context c){SharedPreferences p=prefs(c);return p.getInt("schema",0)==1&&p.contains("ciphertext")&&p.contains("cred");}
 static boolean enabled(Context c){return configured(c)&&prefs(c).getBoolean("enabled",false);}
 static boolean wanted(Context c){return device(c).getSharedPreferences("service_state",0).getBoolean("wanted",true);}
 static void setWanted(Context c,boolean wanted){synchronized(STATE_LOCK){if(!wanted){UnlockTransaction.cancelAll();GENERATION.incrementAndGet();}device(c).getSharedPreferences("service_state",0).edit().putBoolean("wanted",wanted).commit();}}
 static void cancelSetup(){synchronized(STATE_LOCK){UnlockTransaction.cancelAll();GENERATION.incrementAndGet();}}
 static void disable(Context c){synchronized(STATE_LOCK){UnlockTransaction.cancelAll();GENERATION.incrementAndGet();prefs(c).edit().putBoolean("enabled",false).commit();}}
 static void clear(Context c){synchronized(STATE_LOCK){UnlockTransaction.cancelAll();GENERATION.incrementAndGet();prefs(c).edit().clear().commit();pending(c).edit().clear().putBoolean("legacy_checked",true).commit();}}
 private static void commitCurrent(long generation,SharedPreferences.Editor editor)throws GeneralSecurityException {synchronized(STATE_LOCK){require(generation==GENERATION.get(),"BOOT_OPERATION_CANCELLED");require(editor.commit(),"BOOT_STORE_FAILED");}}
 private static void require(boolean ok,String msg)throws GeneralSecurityException{if(!ok)throw new GeneralSecurityException(msg);}
 private static byte[] read(SharedPreferences p,String name,int length)throws Exception{return BootCrypto.bytes(CtapFido.un64(p.getString(name,"")),length);}
 private static BootCrypto.Credential credential(SharedPreferences p)throws Exception{return new BootCrypto.Credential(p.getString("rp",BootCrypto.RP),read(p,"cred",-1),read(p,"x",32),read(p,"y",32));}
 private static void checkInfo(CtapFido.Session s)throws Exception{
  Map<?,?> info=s.cmd(4,null);Object extensions=MiniCbor.key(info,2),protocols=MiniCbor.key(info,6);
  require(extensions instanceof List&&((List<?>)extensions).contains("hmac-secret"),"BOOT_HMAC_SECRET_UNSUPPORTED");
  require(protocols instanceof List&&((List<?>)protocols).contains(1L),"BOOT_PIN_PROTOCOL_1_REQUIRED");
  Object options=MiniCbor.key(info,4);if(options instanceof Map)require(!Boolean.TRUE.equals(((Map<?,?>)options).get("alwaysUv")),"BOOT_ALWAYS_UV_AUTHENTICATOR_UNSUPPORTED");
 }
 private static final class Derived implements AutoCloseable {
  final byte[] output;final long counter;
  Derived(byte[] output,long counter){this.output=output;this.counter=counter;}
  public void close(){Arrays.fill(output,(byte)0);}
 }
 private static Derived derive(CtapFido.Session s,BootCrypto.Credential c,byte[] salt,long previous,CtapFido.Event event)throws Exception{
  event.log("BOOT_KEY_AGREEMENT_BEGIN");
  CtapFido.Pins agreement=CtapFido.Pins.agree(s);event.log("BOOT_KEY_AGREEMENT_OK");
  try{
   byte[] enc=CtapFido.Pins.aes(Cipher.ENCRYPT_MODE,agreement.secret,salt);
   byte[] auth=Arrays.copyOf(CtapFido.Pins.hmac(agreement.secret,enc),16),challenge=BootCrypto.random(32);
   Map<Object,Object> ext=CtapFido.m("hmac-secret",CtapFido.m(1,agreement.client,2,enc,3,auth,4,1));
   event.log("BOOT_HMAC_ASSERTION_BEGIN");
   Map<?,?> response=s.cmd(2,CtapFido.m(1,c.rp,2,challenge,3,Arrays.asList(CtapFido.m("id",c.id,"type","public-key")),4,ext,5,CtapFido.m("up",true)),event);
   Object descriptor=MiniCbor.key(response,1);
   if(descriptor!=null){require(descriptor instanceof Map,"BOOT_INVALID_CREDENTIAL_DESCRIPTOR");Map<?,?> d=(Map<?,?>)descriptor;require("public-key".equals(d.get("type"))&&MessageDigest.isEqual(c.id,BootCrypto.bytes(d.get("id"),-1)),"BOOT_CREDENTIAL_MISMATCH");}
   byte[] data=BootCrypto.bytes(MiniCbor.key(response,2),-1),sig=BootCrypto.bytes(MiniCbor.key(response,3),-1);
   // The extension output is signed. Validate all authData before decrypting it.
   byte[] encrypted=BootCrypto.assertion(c,challenge,data,sig,previous);
   byte[] output=CtapFido.Pins.aes(Cipher.DECRYPT_MODE,agreement.secret,encrypted);
   return new Derived(BootCrypto.bytes(output,32),BootCrypto.counter(data));
  }finally{Arrays.fill(agreement.secret,(byte)0);}
 }
 /** Prepare reuse of the known normal credential: this sends NO makeCredential command. */
 static void useNormalCredential(Context c)throws Exception{synchronized(STATE_LOCK){
  ownerOnly();require(userUnlocked(c)&&!configured(c)&&!hasPending(c),"BOOT_EXISTING_SETUP_MUST_BE_FINISHED_FIRST");
  SharedPreferences normal=c.getSharedPreferences(CtapFido.PREF,0);
  require(normal.contains("cred"),"BOOT_NO_NORMAL_CREDENTIAL");
  BootCrypto.Credential cred=new BootCrypto.Credential(normal.getString("rpId",CtapFido.DEFAULT_RP),read(normal,"cred",-1),read(normal,"x",32),read(normal,"y",32));
  require(cred.id.length>0&&cred.id.length<=1024,"BOOT_INVALID_CREDENTIAL_ID");BootCrypto.publicKey(cred);
  UnlockTransaction.cancelAll();long generation=GENERATION.incrementAndGet();
  commitCurrent(generation,pending(c).edit().clear().putBoolean("legacy_checked",true).putString("rp",cred.rp).putString("cred",CtapFido.b64(cred.id)).putString("x",CtapFido.b64(cred.x)).putString("y",CtapFido.b64(cred.y)).putString("salt",CtapFido.b64(BootCrypto.random(32))).putString("nonce",CtapFido.b64(BootCrypto.random(12))).putLong("counter",normal.getLong("counter",0)));
 }}
 /** Password changes invalidate only the envelope, retaining the existing public boot credential. */
 static void passwordChanged(Context c){synchronized(STATE_LOCK){
  UnlockTransaction.cancelAll();GENERATION.incrementAndGet();
  if(configured(c)){
   SharedPreferences old=prefs(c);SharedPreferences.Editor edit=pending(c).edit().clear();
   for(String field:new String[]{"cred","x","y"})edit.putString(field,old.getString(field,""));
   edit.putString("rp",old.getString("rp",BootCrypto.RP)).putLong("counter",old.getLong("counter",0)).putString("salt",CtapFido.b64(BootCrypto.random(32))).putString("nonce",CtapFido.b64(BootCrypto.random(12)));
   if(!edit.commit())throw new IllegalStateException("BOOT_PENDING_STORE_FAILED");
  }
  prefs(c).edit().clear().commit();
 }}
 static synchronized void provision(Context c,Tag tag,CtapFido.Event event)throws Exception{
  ownerOnly();
  require(userUnlocked(c)&&PinAuto.configured(c),"BOOT_FIRST_CONFIGURE_ANDROID_PASSWORD");
  require(!c.getApplicationInfo().sourceDir.startsWith("/mnt/expand/"),"BOOT_MOVE_APP_TO_INTERNAL_STORAGE_FIRST");
  if(configured(c)){event.log("BOOT_EXISTING_KEY_REUSED; Einrichtung vorhanden, nur testen/aktivieren");return;}
  if(hasPending(c)){wrapPending(c,tag,event);return;}
  require(!creationUncertain(c),"BOOT_CREATION_UNCERTAIN: Erst am Flipper pruefen; kein weiterer Schluessel wird automatisch erstellt");
  long generation; synchronized(STATE_LOCK){UnlockTransaction.cancelAll();generation=GENERATION.incrementAndGet();}
  try(CtapFido.Session session=new CtapFido.Session(tag)){
   checkInfo(session);
   // Persist BEFORE sending makeCredential: lost responses may already have created a key.
   commitCurrent(generation,pending(c).edit().clear().putBoolean("creation_started",true));
   event.log("BOOT_CREATE_SEPARATE_CREDENTIAL; bestaetige am Flipper");
   Map<?,?> response=session.cmd(1,CtapFido.m(1,BootCrypto.random(32),2,CtapFido.m("id",BootCrypto.RP,"name","Kali Lock Boot"),3,CtapFido.m("id",BootCrypto.random(32),"name","Kali Lock first unlock","displayName","Kali Lock first unlock"),4,Arrays.asList(CtapFido.m("alg",-7,"type","public-key")),6,CtapFido.m("hmac-secret",true,"credProtect",1),7,CtapFido.m("rk",false)),event);
   byte[] data=BootCrypto.bytes(MiniCbor.key(response,2),-1);
   BootCrypto.Credential cred=BootCrypto.registration(data);
   // Save public metadata immediately; do NOT issue a second CTAP operation on this tap.
   commitCurrent(generation,pending(c).edit().clear().putString("cred",CtapFido.b64(cred.id)).putString("x",CtapFido.b64(cred.x)).putString("y",CtapFido.b64(cred.y)).putString("salt",CtapFido.b64(BootCrypto.random(32))).putString("nonce",CtapFido.b64(BootCrypto.random(12))).putLong("counter",BootCrypto.counter(data)));
   event.log("BOOT_KEY_SAVED; Flipper entfernen. Naechster NFC-Kontakt verbindet das Passwort; kein neuer Schluessel.");
  }
 }
 private static void wrapPending(Context c,Tag tag,CtapFido.Event event)throws Exception{
  long generation=GENERATION.get();SharedPreferences p=pending(c);
  require(hasPending(c),"BOOT_NO_PENDING_CREDENTIAL");BootCrypto.Credential cred=credential(p);
  byte[] password=PinAuto.readPasswordBytes(c),salt=read(p,"salt",32),nonce=read(p,"nonce",12);
  try(CtapFido.Session session=new CtapFido.Session(tag);Derived secret=derive(session,cred,salt,p.getLong("counter",0),event)){
   byte[] wrapped=BootCrypto.crypt(Cipher.ENCRYPT_MODE,cred,salt,secret.output,nonce,password);
   synchronized(STATE_LOCK){
    commitCurrent(generation,prefs(c).edit().clear().putInt("schema",1).putString("rp",cred.rp).putString("cred",CtapFido.b64(cred.id)).putString("x",CtapFido.b64(cred.x)).putString("y",CtapFido.b64(cred.y)).putString("salt",CtapFido.b64(salt)).putString("nonce",CtapFido.b64(nonce)).putString("ciphertext",CtapFido.b64(wrapped)).putLong("counter",secret.counter).putBoolean("enabled",false));
    pending(c).edit().clear().commit();
   }
   event.log("BOOT_PROVISIONED_DISABLED; Passwort gebunden. Flipper entfernen, dann testen/aktivieren.");
  }finally{Arrays.fill(password,(byte)0);}
 }
 static synchronized void testAndEnable(Context c,Tag tag,CtapFido.Event event)throws Exception{
  ownerOnly();long generation=GENERATION.get();
  require(userUnlocked(c)&&configured(c),"BOOT_NOT_PROVISIONED");SharedPreferences p=prefs(c);
  BootCrypto.Credential cred=credential(p);
  byte[] password=null,expected=null;
  try(CtapFido.Session s=new CtapFido.Session(tag);Derived secret=derive(s,cred,read(p,"salt",32),p.getLong("counter",0),event)){
   password=BootCrypto.crypt(Cipher.DECRYPT_MODE,cred,read(p,"salt",32),secret.output,read(p,"nonce",12),read(p,"ciphertext",-1));
   expected=PinAuto.readPasswordBytes(c);require(MessageDigest.isEqual(password,expected),"BOOT_PASSWORD_CHANGED_REPROVISION_REQUIRED");
   PinAuto.prepareBootIme();require(generation==GENERATION.get(),"BOOT_SETUP_CANCELLED");
   synchronized(STATE_LOCK){commitCurrent(generation,p.edit().putLong("counter",secret.counter).putBoolean("enabled",true));setWanted(c,true);}
   event.log("BOOT_SECRET_ROUNDTRIP_CONFIRMED; enabled=true; Reboot noch NICHT getestet");
  }finally{if(password!=null)Arrays.fill(password,(byte)0);if(expected!=null)Arrays.fill(expected,(byte)0);}
 }
 static synchronized boolean unlock(Context c,Tag tag,CtapFido.Event event)throws Exception{
  ownerOnly();long generation=GENERATION.get();UnlockTransaction transaction=UnlockTransaction.current();
  require(enabled(c)&&wanted(c),"BOOT_UNLOCK_NOT_ENABLED");
  KeyguardManager guard=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);
  require(guard!=null&&guard.isDeviceLocked(),"BOOT_DEVICE_NOT_LOCKED");
  SharedPreferences p=prefs(c);BootCrypto.Credential cred=credential(p);byte[] password=null;
  try(CtapFido.Session s=new CtapFido.Session(tag);Derived secret=derive(s,cred,read(p,"salt",32),p.getLong("counter",0),event)){
   password=BootCrypto.crypt(Cipher.DECRYPT_MODE,cred,read(p,"salt",32),secret.output,read(p,"nonce",12),read(p,"ciphertext",-1));
   commitCurrent(generation,p.edit().putLong("counter",secret.counter));
   transaction.check();require(generation==GENERATION.get()&&enabled(c)&&wanted(c),"BOOT_UNLOCK_CANCELLED");
   transaction.markVerified();event.log("BOOT_FIDO_AND_DECRYPTION_CONFIRMED");
   return PinAuto.submitValidatedPassword(c,new String(password,java.nio.charset.StandardCharsets.UTF_8));
  }finally{if(password!=null)Arrays.fill(password,(byte)0);}
 }
}
