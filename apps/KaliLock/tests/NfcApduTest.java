package de.nethunter.flippernfc;
import java.util.*;import java.io.*;
public final class NfcApduTest {
 static int passed;static void check(boolean b){if(!b)throw new AssertionError();passed++;}
 public static void main(String[] args)throws Exception{
  for(int len:new int[]{1,32,179,180,181,255,256,267,512,1024,65535}){
   byte[] input=new byte[len];new Random(len).nextBytes(input);List<byte[]> frames=NfcApdu.ctapMessages(input);ByteArrayOutputStream reconstructed=new ByteArrayOutputStream();
   for(int i=0;i<frames.size();i++){
    byte[] f=frames.get(i);boolean more=i+1<frames.size();int n;
    if(len<=255){
     check(frames.size()==1);
     n=((f[5]&255)<<8)|(f[6]&255);
     check(f.length==9+n&&f[4]==0&&f[f.length-1]==0&&f[f.length-2]==0);
     reconstructed.write(f,7,n);
    }else{
     n=f[4]&255;
     check(n>0&&n<=180);
     check(f.length==5+n+(more?0:1));
     if(!more)check(f[f.length-1]==0);
     reconstructed.write(f,5,n);
    }
    check((f[0]&255)==(more?0x90:0x80));
    check(f[1]==0x10&&(f[2]&255)==(more?0:0x80)&&f[3]==0);
   }
   check(Arrays.equals(input,reconstructed.toByteArray()));
  }
  for(byte[] invalid:new byte[][]{null,new byte[0],new byte[65536]}){try{NfcApdu.ctapMessages(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){passed++;}}
  Map<Object,Object> pub=CtapFido.m(1,2,3,-25,-1,1,-2,new byte[32],-3,new byte[32]);
  byte[] hmac=CtapFido.join(new byte[]{2},MiniCbor.encode(CtapFido.m(1,"flipper-nfc.local",2,new byte[32],3,Arrays.asList(CtapFido.m("id",new byte[32],"type","public-key")),4,CtapFido.m("hmac-secret",CtapFido.m(1,pub,2,new byte[32],3,new byte[16],4,1)),5,CtapFido.m("up",true))));
  check(hmac.length==267);
  List<byte[]> frames=NfcApdu.ctapMessages(hmac);
  check(frames.size()==2);
  check(Arrays.equals(Arrays.copyOf(frames.get(0),5),new byte[]{(byte)0x90,0x10,0,0,(byte)180}));
  check(Arrays.equals(Arrays.copyOf(frames.get(1),5),new byte[]{(byte)0x80,0x10,(byte)0x80,0,87}));
  System.out.println("PASS "+passed+" APDU framing/reassembly/boundary checks; HMAC fixture bytes="+hmac.length);
 }
}
