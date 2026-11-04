package de.nethunter.flippernfc;
import android.app.*;import android.os.*;import android.content.*;import android.nfc.*;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;
/** Numbered single-purpose workflow; NFC reader mode only active for the selected action. */
public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
 private NfcAdapter adapter;private TextView status;private volatile int pending=0;private volatile String pendingPin="";private volatile String pendingAlias="Mein ZeroFIDO-Schlüssel",pendingUsername="S20 FE",pendingRpId="flipper-nfc.local",pendingRpName="S20 NFC";private volatile boolean activeNfc=false;
 private static final int TAG_LEARN=1,TAG_TEST=2,FIDO_INFO=3,PIN_INFO=4,FIDO_21_NONE=5,FIDO_21_PIN=6,FIDO_20_NONE=7,FIDO_20_PIN=8,FIDO_VERIFY=9;
 @Override public void onCreate(Bundle b){super.onCreate(b);CrashRecorder.install(this);adapter=NfcAdapter.getDefaultAdapter(this);draw();}
 private void draw(){ScrollView scroll=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(24,22,24,30);l.setBackgroundColor(Color.rgb(6,14,23));scroll.addView(l);setContentView(scroll);
  TextView brand=new TextView(this);brand.setText("🔒  KALI LOCK  /  FLIPPER NFC");brand.setTextSize(23);brand.setTextColor(Color.rgb(37,197,255));brand.setTypeface(null,1);brand.setPadding(0,12,0,14);l.addView(brand);
  heading(l,"KALI LOCK · FLIPPER NFC 2.3");label(l,"Ein Gerät, zwei Verfahren: ZeroFIDO (kryptografisch) und NFC-Tag (nur Kennung). Optionale Root-Passwort-Automatik ausschließlich nach gültiger FIDO-Signatur; für alle Sonderzeichen IME aktivieren (16).");
  heading(l,"A · Vorbereitung / Diagnose");button(l,"01 · ZeroFIDO-Informationen lesen",()->request(FIDO_INFO));button(l,"02 · ZeroFIDO-PIN-Status + Versuche",()->request(PIN_INFO));
  heading(l,"B · FIDO-Zugangsschlüssel erstellen und benennen");button(l,"02a · RP-Bezeichner / RP-ID verwalten",()->rpDialog());label(l,"Vorher ZeroFIDO auf dem Flipper starten. FIDO-Version dort einstellen. Für PIN-lose Varianten keine PIN notwendig. Credential ist lokal/nicht auffindbar (rk=false).");
  button(l,"03 · FIDO 2.1 OHNE PIN erstellen",()->request(FIDO_21_NONE));button(l,"04 · FIDO 2.1 MIT PIN erstellen",()->request(FIDO_21_PIN));button(l,"05 · FIDO 2.0 OHNE PIN versuchen",()->request(FIDO_20_NONE));button(l,"06 · FIDO 2.0 MIT PIN erstellen",()->request(FIDO_20_PIN));button(l,"07 · FIDO-Signatur testen (kein Flipper-PIN nötig)",()->request(FIDO_VERIFY));
  button(l,"07a · Registrierten FIDO-Schlüssel UMBENENNEN",()->renameDialog());
  heading(l,"C · Alternative / einfacher NFC-Tag");label(l,"Flipper: NFC → Saved → Tag → Emulate. UID-basierte Erkennung ist kopierbar und darf nicht alleine Android entsperren.");button(l,"08 · NFC-Tag anlernen",()->request(TAG_LEARN));button(l,"09 · NFC-Tag testen",()->request(TAG_TEST));
  heading(l,"D · Sperrbildschirm-NFC-Erkennung");button(l,"10 · NFC-Erkennungsdienst starten",()->{try{startForegroundService(new Intent(this,NfcWatchService.class));record("LOCKSCREEN_LISTENER_STARTED; Passwort-Automatik optional");}catch(Exception e){record("SERVICE_FAILED: "+e.getMessage());}});button(l,"11 · NFC-Erkennungsdienst stoppen",()->{stopService(new Intent(this,NfcWatchService.class));record("LOCKSCREEN_LISTENER_STOPPED");});
  Switch vibration=new Switch(this);
  vibration.setText("Bei NFC-Erkennung vibrieren");
  vibration.setTextColor(Color.WHITE);
  vibration.setChecked(NfcFeedback.enabled(this));
  vibration.setPadding(4,12,4,12);
  vibration.setOnCheckedChangeListener((buttonView,isChecked)->{NfcFeedback.setEnabled(this,isChecked);record("NFC_VIBRATION_"+(isChecked?"ON":"OFF"));});
  l.addView(vibration);
  label(l,"Die Vibration bestätigt nur die NFC-Erkennung, nicht die erfolgreiche FIDO-Prüfung. Bei ausgeschaltetem Display hängt die Erkennung von der NFC-Firmware/LineageOS ab; die App kann einen Tag nicht erkennen, solange Android NFC-Polling deaktiviert.");
  heading(l,"E · Android-Passwort-Automatik (nur nach FIDO-Signatur)");label(l,"12 · Android-Passwort verschlüsselt speichern; 13 · nach erfolgreichem FIDO-Signaturtest aktivieren; 14 · deaktivieren; 15 · Passwort entfernen. Weder NFC-UID noch PIN allein lösen den Vorgang aus. Sicherheitshinweis: Root kann auf PIN im Prozess zugreifen. Funktion experimentell.");
  button(l,"12 · Android-Passwort (Text + Sonderzeichen) speichern",()->{final EditText secret=new EditText(this);secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);new AlertDialog.Builder(this).setTitle("Android-Bildschirmsperr-Passwort").setMessage("Groß-/Kleinbuchstaben, Zahlen, Sonderzeichen und Unicode können verschlüsselt gespeichert werden. Die Eingabe erfolgt über die Kali Lock IME; dazu Option 16 aktivieren. Nach Speichern bleibt die Automatik aus.").setView(secret).setNegativeButton("Abbrechen",(d,w)->secret.setText("")).setPositiveButton("Passwort speichern",(d,w)->{String value=secret.getText().toString();secret.setText("");try{PinAuto.save(this,value);record("ANDROID_PASSWORD_ENCRYPTED; AUTO=OFF");}catch(Exception e){record("PASSWORD_SAVE_FAILED "+e.getMessage());}}).show();});
  button(l,"13 · Automatische Passwort-Eingabe AKTIVIEREN",()->confirmEnable());
  button(l,"14 · Automatische Passwort-Eingabe DEAKTIVIEREN",()->{PinAuto.toggle(this,false);record("AUTO_PIN_DISABLED");});
  button(l,"15 · Verschlüsseltes Android-Passwort LÖSCHEN",()->confirm("Gespeichertes Android-Passwort unwiderruflich löschen?",()->{PinAuto.clear(this);record("ANDROID_PASSWORD_DELETED");}));
  button(l,"16 · Kali Lock Passwort-Tastatur EINRICHTEN",()->new AlertDialog.Builder(this)
    .setTitle("Kali Lock Passwort-Tastatur (experimentell)")
    .setMessage("Für alle Buchstaben und Sonderzeichen muss Android den Text über eine IME statt shell input übernehmen. Diese spezielle Tastatur kann kurzzeitig zur Standardtastatur werden. Anschließend deine normale Tastatur in Einstellungen → System → Tastatur wieder einstellen. Die Aktivierung benötigt Root. Jetzt aktivieren?")
    .setNegativeButton("Abbrechen",null)
    .setPositiveButton("Tastatur aktivieren",(d,w)->new Thread(()->{
      try{String component="de.nethunter.flipperunified/de.nethunter.flippernfc.LockPasswordIme";
       java.lang.Process proc=new ProcessBuilder("su","-c","ime enable "+component+"; ime set "+component).redirectErrorStream(true).start();
       int code=proc.waitFor();record("LOCK_IME_ENABLE_EXIT="+code+"; pruefe Standardtastatur");
      }catch(Exception ex){record("LOCK_IME_ENABLE_FAILED "+ex.getMessage());}
    }).start()).show());
  heading(l,"F · Verwaltung");button(l,"17 · Gespeicherten FIDO-Schlüssel entfernen",()->confirm("Nur lokalen gespeicherten öffentlichen Schlüssel löschen? Der Flipper wird nicht zurückgesetzt.",()->{CtapFido.clear(this);record("LOCAL_FIDO_DELETED");}));button(l,"18 · Angelernten NFC-Tag löschen",()->confirm("NFC-Tag-Kennung löschen?",()->{TagIdentity.clear(this);record("LOCAL_TAG_DELETED");}));button(l,"19 · Diagnose anzeigen",()->show("Status aktualisiert; Sperrbildschirm-Logs: LOCKED_READER_FAILED und LOCKED_FIDO_AUTH_FAILED samt Ursachen ansehen"));
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(13);status.setPadding(2,18,2,6);l.addView(status);show("");
 }
 private void confirmEnable(){if(!CtapFido.verified(this)){record("AUTO_PIN_BLOCKIERT: Erst FIDO-Registrierung + 07 Signaturprüfung erfolgreich abschließen");return;}if(!PinAuto.configured(this)){record("AUTO_PIN_BLOCKIERT: Erst Android-Passwort mit 12 speichern");return;}new AlertDialog.Builder(this).setTitle("Experimentelle Passwort-Automatik").setMessage("Bei gültiger neuer FIDO-Signatur kann die App mit Root deine Android-Passwort eingeben. Kein Schutz vor Root-Malware; funktioniert möglicherweise nicht am Sperrbildschirm. Nicht auf NFC-UID-Basis. Wirklich einschalten?").setNegativeButton("Abbrechen",null).setPositiveButton("AKTIVIEREN",(d,w)->{try{PinAuto.toggle(this,true);record("AUTO_PIN_ENABLED (EXPERIMENTAL)");}catch(Exception e){record("AUTO_PIN_ENABLE_FAILED "+e.getMessage());}}).show();}
 private void heading(LinearLayout l,String title){TextView t=new TextView(this);t.setText(title);t.setTextSize(19);t.setTextColor(Color.rgb(37,197,255));t.setPadding(0,22,0,9);l.addView(t);}
 private void label(LinearLayout l,String message){TextView v=new TextView(this);v.setText(message);v.setTextColor(Color.LTGRAY);v.setPadding(0,4,0,8);l.addView(v);}
 private void button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(15,62,87)));b.setOnClickListener(v->action.run());l.addView(b);}
 private void confirm(String text,Runnable run){new AlertDialog.Builder(this).setMessage(text).setPositiveButton("Löschen",(d,i)->run.run()).setNegativeButton("Abbrechen",null).show();}
 private void record(String msg){try{NfcWatchService.log(this,msg);}catch(Throwable ignored){}runOnUiThread(()->{try{show(msg);}catch(Throwable ignored){}});}
 private void show(String msg){if(status!=null){status.setText("Letzter Prozessfehler: "+CrashRecorder.lastCrash(this)+"\nFIDO: "+CtapFido.mode(this)+" / Signatur: "+(CtapFido.verified(this)?"GUELTIG":"NICHT BESTAETIGT")+"\nNFC-Tag: "+(TagIdentity.paired(this)?"ANGELERNT":"KEINER")+"\nName: "+CtapFido.displayAlias(this)+" (Benutzer: "+CtapFido.userName(this)+")"+"\nRP: "+CtapFido.rpName(this)+" ("+CtapFido.rpId(this)+")"+"\nNFC-Vibration: "+(NfcFeedback.enabled(this)?"AN":"AUS")+"\nAuto-Passwort: "+(PinAuto.enabled(this)?"AN (EXPERIMENTELL)":"AUS")+"\n"+msg+"\n\n"+getSharedPreferences("events",0).getString("history",""));}}
 private void reader(boolean enabled){if(adapter==null){record("NFC NICHT VERFUEGBAR");return;}try{if(enabled){adapter.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null);activeNfc=true;}else{if(activeNfc)adapter.disableReaderMode(this);activeNfc=false;}}catch(Throwable e){activeNfc=false;record("NFC_READER_ERROR "+e.getClass().getSimpleName()+" "+e.getMessage());}}
 private void renameDialog(){
  if(!CtapFido.paired(this)){record("KEIN_REGISTRIERTER_FIDO_SCHLUESSEL");return;}
  EditText name=new EditText(this);name.setSingleLine(true);name.setText(CtapFido.displayAlias(this));
  new AlertDialog.Builder(this).setTitle("Lokalen FIDO-Schlüsselnamen ändern")
   .setMessage("Nur der lokale Anzeigename wird geändert. Der bereits registrierte Flipper-Schlüssel bleibt erhalten.")
   .setView(name).setNegativeButton("Abbrechen",null)
   .setPositiveButton("Speichern",(d,w)->{try{CtapFido.rename(this,name.getText().toString());record("FIDO_ALIAS_UPDATED");}catch(Exception e){record("FIDO_ALIAS_ERROR "+e.getMessage());}}).show();
 }
 private void rpDialog(){
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(22,8,22,8);
  TextView note=new TextView(this);note.setText("RP-Anzeigename: Beim vorhandenen Schlüssel lokal umbenennbar.\nTechnische RP-ID: Für den vorhandenen Schlüssel kryptografisch fest gebunden. Eine neue RP-ID gilt ausschließlich für NEUE Registrierungen; aktueller Schlüssel bleibt unverändert.");layout.addView(note);
  EditText current=new EditText(this);current.setSingleLine(true);current.setHint("Anzeigename des vorhandenen RP");current.setText(CtapFido.rpName(this));layout.addView(current);
  EditText nextId=new EditText(this);nextId.setSingleLine(true);nextId.setHint("RP-ID für neue Registrierungen");nextId.setText(CtapFido.futureRpId(this));layout.addView(nextId);
  EditText nextName=new EditText(this);nextName.setSingleLine(true);nextName.setHint("RP-Anzeigename für neue Registrierungen");nextName.setText(CtapFido.futureRpName(this));layout.addView(nextName);
  new AlertDialog.Builder(this).setTitle("02a · RP-Verwaltung").setView(layout)
   .setNeutralButton("Nur aktuellen Namen ändern",(d,w)->{try{CtapFido.renameRp(this,current.getText().toString());record("RP_DISPLAY_RENAMED: Existing RP-ID unchanged");}catch(Exception ex){record("RP_RENAME_FAILED "+ex.getMessage());}})
   .setNegativeButton("Abbrechen",null)
   .setPositiveButton("Vorgaben für neue Schlüssel",(d,w)->{try{CtapFido.saveFutureRp(this,nextId.getText().toString(),nextName.getText().toString());record("NEXT_RP_DEFAULTS_SAVED; vorhandene Credential unverändert");}catch(Exception ex){record("RP_DEFAULTS_FAILED "+ex.getMessage());}}).show();
 }
 private void request(int mode){
  if(mode==FIDO_21_PIN||mode==FIDO_20_PIN||mode==FIDO_21_NONE||mode==FIDO_20_NONE){
   boolean withPin=mode==FIDO_21_PIN||mode==FIDO_20_PIN;
   LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,8,24,4);
   TextView help=new TextView(this);help.setText("Schlüsselname, Benutzername und RP für die NEUE Registrierung. Das Ändern der RP-ID erzeugt eine andere kryptografische Bindung; der bisherige Schlüssel kann nicht umgeschrieben werden.");layout.addView(help);
   EditText alias=new EditText(this);alias.setSingleLine(true);alias.setHint("Schlüsselname");alias.setText(CtapFido.displayAlias(this));layout.addView(alias);
   EditText user=new EditText(this);user.setSingleLine(true);user.setHint("Benutzername");user.setText(CtapFido.userName(this));layout.addView(user);
   EditText rpIdInput=new EditText(this);rpIdInput.setSingleLine(true);rpIdInput.setHint("RP-ID z.B. flipper-nfc.local");rpIdInput.setText(CtapFido.futureRpId(this));layout.addView(rpIdInput);
   EditText rpNameInput=new EditText(this);rpNameInput.setSingleLine(true);rpNameInput.setHint("RP-Anzeigename");rpNameInput.setText(CtapFido.futureRpName(this));layout.addView(rpNameInput);
   EditText secret=new EditText(this);
   if(withPin){secret.setHint("ZeroFIDO-PIN (nicht Android-Passwort)");secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);layout.addView(secret);}
   new AlertDialog.Builder(this).setTitle("FIDO " + ((mode==FIDO_21_PIN||mode==FIDO_21_NONE)?"2.1":"2.0")+(withPin?" mit PIN":" ohne PIN"))
    .setView(layout).setNegativeButton("Abbrechen",null).setPositiveButton("NFC starten",(d,w)->{
      String chosenAlias=alias.getText().toString().trim(),chosenUser=user.getText().toString().trim(),pin=withPin?secret.getText().toString():"";secret.setText("");
      if(chosenAlias.isEmpty()||chosenUser.isEmpty()||chosenAlias.length()>64||chosenUser.length()>64){record("NAME_FEHLT_ODER_ZU_LANG");return;}
      if(withPin&&pin.isEmpty()){record("PIN_FEHLT: Keine PIN-Versuche gestartet");return;}
      try{pendingRpId=CtapFido.validateRpId(rpIdInput.getText().toString());pendingRpName=CtapFido.validateRpName(rpNameInput.getText().toString());}
      catch(Exception ex){record("INVALID_RP: "+ex.getMessage());return;}
      pendingAlias=chosenAlias;pendingUsername=chosenUser;arm(mode,pin);
    }).show();
  }else arm(mode,"");
 }
 private void arm(int mode,String secret){pendingPin=secret;pending=mode;try{stopService(new Intent(this,NfcWatchService.class));}catch(Throwable e){record("SERVICE_STOP_ERROR "+e);}reader(true);record("NFC BEREIT: Flipper ans S20 halten (Aktion "+mode+")");}
 @Override public void onTagDiscovered(Tag tag){int action=pending;if(action==0)return;pending=0;final String secret=pendingPin;pendingPin="";final String alias=pendingAlias,username=pendingUsername,rpId=pendingRpId,rpName=pendingRpName;try{switch(action){
  case TAG_LEARN: TagIdentity.pair(this,tag);record("NFC_TAG_ANGELERNT");break;
  case TAG_TEST:record("NFC_TAG_TEST="+TagIdentity.matches(this,tag));break;
  case FIDO_INFO:CtapFido.info(tag,this::record);break;
  case PIN_INFO:CtapFido.pinStatus(tag,this::record);break;
  case FIDO_21_NONE:CtapFido.enroll(this,tag,true,false,"",alias,username,rpId,rpName,this::record);break;
  case FIDO_21_PIN:CtapFido.enroll(this,tag,true,true,secret,alias,username,rpId,rpName,this::record);break;
  case FIDO_20_NONE:CtapFido.enroll(this,tag,false,false,"",alias,username,rpId,rpName,this::record);break;
  case FIDO_20_PIN:CtapFido.enroll(this,tag,false,true,secret,alias,username,rpId,rpName,this::record);break;
  case FIDO_VERIFY:CtapFido.verify(this,tag,this::record);break;
  default:record("UNBEKANNTE_AKTION");
 }}catch(Throwable e){record("AKTION_"+action+"_FEHLER: "+e.getClass().getSimpleName()+" "+e.getMessage());}finally{runOnUiThread(()->{try{reader(false);show("");}catch(Throwable e){record("NFC_FINALLY_ERROR "+e.getClass().getSimpleName());}});}}
 @Override protected void onResume(){super.onResume();if(pending!=0)reader(true);if(status!=null)show("");}
 @Override protected void onPause(){reader(false);super.onPause();}
}
