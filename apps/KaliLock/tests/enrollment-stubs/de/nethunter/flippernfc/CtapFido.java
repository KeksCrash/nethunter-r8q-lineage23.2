package de.nethunter.flippernfc;
import java.util.*;import java.security.*;import java.security.spec.*;import java.security.interfaces.*;import javax.crypto.*;import javax.crypto.spec.*;import android.nfc.Tag;
final class CtapFido {
 static final String PREF="fido_credentials",DEFAULT_RP="flipper-nfc.local";
 interface Event{void log(String s);}
 static int creates,derivations;static long counter;static boolean failCreate,failDerive;
 static final byte[] SHARED=new byte[32],OUTPUT=new byte[32];
 static final KeyPair KEY;static final BootCrypto.Credential CRED;
 static {try{KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));KEY=g.generateKeyPair();ECPublicKey k=(ECPublicKey)KEY.getPublic();CRED=new BootCrypto.Credential(new byte[]{1,2,3},fixed(k.getW().getAffineX()),fixed(k.getW().getAffineY()));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static byte[] fixed(java.math.BigInteger n){byte[] a=n.toByteArray(),b=new byte[32];int skip=a.length==33?1:0;System.arraycopy(a,skip,b,32-a.length+skip,a.length-skip);return b;}
 static Map<Object,Object> m(Object... p){Map<Object,Object> r=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)r.put(p[i] instanceof Number?((Number)p[i]).longValue():p[i],p[i+1]);return r;}
 static String b64(byte[] b){return Base64.getEncoder().encodeToString(b);}
 static byte[] un64(String s){return Base64.getDecoder().decode(s);}
 static byte[] header(int flags)throws Exception{return header(flags,BootCrypto.RP);}
 static byte[] header(int flags,String rp)throws Exception{byte[] a=new byte[37];System.arraycopy(BootCrypto.sha(rp.getBytes(java.nio.charset.StandardCharsets.UTF_8)),0,a,0,32);a[32]=(byte)flags;java.nio.ByteBuffer.wrap(a,33,4).putInt((int)counter);return a;}
 static final class Session implements AutoCloseable {
  Session(Tag t){}
  Map<?,?> cmd(int command,Map<Object,Object> params)throws Exception{return cmd(command,params,s->{});}
  Map<?,?> cmd(int command,Map<Object,Object> params,Event event)throws Exception{
   if(command==4)return m(2,Arrays.asList("hmac-secret"),6,Arrays.asList(1L),4,m("alwaysUv",false));
   if(command==1){creates++;if(failCreate)throw new java.io.IOException("Synthetic response lost after creation");byte[] a=BootCrypto.join(header(0xc1),new byte[16],new byte[]{0,3},CRED.id,MiniCbor.encode(m(1,2,3,-7,-1,1,-2,CRED.x,-3,CRED.y)),MiniCbor.encode(m("hmac-secret",true)));return m(2,a);}
   if(command==2){derivations++;if(failDerive)throw new java.io.IOException("Synthetic tag lost");counter++;byte[] auth=BootCrypto.join(header(0x81,(String)params.get(1L)),MiniCbor.encode(m("hmac-secret",Pins.aes(Cipher.ENCRYPT_MODE,SHARED,OUTPUT))));Signature sig=Signature.getInstance("SHA256withECDSA");sig.initSign(KEY.getPrivate());sig.update(BootCrypto.join(auth,(byte[])params.get(2L)));return m(1,m("type","public-key","id",CRED.id),2,auth,3,sig.sign());}
   throw new AssertionError("Unexpected command");
  }
  public void close(){}
 }
 static final class Pins{
  final byte[] secret=SHARED.clone();final Map<Object,Object> client=m();
  static Pins agree(Session s){return new Pins();}
  static byte[] aes(int mode,byte[] key,byte[] input)throws Exception{Cipher c=Cipher.getInstance("AES/CBC/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new IvParameterSpec(new byte[16]));return c.doFinal(input);}
  static byte[] hmac(byte[] key,byte[] input)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(input);}
 }
}
