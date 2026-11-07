package de.nethunter.flippernfc;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Pure crypto/parser for the separate, non-UV hmac-secret boot credential. */
final class BootCrypto {
 static final String RP="boot.kalilock.local";
 private static final byte[] DOMAIN="KaliLock direct boot v1; android-user=0; uv=0".getBytes(StandardCharsets.UTF_8);
 static final class Credential {
  final byte[] id,x,y;final String rp;
  Credential(byte[] id,byte[] x,byte[] y){this(RP,id,x,y);}
  Credential(String rp,byte[] id,byte[] x,byte[] y){if(rp==null||rp.isEmpty()||rp.length()>253)throw new IllegalArgumentException("INVALID_RP");this.rp=rp;this.id=id.clone();this.x=x.clone();this.y=y.clone();}
 }
 static byte[] sha(byte[] b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
 static byte[] random(int n){byte[] b=new byte[n];new SecureRandom().nextBytes(b);return b;}
 static byte[] bytes(Object o,int size)throws Exception{
  if(!(o instanceof byte[])||(size>=0&&((byte[])o).length!=size))throw new GeneralSecurityException("BOOT_INVALID_BYTES");
  return (byte[])o;
 }
 static byte[] join(byte[]... parts){int n=0;for(byte[] p:parts)n+=p.length;byte[] r=new byte[n];int pos=0;for(byte[] p:parts){System.arraycopy(p,0,r,pos,p.length);pos+=p.length;}return r;}
 private static void require(boolean ok,String message)throws GeneralSecurityException{if(!ok)throw new GeneralSecurityException(message);}
 static PublicKey publicKey(Credential c)throws Exception{
  require(c.x.length==32&&c.y.length==32,"BOOT_INVALID_PUBLIC_KEY");
  AlgorithmParameters parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));
  return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(new java.math.BigInteger(1,c.x),new java.math.BigInteger(1,c.y)),parameters.getParameterSpec(ECParameterSpec.class)));
 }
 private static int flags(byte[] auth,String rp)throws Exception{
  require(auth.length>=37&&MessageDigest.isEqual(Arrays.copyOf(auth,32),sha(rp.getBytes(StandardCharsets.UTF_8))),"BOOT_RP_HASH_MISMATCH");
  int f=auth[32]&255;
  require((f&1)!=0&&(f&4)==0,"BOOT_REQUIRES_USER_PRESENCE_WITHOUT_UV");
  return f;
 }
 private static Map<?,?> extensions(byte[] auth,int offset)throws Exception{
  MiniCbor.Result ext=MiniCbor.at(auth,offset);
  require(ext.value instanceof Map&&ext.next==auth.length,"BOOT_INVALID_EXTENSIONS");
  return (Map<?,?>)ext.value;
 }
 static Credential registration(byte[] auth)throws Exception{
  require((flags(auth,RP)&0xc0)==0xc0&&auth.length>=56,"BOOT_REGISTRATION_FLAGS");
  int n=((auth[53]&255)<<8)|(auth[54]&255);
  require(n>0&&n<=1024&&55+n<auth.length,"BOOT_CREDENTIAL_LENGTH");
  byte[] id=Arrays.copyOfRange(auth,55,55+n);
  MiniCbor.Result result=MiniCbor.at(auth,55+n);
  require(result.value instanceof Map,"BOOT_INVALID_COSE");Map<?,?> cose=(Map<?,?>)result.value;
  require(Long.valueOf(2).equals(cose.get(1L))&&Long.valueOf(-7).equals(cose.get(3L))&&Long.valueOf(1).equals(cose.get(-1L)),"BOOT_REQUIRES_ES256_P256");
  Credential c=new Credential(id,bytes(cose.get(-2L),32),bytes(cose.get(-3L),32));publicKey(c);
  require(Boolean.TRUE.equals(extensions(auth,result.next).get("hmac-secret")),"BOOT_HMAC_SECRET_NOT_CREATED");
  return c;
 }
 static long counter(byte[] auth){return ((long)(auth[33]&255)<<24)|((long)(auth[34]&255)<<16)|((long)(auth[35]&255)<<8)|(auth[36]&255);}
 static byte[] assertion(Credential c,byte[] challenge,byte[] auth,byte[] signature,long previous)throws Exception{
  require(challenge.length==32,"BOOT_INVALID_CHALLENGE");
  int f=flags(auth,c.rp);require((f&0x80)!=0&&(f&0x40)==0,"BOOT_ASSERTION_FLAGS");
  Signature v=Signature.getInstance("SHA256withECDSA");v.initVerify(publicKey(c));v.update(join(auth,challenge));
  require(v.verify(signature),"BOOT_SIGNATURE_INVALID");
  long count=counter(auth);require((count==0&&previous==0)||count>previous,"BOOT_COUNTER_ROLLBACK");
  return bytes(extensions(auth,37).get("hmac-secret"),32);
 }
 static byte[] aad(Credential c,byte[] salt)throws Exception{
  require(salt.length==32,"BOOT_INVALID_SALT");
  return join(DOMAIN,sha(c.rp.getBytes(StandardCharsets.UTF_8)),sha(c.id),c.x,c.y,salt);
 }
 private static byte[] key(byte[] hmacOutput,byte[] binding)throws Exception{
  require(hmacOutput.length==32,"BOOT_INVALID_HMAC_OUTPUT");
  // HKDF-Extract(salt=SHA256(binding)), HKDF-Expand(info=domain, L=32).
  Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(sha(binding),"HmacSHA256"));byte[] prk=m.doFinal(hmacOutput);
  try{m.init(new SecretKeySpec(prk,"HmacSHA256"));return m.doFinal(join(DOMAIN,new byte[]{1}));}finally{Arrays.fill(prk,(byte)0);}
 }
 static byte[] crypt(int mode,Credential c,byte[] salt,byte[] output,byte[] nonce,byte[] input)throws Exception{
  require(nonce.length==12,"BOOT_INVALID_NONCE");byte[] binding=aad(c,salt),k=key(output,binding);
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(k,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(binding);return cipher.doFinal(input);}
  finally{Arrays.fill(k,(byte)0);}
 }
}
