package de.nethunter.flippernfc;
import android.content.*;import android.nfc.Tag;import android.nfc.tech.IsoDep;import android.util.Base64;
import java.io.*;import java.math.BigInteger;import java.nio.charset.StandardCharsets;import java.security.*;import java.security.interfaces.ECPublicKey;import java.security.spec.*;import java.util.*;import javax.crypto.*;import javax.crypto.spec.*;
/** NFC CTAP2 test client: four enrollment modes, signature validation; no Android keyguard bypass. */
final class CtapFido {
 static final String DEFAULT_RP="flipper-nfc.local";static final String PREF="fido_credentials";
 interface Event {void log(String s);}static class Failure extends Exception {Failure(String s){super(s);}}
 static Map<Object,Object> m(Object... p){Map<Object,Object> a=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)a.put(p[i],p[i+1]);return a;}
 static byte[] random(int n){byte[] b=new byte[n];new SecureRandom().nextBytes(b);return b;}
 static byte[] sha(byte[] b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
 static byte[] join(byte[]...parts){ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] a:parts)b.write(a,0,a.length);return b.toByteArray();}
 static byte[] sub(byte[] b,int a,int z){return Arrays.copyOfRange(b,a,z);}
 static byte[] bytes(Object o,String field)throws Failure{if(!(o instanceof byte[]))throw new Failure(field+" MISSING");return (byte[])o;}
 static Map<?,?> map(Object o,String field)throws Failure{if(!(o instanceof Map))throw new Failure(field+" MISSING");return (Map<?,?>)o;}
 static long num(Object o,String field)throws Failure{if(!(o instanceof Number))throw new Failure(field+" MISSING");return ((Number)o).longValue();}
 static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}static byte[] un64(String b){return Base64.decode(b,Base64.NO_WRAP);}
 static final class Session implements AutoCloseable{
  final IsoDep iso; Session(Tag t)throws Exception{iso=IsoDep.get(t);if(iso==null)throw new Failure("KEIN_ISODEP: ZeroFIDO NFC statt Kartenemulation verwenden");iso.connect();iso.setTimeout(20000);byte[] aid={ (byte)0xa0,0,0,0x06,0x47,0x2f,0,1};apdu(join(new byte[]{0,(byte)0xa4,4,0,8},aid,new byte[]{0}));}
  // CTAP2-NFC 0x9100 means "still processing" or "user presence needed", not a failed APDU.
  // Keep polling on the same NFC tag without silently truncating the registration.
  byte[] apdu(byte[] data)throws Exception{return apdu(data,null);}
  byte[] apdu(byte[] data,Event event)throws Exception{
   byte[] reply=iso.transceive(data);ByteArrayOutputStream out=new ByteArrayOutputStream();
   long started=android.os.SystemClock.elapsedRealtime(),lastNotice=0;
   int keepalives=0,chains=0;
   for(int i=0;i<1200;i++){
    if(reply.length<2)throw new Failure("APDU_SHORT");
    int sw=((reply[reply.length-2]&255)<<8)|(reply[reply.length-1]&255);
    if(sw==0x9100){
     keepalives++;
     int reason=reply.length>2?reply[0]&255:0;
     long now=android.os.SystemClock.elapsedRealtime();
     if(event!=null && (lastNotice==0 || now-lastNotice>4000)){
      event.log("FIDO_WARTET: "+(reason==2?"BENUTZERBESTAETIGUNG_NOETIG":"FLIPPER_VERARBEITET")+" keepalives="+keepalives+" sek="+((now-started)/1000));lastNotice=now;
     }
     if(now-started>90000)throw new Failure("FIDO_TIMEOUT_90S: Keine fertige Antwort, Flipper NFC-Modus/Bestätigung prüfen; keepalives="+keepalives);
     reply=iso.transceive(new byte[]{(byte)0x80,0x11,0,0,0});
     continue;
    }
    out.write(reply,0,reply.length-2);
    if((sw&0xff00)==0x6100){
     if(++chains>256||out.size()>65536)throw new Failure("ISO7816_RESPONSE_TOO_LARGE");
     reply=iso.transceive(new byte[]{0,(byte)0xc0,0,0,(byte)(sw&255)});continue;
    }
    if(sw!=0x9000)throw new Failure(String.format(Locale.ROOT,"ISO7816_%04X",sw));
    if(event!=null&&keepalives>0)event.log("FIDO_ANTWORT_OK keepalives="+keepalives+" bytes="+out.size());
    return out.toByteArray();
   }
   throw new Failure("FIDO_POLL_LIMIT_1200");
  }

  Map<?,?> cmd(int command,Map<Object,Object> params)throws Exception{return cmd(command,params,null);}
  Map<?,?> cmd(int command,Map<Object,Object> params,Event event)throws Exception{byte[] b=join(new byte[]{(byte)command},params==null?new byte[0]:MiniCbor.encode(params));byte[] data=join(new byte[]{(byte)0x80,0x10,(byte)0x80,0,0,(byte)(b.length>>8),(byte)b.length},b,new byte[]{0,0});byte[] r=apdu(data,event);if(r.length==0)throw new Failure("CTAP_NO_REPLY");int status=r[0]&255;if(status!=0)throw new Failure(String.format(Locale.ROOT,"CTAP2_STATUS_0x%02X",status));return r.length==1?m():map(MiniCbor.decode(sub(r,1,r.length)),"CTAP response");}
  public void close(){try{iso.close();}catch(Exception ignored){}}
 }
 static Map<?,?> info(Tag tag,Event event)throws Exception{try(Session s=new Session(tag)){Map<?,?> info=s.cmd(4,null);event.log("CTAP_INFO versions="+MiniCbor.key(info,1)+" options="+MiniCbor.key(info,4)+" pinProtocols="+MiniCbor.key(info,6));return info;}}
 static String pinStatus(Tag tag,Event event)throws Exception{try(Session s=new Session(tag)){Map<?,?> info=s.cmd(4,null);Map<?,?> opts=map(MiniCbor.key(info,4),"options");Object state=opts.get("clientPin");String result=Boolean.TRUE.equals(state)?"GESETZT":Boolean.FALSE.equals(state)?"NICHT_GESETZT":"UNBEKANNT";event.log("ZEROFIDO_PIN_STATUS="+result+" versions="+MiniCbor.key(info,1)+" protocols="+MiniCbor.key(info,6));try{Map<?,?> p=s.cmd(6,m(1,1,2,1));event.log("PIN_RETRIES="+MiniCbor.key(p,3));}catch(Exception ex){event.log("PIN_RETRIES_UNAVAILABLE="+ex.getMessage());}return result;}}
 static final class Pins {
  final byte[] secret;final Map<Object,Object> client;Pins(byte[] s,Map<Object,Object> c){secret=s;client=c;}
  static byte[] fixed(BigInteger n){byte[] a=n.toByteArray(),o=new byte[32];int shift=a.length==33?1:0;System.arraycopy(a,shift,o,32-(a.length-shift),a.length-shift);return o;}
  static byte[] hmac(byte[] k,byte[] p)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(k,"HmacSHA256"));return mac.doFinal(p);}
  static byte[] aes(int mode,byte[] key,byte[] val)throws Exception{Cipher c=Cipher.getInstance("AES/CBC/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new IvParameterSpec(new byte[16]));return c.doFinal(val);}
  static Pins agree(Session s)throws Exception{Map<?,?> resp=s.cmd(6,m(1,1,2,2));Map<?,?> peer=map(MiniCbor.key(resp,1),"keyAgreement");byte[] x=bytes(MiniCbor.key(peer,-2),"x"),y=bytes(MiniCbor.key(peer,-3),"y");KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));KeyPair kp=g.generateKeyPair();ECPublicKey pub=(ECPublicKey)kp.getPublic();PublicKey other=KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1,x),new BigInteger(1,y)),pub.getParams()));KeyAgreement a=KeyAgreement.getInstance("ECDH");a.init(kp.getPrivate());a.doPhase(other,true);return new Pins(sha(a.generateSecret()),m(1,2,3,-25,-1,1,-2,fixed(pub.getW().getAffineX()),-3,fixed(pub.getW().getAffineY())));}
  static byte[] token(Session s,String pin,boolean version21,String rpId,Event event)throws Exception{
    Pins p=agree(s);byte[] hash=sha(pin.getBytes(StandardCharsets.UTF_8)),encrypted=aes(Cipher.ENCRYPT_MODE,p.secret,Arrays.copyOf(hash,16));Arrays.fill(hash,(byte)0);
    Map<Object,Object> args=version21?m(1,1,2,9,3,p.client,6,encrypted,9,1,10,rpId):m(1,1,2,5,3,p.client,6,encrypted);
    byte[] response=bytes(MiniCbor.key(s.cmd(6,args),2),"pinToken");byte[] token=aes(Cipher.DECRYPT_MODE,p.secret,response);Arrays.fill(p.secret,(byte)0);if(token.length!=16&&token.length!=32)throw new Failure("INVALID_PIN_TOKEN");event.log("PIN_AUTH_TOKEN_OK");return token;
  }
 }
 static PublicKey pub(byte[] x,byte[] y)throws Exception{KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));ECPublicKey template=(ECPublicKey)g.generateKeyPair().getPublic();return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1,x),new BigInteger(1,y)),template.getParams()));}
 static String displayAlias(Context c){return c.getSharedPreferences(PREF,0).getString("alias","Mein ZeroFIDO-Schlüssel");}
 static String rpId(Context c){return c.getSharedPreferences(PREF,0).getString("rpId",DEFAULT_RP);}
 static String rpName(Context c){return c.getSharedPreferences(PREF,0).getString("rpName","S20 NFC");}
 static String futureRpId(Context c){return c.getSharedPreferences("fido_rp_defaults",0).getString("rpId",DEFAULT_RP);}
 static String futureRpName(Context c){return c.getSharedPreferences("fido_rp_defaults",0).getString("rpName","S20 NFC");}
 static String validateRpId(String value){
  if(value==null)throw new IllegalArgumentException("RP-ID fehlt");
  String rp=value.trim().toLowerCase(java.util.Locale.ROOT);
  if(rp.length()>253||!rp.matches("(?=.{1,253}$)[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?)+"))throw new IllegalArgumentException("RP-ID als DNS-Name eingeben (z.B. flipper-nfc.local)");
  return rp;
 }
 static String validateRpName(String value){
  if(value==null||value.trim().isEmpty()||value.codePointCount(0,value.length())>64)throw new IllegalArgumentException("RP-Anzeigename muss 1–64 Zeichen haben");
  return value.trim();
 }
 static void renameRp(Context c,String label){c.getSharedPreferences(PREF,0).edit().putString("rpName",validateRpName(label)).commit();}
 static void saveFutureRp(Context c,String id,String label){c.getSharedPreferences("fido_rp_defaults",0).edit().putString("rpId",validateRpId(id)).putString("rpName",validateRpName(label)).commit();}
 static String userName(Context c){return c.getSharedPreferences(PREF,0).getString("userName","S20 FE");}
 static void rename(Context c,String alias){String name=alias==null?"":alias.trim();if(name.isEmpty()||name.codePointCount(0,name.length())>64)throw new IllegalArgumentException("Name muss 1–64 Zeichen lang sein");c.getSharedPreferences(PREF,0).edit().putString("alias",name).apply();}
 static boolean paired(Context c){return c.getSharedPreferences(PREF,0).contains("cred");}
 static void enroll(Context c,Tag tag,boolean version21,boolean usePin,String pin,Event event)throws Exception{enroll(c,tag,version21,usePin,pin,"Mein ZeroFIDO-Schlüssel","S20 FE",futureRpId(c),futureRpName(c),event);}
 static void enroll(Context c,Tag tag,boolean version21,boolean usePin,String pin,String alias,String username,String requestedRpId,String requestedRpName,Event event)throws Exception{
  final String registrationRpId=validateRpId(requestedRpId),registrationRpName=validateRpName(requestedRpName);
  if(alias==null||alias.trim().isEmpty()||alias.codePointCount(0,alias.length())>64)throw new Failure("INVALID_ALIAS");
  if(username==null||username.trim().isEmpty()||username.codePointCount(0,username.length())>64)throw new Failure("INVALID_USER_NAME");
  try(Session s=new Session(tag)){
    Map<?,?> info=s.cmd(4,null);Object versions=MiniCbor.key(info,1);String desired=version21?"FIDO_2_1":"FIDO_2_0";if(!(versions instanceof List)||!((List<?>)versions).contains(desired))throw new Failure("FIDO_VERSION_NOT_ADVERTISED: "+desired);
    if(usePin&&(pin==null||pin.isEmpty()))throw new Failure("ZEROFIDO_PIN_FEHLT_IM_EINGABEFELD");
    byte[] challenge=random(32),id=random(16);
    Map<Object,Object> args=m(1,challenge,2,m("id",registrationRpId,"name",registrationRpName),3,m("id",id,"name",username.trim(),"displayName",alias.trim()),4,Arrays.asList(m("alg",-7,"type","public-key")),7,m("rk",false));
    event.log("MAKE_CREDENTIAL version="+desired+" pin="+usePin+" rk=false");
    if(usePin){Map<?,?> opts=map(MiniCbor.key(info,4),"options");if(!Boolean.TRUE.equals(opts.get("clientPin")))throw new Failure("ZERO_FIDO_PIN_NICHT_EINGERICHTET_ODER_DEAKTIVIERT");Map<?,?> retries=s.cmd(6,m(1,1,2,1));Object remain=MiniCbor.key(retries,3);if(remain instanceof Number && ((Number)remain).longValue()<=0)throw new Failure("PIN_GESPERRT_KEINE_WEITEREN_VERSUCHE");event.log("PIN_RETRIES_PRECHECK="+remain);byte[] token=Pins.token(s,pin,version21,registrationRpId,event);args.put(8,Arrays.copyOf(Pins.hmac(token,challenge),16));args.put(9,1);Arrays.fill(token,(byte)0);}
    Map<?,?> response=s.cmd(1,args,event);byte[] auth=bytes(MiniCbor.key(response,2),"authData");
    if(auth.length<55||(auth[32]&0x41)!=0x41||!MessageDigest.isEqual(sub(auth,0,32),sha(registrationRpId.getBytes(StandardCharsets.UTF_8))))throw new Failure("REGISTRATION_AUTHDATA_INVALID");
    int clen=((auth[53]&255)<<8)|(auth[54]&255),offset=55;if(clen<1||clen>1024||offset+clen>=auth.length)throw new Failure("BAD_CREDENTIAL_LENGTH");
    byte[] cred=sub(auth,offset,offset+clen);Map<?,?> cose=map(MiniCbor.at(auth,offset+clen).value,"COSE_KEY");
    if(num(MiniCbor.key(cose,3),"alg")!=-7||num(MiniCbor.key(cose,1),"kty")!=2)throw new Failure("REQUIRES_ES256");byte[] x=bytes(MiniCbor.key(cose,-2),"x"),y=bytes(MiniCbor.key(cose,-3),"y");pub(x,y);
    // Replace old credentials only after verifying complete registration structure.
    if(!c.getSharedPreferences(PREF,0).edit().putString("cred",b64(cred)).putString("x",b64(x)).putString("y",b64(y)).putLong("counter",0).putBoolean("verified",false).putString("mode",desired+(usePin?"+PIN":"-OHNE_PIN")).putString("alias",alias.trim()).putString("userName",username.trim()).putString("rpId",registrationRpId).putString("rpName",registrationRpName).commit())throw new Failure("STORE_FAILED");
    event.log("PASSKEY_ANGELEGT; TEST_MIT_GETASSERTION_NOETIG");
  }
 }
 static void verify(Context c,Tag tag,Event event)throws Exception{
  android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);if(!paired(c))throw new Failure("KEIN_FIDO_CREDENTIAL");final String enrolledRpId=rpId(c);byte[] cred=un64(p.getString("cred","")),x=un64(p.getString("x","")),y=un64(p.getString("y","")),challenge=random(32);
  try(Session s=new Session(tag)){
    Map<?,?> result=s.cmd(2,m(1,enrolledRpId,2,challenge,3,Arrays.asList(m("id",cred,"type","public-key"))),event);byte[] auth=bytes(MiniCbor.key(result,2),"authData"),signature=bytes(MiniCbor.key(result,3),"signature");
    if(auth.length<37||!MessageDigest.isEqual(sub(auth,0,32),sha(enrolledRpId.getBytes(StandardCharsets.UTF_8)))||(auth[32]&1)==0)throw new Failure("ASSERTION_INVALID_AUTHDATA");
    Object desc=MiniCbor.key(result,1);if(desc instanceof Map){byte[] echoed=bytes(((Map<?,?>)desc).get("id"),"credentialId");if(!MessageDigest.isEqual(echoed,cred))throw new Failure("CREDENTIAL_MISMATCH");}
    Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(pub(x,y));verifier.update(join(auth,challenge));if(!verifier.verify(signature))throw new Failure("ES256_SIGNATURE_INVALID");
    long counter=((long)(auth[33]&255)<<24)|((long)(auth[34]&255)<<16)|((long)(auth[35]&255)<<8)|(auth[36]&255);if(p.getBoolean("verified",false)&&counter!=0&&counter<=p.getLong("counter",0))throw new Failure("SIGN_COUNTER_ROLLBACK");
    p.edit().putBoolean("verified",true).putLong("counter",counter).apply();event.log("FIDO_SIGNATUR_GUELTIG counter="+counter);
  }
 }
 static boolean verified(Context c){return c.getSharedPreferences(PREF,0).getBoolean("verified",false);}
 static String mode(Context c){return c.getSharedPreferences(PREF,0).getString("mode","KEINER");}
 static void clear(Context c){c.getSharedPreferences(PREF,0).edit().clear().apply();}
}
