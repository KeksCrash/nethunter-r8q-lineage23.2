package de.nethunter.flippernfc;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import android.app.*;import android.os.*;import android.content.*;import android.nfc.*;import android.graphics.Color;import android.text.InputType;import android.view.*;import android.widget.*;
/** Numbered single-purpose workflow; NFC reader mode only active for the selected action. */
public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
 private final ExecutorService nfcControl=Executors.newSingleThreadExecutor(),nfcWorker=Executors.newSingleThreadExecutor();
 private final SetupSequence setupSequence=new SetupSequence();
 private final AtomicBoolean actionBusy=new AtomicBoolean();
 private final AtomicInteger operationEpoch=new AtomicInteger(),readerEpoch=new AtomicInteger();
 private volatile boolean resumed,destroyed;
 private volatile Tag operationTag;private volatile Future<?> operationJob;
 private volatile String lastMessage="";
 private NfcAdapter adapter;private TextView status;private volatile int pending=0;private volatile String pendingPin="";private volatile String pendingAlias="Mein ZeroFIDO-Schlüssel",pendingUsername="S20 FE",pendingRpId="flipper-nfc.local",pendingRpName="S20 NFC";private volatile boolean activeNfc=false;
 private static final int TAG_LEARN=1,TAG_TEST=2,FIDO_INFO=3,PIN_INFO=4,FIDO_21_NONE=5,FIDO_21_PIN=6,FIDO_20_NONE=7,FIDO_20_PIN=8,FIDO_VERIFY=9,BOOT_ENROLL=10,BOOT_TEST=11,AUTO_SETUP=12;
 @Override public void onCreate(Bundle b){super.onCreate(b);CrashRecorder.install(this);DirectBootUnlock.migrateInterruptedLegacyCreation(this);adapter=NfcAdapter.getDefaultAdapter(this);draw();}
 private TextView overview;
 private int selectedPage=0;
 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(12),dp(18),dp(16));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.rgb(29,10,10));bg.setCornerRadius(dp(18));bg.setStroke(dp(1),Color.rgb(114,22,28));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(10),0,dp(6));parent.addView(c,lp);return c;}
 private void draw(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(14),dp(20),dp(10));root.setBackgroundColor(Color.rgb(10,3,6));setContentView(root);
  TextView brand=new TextView(this);brand.setText("KARI NETHUNTER");brand.setTextSize(28);brand.setTypeface(null,1);brand.setTextColor(Color.rgb(255,35,35));brand.setShadowLayer(dp(9),0,0,Color.rgb(200,0,0));root.addView(brand);
  label(root,"Dein Flipper. Dein Schlüssel.");
  LinearLayout nav=new LinearLayout(this);root.addView(nav);
  String[] pages={"Entsperren","Schlüssel","Einstellungen"};for(int i=0;i<pages.length;i++){final int page=i;Button b=new Button(this);b.setText(pages[i]);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(i==selectedPage?Color.rgb(255,35,35):Color.rgb(49,13,13)));nav.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));b.setOnClickListener(v->{selectedPage=page;draw();});}
  button(root,"Laufende NFC-Aktion abbrechen",()->cancelNfc(true));
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout l=new LinearLayout(this);l.setOrientation(1);scroll.addView(l);
  status=null;overview=null;
  if(selectedPage==0){
   LinearLayout c=card(l);heading(c,"Mit ZeroFIDO entsperren");overview=new TextView(this);overview.setTextSize(16);overview.setTextColor(Color.WHITE);c.addView(overview);
   label(c,"ZeroFIDO auf dem Flipper öffnen und an die NFC-Antenne halten. Jeder Entsperrvorgang prüft eine neue FIDO-Signatur.");
   button(c,"NFC einrichten & prüfen",this::startAutoSetup);
   label(c,"Einmal starten: Die App verwendet dein hinterlegtes Android-Passwort und den bekannten Schlüssel. Passwortbindung und Test folgen automatisch; bei Aufforderung den Flipper entfernen und erneut anhalten.");
   button(c,"NFC-Dienst starten",()->{try{getSharedPreferences("service_state",0).edit().putBoolean("wanted",true).commit();DirectBootUnlock.setWanted(this,true);startForegroundService(new Intent(this,NfcWatchService.class));record("NFC-Dienst gestartet");}catch(Exception e){record("Dienst konnte nicht starten: "+e.getMessage());}});
   button(c,"NFC-Dienst stoppen",()->{getSharedPreferences("service_state",0).edit().putBoolean("wanted",false).commit();DirectBootUnlock.setWanted(this,false);PinAuto.cancelPendingImePassword();stopService(new Intent(this,NfcWatchService.class));record("NFC-Dienst gestoppt");});
   LinearLayout tip=card(l);if(!CtapFido.paired(this)||!CtapFido.verified(this)||!PinAuto.configured(this)){heading(tip,"Einmal einrichten");label(tip,"1. Schlüssel registrieren und Signatur testen.\n2. Android-Passwort speichern und Tastatur freigeben.\n3. Automatik einschalten und NFC-Dienst starten.");}else heading(tip,"Bereit für ZeroFIDO");label(tip,"Nach einer erfolgreichen Prüfung 18 Sekunden bis zum nächsten Versuch warten. NFC bei ausgeschaltetem Display benötigt den passenden Systempatch.");
  }else if(selectedPage==1){
   LinearLayout c=card(l);heading(c,"Dein Zugangsschlüssel");label(c,CtapFido.paired(this)?CtapFido.displayAlias(this)+"\n"+CtapFido.rpName(this)+" · "+CtapFido.rpId(this):"Noch kein Schlüssel registriert.");
   button(c,"Schlüssel registrieren",()->new AlertDialog.Builder(this).setTitle("Neuer ZeroFIDO-Schlüssel").setItems(new String[]{"FIDO 2.1 ohne Flipper-PIN","FIDO 2.1 mit Flipper-PIN"},(d,w)->request(w==0?FIDO_21_NONE:FIDO_21_PIN)).setNegativeButton("Abbrechen",null).show());
   button(c,"Signatur testen",()->request(FIDO_VERIFY));button(c,"NFC-Fähigkeiten für Boot-Entsperrung prüfen",()->request(FIDO_INFO));button(c,"Schlüssel umbenennen",()->renameDialog());button(c,"RP verwalten",()->rpDialog());
   button(c,"Lokalen Schlüssel entfernen",()->confirm("Lokalen Schlüssel entfernen? Der Flipper wird nicht zurückgesetzt. Die Passwort-Automatik wird deaktiviert.",()->{PinAuto.toggle(this,false);CtapFido.clear(this);record("Lokaler Schlüssel entfernt");draw();}));
  }else{
   LinearLayout c=card(l);heading(c,"Android entsperren");label(c,"Das Android-Passwort wird lokal verschlüsselt gespeichert und ausschließlich nach einer gültigen FIDO-Prüfung über die Passwort-Tastatur eingegeben. Dafür ist Root erforderlich.");
   button(c,"Vorhandenes Android-Sperrpasswort hinterlegen",()->passwordDialog());
   label(c,"Hier hinterlegst du das bereits bestehende Android-Passwort. Ein neues Sperrpasswort vergibst du in den Android-Systemeinstellungen und aktualisierst es danach hier. Der gespeicherte Boot-Schlüssel bleibt dabei erhalten; seine Passwortbindung muss neu erstellt werden.");
   button(c,"Android-Sperrpasswort in Systemeinstellungen ändern",()->startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)));
   label(c,"Die Flipper-PIN ist eine separate PIN des gesamten FIDO-Authentifikators, kein Passwort je Schlüssel. Eine bestehende Flipper-PIN wird bei der Registrierung mit PIN abgefragt; sie wird nicht neu vergeben oder gespeichert. Der Boot-Unlock nutzt Benutzerbestätigung ohne PIN-Eingabe.");
   button(c,"Flipper-PIN-Status per NFC lesen",()->request(PIN_INFO));
   button(c,"Passwort-Tastatur freigeben",()->new AlertDialog.Builder(this).setTitle("Kari Nethunter Tastatur").setMessage("Die Tastatur wird freigegeben. Deine normale Tastatur bleibt ausgewählt; Kali Lock wechselt erst nach einer gültigen FIDO-Prüfung kurzzeitig zur Passwort-Eingabe.").setNegativeButton("Abbrechen",null).setPositiveButton("Freigeben",(d,w)->new Thread(()->{try{java.lang.Process proc=new ProcessBuilder("su","-c","ime enable de.nethunter.flipperunified/de.nethunter.flippernfc.LockPasswordIme").redirectErrorStream(true).start();int code=proc.waitFor();record(code==0?"Passwort-Tastatur freigegeben":"Tastatur-Freigabe fehlgeschlagen");}catch(Exception ex){record("Tastatur-Fehler: "+ex.getMessage());}}).start()).show());
   button(c,PinAuto.enabled(this)?"Automatik ausschalten":"Automatik einschalten",()->{if(PinAuto.enabled(this)){PinAuto.toggle(this,false);record("Automatik ausgeschaltet");draw();}else confirmEnable();});
   button(c,"Gespeichertes Passwort entfernen",()->confirm("Gespeichertes Android-Passwort löschen?",()->{PinAuto.clear(this);record("Passwort entfernt");draw();}));
   LinearLayout boot=card(l);heading(boot,"Erste Entsperrung nach Neustart");
   label(boot,DirectBootUnlock.enabled(this)?"Boot-Schlüssel geprüft und aktiviert. Ein echter Neustarttest steht separat an.":DirectBootUnlock.configured(this)?"Passwortbindung vorhanden. Nur noch testen/aktivieren; kein neuer Schlüssel.":DirectBootUnlock.hasPending(this)?"Schlüssel gespeichert. Beim nächsten NFC-Kontakt nur das Android-Passwort binden.":DirectBootUnlock.creationUncertain(this)?"Ein früherer Versuch könnte bereits einen Schlüssel auf dem Flipper angelegt haben. Weitere Erstellung ist gesperrt, bis du dies geprüft hast.":"Noch kein Boot-Schlüssel eingerichtet.");
   label(boot,"Du kannst zuerst den vorhandenen NFC-Schlüssel auch für Boot verwenden, ohne einen weiteren Eintrag zu erstellen. Dafür muss die bisherige Registrierung hmac-secret unterstützen. Alternativ lässt sich ausdrücklich ein separater Boot-Schlüssel anlegen. Wiederholen verwendet die gespeicherte Kennung. Zwischen den Schritten den Flipper entfernen und neu anhalten. Das Android-Sperrpasswort wird hier nicht geändert.");
   button(boot,"NFC einrichten & prüfen",this::startAutoSetup);
   label(boot,"Der Ablauf übernimmt den bekannten Schlüssel, bindet das gespeicherte Passwort und prüft die Entschlüsselung. Kein Wechsel zwischen Einrichtungs- und Testbuttons. Ein weiterer Flipper-Kontakt kann erforderlich sein.");
   button(boot,"Boot-Entsperrung deaktivieren",()->{DirectBootUnlock.disable(this);record("Boot-Entsperrung deaktiviert");draw();});
   button(boot,"Boot-Schlüssel lokal entfernen",()->confirm("Lokalen Boot-Schlüssel und verschlüsselte Boot-Passwortkopie entfernen? Normale NFC-Registrierung bleibt erhalten.",()->{DirectBootUnlock.clear(this);record("Boot-Einrichtung entfernt");draw();}));
   LinearLayout feedback=card(l);heading(feedback,"Rückmeldung");Switch vibration=new Switch(this);vibration.setText("Bei NFC-Kontakt vibrieren");vibration.setTextColor(Color.WHITE);vibration.setChecked(NfcFeedback.enabled(this));vibration.setOnCheckedChangeListener((b,on)->NfcFeedback.setEnabled(this,on));feedback.addView(vibration);
   LinearLayout help=card(l);heading(help,"Hilfe");label(help,"Die Vibration bestätigt den Kontakt. Erst eine gültige Signatur erlaubt die Passwort-Eingabe. Bei ausgeschaltetem Bildschirm muss der NFC-Systempatch aktiv sein.");button(help,"Diagnose anzeigen",()->new AlertDialog.Builder(this).setTitle("Kari Nethunter · Diagnose").setMessage("Letzter Fehler: "+CrashRecorder.lastCrash(this)+"\n\n"+getSharedPreferences("events",0).getString("history","")+"\nBoot-Ereignisse:\n"+DirectBootUnlock.device(this).getSharedPreferences("events",0).getString("history","")).setPositiveButton("Schließen",null).show());
  }
  status=new TextView(this);status.setTextColor(Color.rgb(224,170,170));status.setTextSize(13);status.setPadding(dp(4),dp(12),dp(4),dp(10));l.addView(status);show(lastMessage);
 }
 private void passwordDialog(){
  if(actionBusy.get()||pending!=0){record("Erst die laufende NFC-Aktion beenden oder abbrechen.");return;}
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(18),dp(8),dp(18),0);
  EditText secret=new EditText(this),again=new EditText(this);
  secret.setHint("Aktuelles Android-Sperrpasswort");again.setHint("Dasselbe Passwort wiederholen");
  secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);again.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  form.addView(secret);form.addView(again);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Vorhandenes Android-Passwort hinterlegen").setMessage("Hier wird kein neues Handy-Passwort vergeben. Gib dein aktuell gültiges Android-Sperrpasswort ein. Nach dem Speichern bleibt die Automatik aus. Bestehender Boot-Schlüssel bleibt erhalten; seine verschlüsselte Passwortbindung wird erneuert.").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Verschlüsselt speichern",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String value=secret.getText().toString();
   if(value.isEmpty()||value.codePointCount(0,value.length())>256){secret.setError("Bitte das aktuelle Android-Passwort mit 1–256 Zeichen eingeben");return;}
   if(!value.equals(again.getText().toString())){again.setError("Die Eingaben stimmen nicht überein");return;}
   try{PinAuto.save(this,value);record("Android-Passwort verschlüsselt hinterlegt · Automatik aus · Sperrpasswort unverändert");secret.setText("");again.setText("");dialog.dismiss();draw();}
   catch(Exception e){record("Speichern fehlgeschlagen: "+e.getClass().getSimpleName());}
  }));
  dialog.setOnDismissListener(d->{secret.setText("");again.setText("");});dialog.show();
 }
 private void confirmEnable(){if(!CtapFido.verified(this)){record("AUTO_PIN_BLOCKIERT: Zuerst unter Schlüssel registrieren und die Signatur testen");return;}if(!PinAuto.configured(this)){record("AUTO_PIN_BLOCKIERT: Zuerst unter Einstellungen das Android-Passwort speichern");return;}new AlertDialog.Builder(this).setTitle("Experimentelle Passwort-Automatik").setMessage("Bei gültiger neuer FIDO-Signatur kann die App mit Root deine Android-Passwort eingeben. Kein Schutz vor Root-Malware; funktioniert möglicherweise nicht am Sperrbildschirm. Nicht auf NFC-UID-Basis. Wirklich einschalten?").setNegativeButton("Abbrechen",null).setPositiveButton("AKTIVIEREN",(d,w)->{try{PinAuto.toggle(this,true);record("Automatik eingeschaltet");draw();}catch(Exception e){record("AUTO_PIN_ENABLE_FAILED "+e.getMessage());}}).show();}
 private void heading(LinearLayout l,String title){TextView t=new TextView(this);t.setText(title);t.setTextSize(19);t.setTextColor(Color.rgb(255,35,35));t.setPadding(0,22,0,9);l.addView(t);}
 private void label(LinearLayout l,String message){TextView v=new TextView(this);v.setText(message);v.setTextColor(Color.LTGRAY);v.setPadding(0,4,0,8);l.addView(v);}
 private void button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(90,13,20)));b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());l.addView(b,new LinearLayout.LayoutParams(-1,-2));}
 private void confirm(String text,Runnable run){new AlertDialog.Builder(this).setMessage(text).setPositiveButton("Löschen",(d,i)->run.run()).setNegativeButton("Abbrechen",null).show();}
 private void record(String msg){lastMessage=msg;try{NfcWatchService.log(this,msg);}catch(Throwable ignored){}runOnUiThread(()->{try{show(msg);}catch(Throwable ignored){}});}
 private void show(String msg){if(overview!=null)overview.setText((CtapFido.paired(this)?"● Schlüssel registriert":"○ Schlüssel fehlt")+"\n"+(CtapFido.verified(this)?"● Signatur bestätigt":"○ Signatur noch testen")+"\n"+(PinAuto.configured(this)?"● Passwort gespeichert":"○ Passwort fehlt")+"\n"+(PinAuto.enabled(this)?"● Automatik eingeschaltet":"○ Automatik ausgeschaltet"));if(status!=null&&!msg.isEmpty())status.setText(msg);}
 private void reader(boolean enabled){reader(enabled,null);}
 private void reader(boolean enabled,Runnable done){
  final int epoch=readerEpoch.incrementAndGet();
  if(nfcControl.isShutdown())return;
  try{nfcControl.execute(()->{
   try{
    if(epoch!=readerEpoch.get())return;
    if(adapter==null){record("NFC NICHT VERFUEGBAR");return;}
    if(enabled){if(destroyed||!resumed)return;activeNfc=true;Bundle extras=new Bundle();extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,1000);adapter.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,extras);}
    else if(activeNfc){activeNfc=false;adapter.disableReaderMode(this);}
   }catch(Exception e){record("NFC_READER_ERROR "+e.getClass().getSimpleName());}
   finally{if(done!=null)done.run();}
  });}catch(RejectedExecutionException ignored){}
 }
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
  new AlertDialog.Builder(this).setTitle("RP verwalten").setView(layout)
   .setNeutralButton("Nur aktuellen Namen ändern",(d,w)->{try{CtapFido.renameRp(this,current.getText().toString());record("RP_DISPLAY_RENAMED: Existing RP-ID unchanged");}catch(Exception ex){record("RP_RENAME_FAILED "+ex.getMessage());}})
   .setNegativeButton("Abbrechen",null)
   .setPositiveButton("Vorgaben für neue Schlüssel",(d,w)->{try{CtapFido.saveFutureRp(this,nextId.getText().toString(),nextName.getText().toString());record("NEXT_RP_DEFAULTS_SAVED; vorhandene Credential unverändert");}catch(Exception ex){record("RP_DEFAULTS_FAILED "+ex.getMessage());}}).show();
 }
 private void request(int mode){
  if(actionBusy.get()||pending!=0){record("NFC-Aktion laeuft bereits. Erst beenden oder abbrechen; keine neue Registrierung gestartet.");return;}
  if((mode==BOOT_ENROLL||mode==BOOT_TEST)&&!PinAuto.configured(this)){record("Zuerst das vorhandene Android-Sperrpasswort hinterlegen.");passwordDialog();return;}
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
 private void startAutoSetup(){
  if(setupSequence.active()){record("Ablauf läuft. ZeroFIDO geöffnet halten und auf die NFC-Anweisung warten.");return;}
  if(actionBusy.get()||pending!=0)cancelNfc(false);
  if(!PinAuto.configured(this)){record("Android-Passwort fehlt. Einmal hinterlegen, danach den Ablauf starten.");passwordDialog();return;}
  try{
   if(!DirectBootUnlock.configured(this)&&!DirectBootUnlock.hasPending(this)){
    if(!CtapFido.paired(this)){record("Kein bekannter NFC-Schlüssel vorhanden. Es wurde kein neuer Eintrag angelegt.");return;}
    DirectBootUnlock.useNormalCredential(this);
   }
   setupSequence.start();arm(AUTO_SETUP,"");record("AUTO_SETUP_STARTED: ZeroFIDO anhalten. Gespeichertes Passwort und bekannter Schlüssel werden verwendet.");
  }catch(Exception e){setupSequence.cancel();record("AUTO_SETUP_FAILED "+e.getClass().getSimpleName()+": "+e.getMessage());}
 }
 private void waitForNextSetupTap(Tag tag,int epoch){
  record("Flipper jetzt entfernen und erneut anhalten. Der Ablauf geht automatisch weiter.");
  try{nfcControl.execute(()->{
   if(epoch!=operationEpoch.get()||!setupSequence.active())return;
   Runnable ready=()->runOnUiThread(()->{
    if(epoch!=operationEpoch.get()||!setupSequence.active()||!resumed||destroyed)return;
    pending=AUTO_SETUP;reader(true);
    record(DirectBootUnlock.configured(this)?"AUTO_SETUP_WAIT_VERIFY: ZeroFIDO erneut anhalten; abschließender Test.":"AUTO_SETUP_WAIT_BIND: ZeroFIDO erneut anhalten; Passwortbindung wird wiederholt.");
   });
   try{if(!adapter.ignore(tag,400,()->ready.run(),new Handler(Looper.getMainLooper())))ready.run();}
   catch(Exception e){ready.run();}
  });}catch(RejectedExecutionException ignored){}
 }
 private void arm(int mode,String secret){if(actionBusy.get()||pending!=0){record("NFC-Aktion laeuft bereits");return;}operationEpoch.incrementAndGet();pendingPin=secret;pending=mode;try{stopService(new Intent(this,NfcWatchService.class));}catch(Throwable e){record("SERVICE_STOP_ERROR "+e);}reader(true);record("NFC BEREIT: Flipper ans S20 halten (Aktion "+mode+")");}
 private void cancelNfc(boolean notify){setupSequence.cancel();final int epoch=operationEpoch.incrementAndGet();pending=0;pendingPin="";DirectBootUnlock.cancelSetup();Tag tag=operationTag;operationTag=null;CtapFido.Session.cancelTag(tag);Future<?> job=operationJob;if(job!=null)job.cancel(true);actionBusy.set(false);reader(false,()->{if(epoch==operationEpoch.get()&&DirectBootUnlock.wanted(this)&&(PinAuto.enabled(this)||DirectBootUnlock.enabled(this)))try{getApplicationContext().startForegroundService(new Intent(getApplicationContext(),NfcWatchService.class));}catch(Exception e){record("NFC_SERVICE_RESTORE_FAILED "+e.getClass().getSimpleName());}});if(notify)record("NFC-Aktion abgebrochen. Ein bereits angelegter Schluessel wird nicht automatisch neu erstellt.");}
 @Override public void onTagDiscovered(Tag tag){
  if(destroyed||!resumed||nfcWorker.isShutdown()||pending==0||!actionBusy.compareAndSet(false,true))return;
  final int action=pending,epoch=operationEpoch.get();pending=0;operationTag=tag;
  final String secret=pendingPin;pendingPin="";final String alias=pendingAlias,username=pendingUsername,rpId=pendingRpId,rpName=pendingRpName;
  try{operationJob=nfcWorker.submit(()->performNfc(tag,action,epoch,secret,alias,username,rpId,rpName));}catch(RejectedExecutionException ignored){actionBusy.set(false);}
 }
 private void performNfc(Tag tag,int action,int epoch,String secret,String alias,String username,String rpId,String rpName){if(epoch!=operationEpoch.get())return;boolean waitForTap=false;try{switch(action){

  case TAG_LEARN: TagIdentity.pair(this,tag);record("NFC_TAG_ANGELERNT");break;
  case TAG_TEST:record("NFC_TAG_TEST="+TagIdentity.matches(this,tag));break;
  case FIDO_INFO:CtapFido.info(tag,this::record);break;
  case PIN_INFO:CtapFido.pinStatus(tag,this::record);break;
  case FIDO_21_NONE:CtapFido.enroll(this,tag,true,false,"",alias,username,rpId,rpName,this::record);break;
  case FIDO_21_PIN:CtapFido.enroll(this,tag,true,true,secret,alias,username,rpId,rpName,this::record);break;
  case FIDO_20_NONE:CtapFido.enroll(this,tag,false,false,"",alias,username,rpId,rpName,this::record);break;
  case FIDO_20_PIN:CtapFido.enroll(this,tag,false,true,secret,alias,username,rpId,rpName,this::record);break;
  case FIDO_VERIFY:CtapFido.verify(this,tag,this::record);break;
  case BOOT_ENROLL:DirectBootUnlock.provision(this,tag,this::record);break;
  case BOOT_TEST:DirectBootUnlock.testAndEnable(this,tag,this::record);break;
  case AUTO_SETUP:{
   SetupSequence.Step step=setupSequence.next(DirectBootUnlock.configured(this));
   record(step==SetupSequence.Step.BIND?"AUTO_SETUP_BIND_BEGIN":"AUTO_SETUP_VERIFY_BEGIN");
   if(step==SetupSequence.Step.BIND)DirectBootUnlock.provision(this,tag,this::record);else DirectBootUnlock.testAndEnable(this,tag,this::record);
   SetupSequence.Result result=setupSequence.complete(step,DirectBootUnlock.configured(this),DirectBootUnlock.enabled(this));
   waitForTap=result==SetupSequence.Result.WAIT_FOR_NEXT_TAP;
   if(result==SetupSequence.Result.DONE)record("AUTO_SETUP_VERIFIED: Passwortbindung und unabhängiger Entschlüsselungstest erfolgreich. Für Neustarttest bereit.");
   else if(!waitForTap)record("AUTO_SETUP_FAILED: Einrichtung unvollständig; kein Neustart freigegeben.");
   break;
  }
  default:record("UNBEKANNTE_AKTION");
 }}catch(Throwable e){
   record("AKTION_"+action+"_FEHLER: "+CrashRecorder.detail(e));
   if(action==AUTO_SETUP&&setupSequence.active()){
    if(e instanceof java.io.IOException)waitForTap=setupSequence.transportFailed()==SetupSequence.Result.WAIT_FOR_NEXT_TAP;
    else setupSequence.cancel();
    if(!waitForTap)record("AUTO_SETUP_FAILED: "+e.getClass().getSimpleName()+": "+e.getMessage());
   }
  }finally{
   if(epoch==operationEpoch.get()){operationTag=null;actionBusy.set(false);if(waitForTap&&setupSequence.active())waitForNextSetupTap(tag,epoch);else finishReader(epoch);}
  }}
 private void finishReader(int epoch){reader(false,()->runOnUiThread(()->{
  if(destroyed||epoch!=operationEpoch.get())return;
  try{if(resumed)draw();if(DirectBootUnlock.wanted(this)&&(PinAuto.enabled(this)||DirectBootUnlock.enabled(this)))startForegroundService(new Intent(this,NfcWatchService.class));}
  catch(Exception e){record("NFC_FINALLY_ERROR "+e.getClass().getSimpleName());}
 }));}
 @Override protected void onResume(){super.onResume();resumed=true;if(pending!=0)reader(true);if(status!=null)show("");}
 @Override protected void onPause(){resumed=false;if(setupSequence.active()||actionBusy.get()||pending!=0)cancelNfc(false);else reader(false);super.onPause();}
 @Override protected void onDestroy(){destroyed=true;cancelNfc(false);nfcWorker.shutdownNow();nfcControl.shutdown();super.onDestroy();}
}
