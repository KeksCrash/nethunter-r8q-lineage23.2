package de.nethunter.flippernfc;
import android.nfc.Tag;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
final class TagIdentity {
 private static final String PREF="tagpair";
 static String fingerprint(Context c, Tag tag)throws Exception {
  if(tag==null||tag.getId()==null||tag.getId().length==0) throw new IllegalArgumentException("Tag ohne UID");
  SharedPreferences p=c.getSharedPreferences(PREF,0);
  String s=p.getString("salt",null); if(s==null){byte[] b=new byte[32];new SecureRandom().nextBytes(b);s=Base64.encodeToString(b,Base64.NO_WRAP);if(!p.edit().putString("salt",s).commit())throw new IllegalStateException("Salt nicht speicherbar");}
  MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(Base64.decode(s,Base64.NO_WRAP));d.update(tag.getId());return Base64.encodeToString(d.digest(),Base64.NO_WRAP);
 }
 static boolean paired(Context c){return c.getSharedPreferences(PREF,0).contains("fingerprint");}
 static void pair(Context c,Tag tag)throws Exception {String fp=fingerprint(c,tag);if(!c.getSharedPreferences(PREF,0).edit().putString("fingerprint",fp).commit())throw new IllegalStateException("Speichern fehlgeschlagen");}
 static boolean matches(Context c,Tag tag)throws Exception {SharedPreferences p=c.getSharedPreferences(PREF,0);String expected=p.getString("fingerprint",null);return expected!=null&&MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),fingerprint(c,tag).getBytes(StandardCharsets.US_ASCII));}
 static void clear(Context c){c.getSharedPreferences(PREF,0).edit().clear().apply();}
 static String uid(Tag tag){StringBuilder b=new StringBuilder();for(byte x:tag.getId())b.append(String.format(java.util.Locale.ROOT,"%02X",x&255));return b.toString();}
}
