package de.nethunter.flippernfc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** CTAP NFC framing, keeping large commands within small ISO-DEP exchanges. */
final class NfcApdu {
 private static final int CHAIN_CHUNK=180;

 static List<byte[]> ctapMessages(byte[] payload){
  if(payload==null||payload.length==0||payload.length>65535)throw new IllegalArgumentException("INVALID_CTAP_MESSAGE_LENGTH");
  if(payload.length>255){
   List<byte[]> frames=new ArrayList<>();
   for(int offset=0;offset<payload.length;offset+=CHAIN_CHUNK){
    int size=Math.min(CHAIN_CHUNK,payload.length-offset);
    boolean more=offset+size<payload.length;
    // Intermediate frames have no Le. Only the final frame requests keepalives.
    byte[] frame=new byte[5+size+(more?0:1)];
    frame[0]=(byte)(more?0x90:0x80);
    frame[1]=0x10;
    frame[2]=(byte)(more?0:0x80);
    frame[4]=(byte)size;
    System.arraycopy(payload,offset,frame,5,size);
    frames.add(frame);
   }
   return frames;
  }

  // Keep the extended encoding used by the working ordinary unlock path.
  byte[] apdu=new byte[9+payload.length];
  apdu[0]=(byte)0x80;apdu[1]=0x10;apdu[2]=(byte)0x80;apdu[3]=0;apdu[4]=0;apdu[5]=(byte)(payload.length>>8);apdu[6]=(byte)payload.length;
  System.arraycopy(payload,0,apdu,7,payload.length);
  return Collections.singletonList(apdu);
 }
}
