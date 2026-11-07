package de.nethunter.flippernfc;
import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class BootCryptoTest {
 interface Checked {void run() throws Exception;}
 static int passed;
 static void check(boolean b){if(!b)throw new AssertionError();passed++;}
 static void rejects(Checked r)throws Exception {try{r.run();}catch(java.io.IOException|GeneralSecurityException|IllegalArgumentException ex){passed++;return;}throw new AssertionError("Malformed input accepted");}
 static byte[] hex(String s){return HexFormat.of().parseHex(s);}
 static byte[] header(int flags,long counter)throws Exception {byte[] b=new byte[37];System.arraycopy(BootCrypto.sha(BootCrypto.RP.getBytes(StandardCharsets.UTF_8)),0,b,0,32);b[32]=(byte)flags;ByteBuffer.wrap(b,33,4).putInt((int)counter);return b;}
 static byte[] sign(KeyPair p,byte[] data,byte[] challenge)throws Exception {Signature s=Signature.getInstance("SHA256withECDSA");s.initSign(p.getPrivate());s.update(BootCrypto.join(data,challenge));return s.sign();}
 public static void main(String[] args)throws Exception {
  rejects(()->MiniCbor.decode(hex("a201010102")));
  rejects(()->MiniCbor.decode(hex("1bfffffffffffffffe")));
  rejects(()->MiniCbor.decode(hex("a16161f501f5")));
  rejects(()->MiniCbor.decode(hex("61ff")));
  rejects(()->MiniCbor.decode(hex("1817")));
  rejects(()->MiniCbor.decode(hex("5bffffffffffffffff")));
  rejects(()->MiniCbor.decode(hex("a16161f56161f4")));
  Map<Object,Object> unordered=new LinkedHashMap<>();unordered.put("a",true);unordered.put(1,true);
  check(Arrays.equals(MiniCbor.encode(unordered),hex("a201f56161f5")));
  byte[] shared=hex("23775201799b2234a18e8071e409cec80d42632fe77534180afdc533c9b76f81");
  byte[] salt=hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
  byte[] enc=CtapFido.Pins.aes(Cipher.ENCRYPT_MODE,shared,salt);
  check(Arrays.equals(enc,hex("0c4327e20c8f293cc471a511e1afd1dbe165812aa9f3b624eb6664dca8e2b08a")));
  check(Arrays.equals(Arrays.copyOf(CtapFido.Pins.hmac(shared,enc),16),hex("a5ca37d98ac7537931b0cd3b7aed4ad0")));
  byte[] output=hex("72769de7f20d73ce35815c4a63afb067ed6ba2829a78ccbb6818cf51a97b4b14");
  check(Arrays.equals(CtapFido.Pins.aes(Cipher.ENCRYPT_MODE,shared,output),hex("289109f6ebb50199aab1e6c9c0d65ce95d25431b0915039a21488330e555afde")));
  KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));KeyPair kp=g.generateKeyPair();ECPublicKey pub=(ECPublicKey)kp.getPublic();
  BootCrypto.Credential c=new BootCrypto.Credential(new byte[]{1,2,3},CtapFido.Pins.fixed(pub.getW().getAffineX()),CtapFido.Pins.fixed(pub.getW().getAffineY()));
  byte[] cose=MiniCbor.encode(CtapFido.m(1,2,3,-7,-1,1,-2,c.x,-3,c.y));
  byte[] registration=BootCrypto.join(header(0xc1,0),new byte[16],new byte[]{0,3},c.id,cose,MiniCbor.encode(CtapFido.m("hmac-secret",true)));
  check(Arrays.equals(BootCrypto.registration(registration).id,c.id));
  byte[] unsupported=BootCrypto.join(header(0xc1,0),new byte[16],new byte[]{0,3},c.id,cose,MiniCbor.encode(CtapFido.m("hmac-secret",false)));
  rejects(()->BootCrypto.registration(unsupported));
  byte[] nonce=BootCrypto.random(12),plain="synthetic test password".getBytes(StandardCharsets.UTF_8);
  byte[] wrapped=BootCrypto.crypt(Cipher.ENCRYPT_MODE,c,salt,output,nonce,plain);
  check(Arrays.equals(plain,BootCrypto.crypt(Cipher.DECRYPT_MODE,c,salt,output,nonce,wrapped)));
  byte[] wrong=output.clone();wrong[0]^=1;
  rejects(()->BootCrypto.crypt(Cipher.DECRYPT_MODE,c,salt,wrong,nonce,wrapped));
  byte[] changed=wrapped.clone();changed[0]^=1;
  rejects(()->BootCrypto.crypt(Cipher.DECRYPT_MODE,c,salt,output,nonce,changed));
  byte[] otherSalt=salt.clone();otherSalt[0]^=1;
  rejects(()->BootCrypto.crypt(Cipher.DECRYPT_MODE,c,otherSalt,output,nonce,wrapped));
  BootCrypto.Credential other=new BootCrypto.Credential(new byte[]{9},c.x,c.y);
  rejects(()->BootCrypto.crypt(Cipher.DECRYPT_MODE,other,salt,output,nonce,wrapped));
  BootCrypto.Credential otherRp=new BootCrypto.Credential("different.example",c.id,c.x,c.y);
  rejects(()->BootCrypto.crypt(Cipher.DECRYPT_MODE,otherRp,salt,output,nonce,wrapped));
  byte[] challenge=BootCrypto.random(32),auth=BootCrypto.join(header(0x81,6),MiniCbor.encode(CtapFido.m("hmac-secret",enc))),signature=sign(kp,auth,challenge);
  check(Arrays.equals(BootCrypto.assertion(c,challenge,auth,signature,5),enc));
  rejects(()->BootCrypto.assertion(c,challenge,auth,signature,6));
  rejects(()->BootCrypto.assertion(otherRp,challenge,auth,signature,5));
  rejects(()->BootCrypto.assertion(c,challenge,auth,signature,7));
  rejects(()->BootCrypto.assertion(c,BootCrypto.random(32),auth,signature,5));
  byte[] tampered=auth.clone();tampered[tampered.length-1]^=1;
  rejects(()->BootCrypto.assertion(c,challenge,tampered,signature,5));
  for(int flags:new int[]{0x80,0x85,0xc1,0x01}){
   byte[] a=BootCrypto.join(header(flags,7),MiniCbor.encode(CtapFido.m("hmac-secret",enc))),sig=sign(kp,a,challenge);
   rejects(()->BootCrypto.assertion(c,challenge,a,sig,6));
  }
  byte[] zero=BootCrypto.join(header(0x81,0),MiniCbor.encode(CtapFido.m("hmac-secret",enc))),zeroSig=sign(kp,zero,challenge);
  check(Arrays.equals(BootCrypto.assertion(c,challenge,zero,zeroSig,0),enc));
  rejects(()->BootCrypto.assertion(c,challenge,zero,zeroSig,1));
  byte[] bool=BootCrypto.join(header(0x81,7),MiniCbor.encode(CtapFido.m("hmac-secret",true))),boolSig=sign(kp,bool,challenge);
  rejects(()->BootCrypto.assertion(c,challenge,bool,boolSig,6));
  Random r=new Random(42);
  for(int i=0;i<2000;i++){byte[] fuzz=new byte[1+r.nextInt(80)];r.nextBytes(fuzz);try{MiniCbor.decode(fuzz);}catch(java.io.IOException expected){}}
  System.out.println("PASS "+passed+" checks + 2000 CBOR malformed-input samples");
 }
}
