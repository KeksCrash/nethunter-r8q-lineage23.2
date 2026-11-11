package de.nethunter.smartunlock;
import android.app.Activity;import android.app.NotificationManager;import android.os.Build;import android.os.Bundle;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.view.View;import android.widget.*;
public class MainActivity extends Activity {
 TextView state,detail,raw;Button toggle;int bg=Color.rgb(10,9,15),red=Color.rgb(255,50,70),muted=Color.rgb(182,178,190);
 GradientDrawable panel(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);d.setStroke(2,stroke);return d;}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
  ScrollView sc=new ScrollView(this);LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);outer.setPadding(24,38,24,32);outer.setBackgroundColor(bg);sc.addView(outer);
  outer.addView(text("LINEAGEOS 23.2 / NETHUNTER",12,red));TextView head=text("SMART\nUNLOCK",38,Color.WHITE);outer.addView(head);
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(24,24,24,24);card.setBackground(panel(Color.rgb(24,17,26),Color.rgb(110,28,46),30));outer.addView(card);
  card.addView(text("● SYSTEMSTATUS",13,red));state=text("PRÜFE…",28,Color.WHITE);card.addView(state);detail=text("Lese Profilstatus…",15,muted);card.addView(detail);
  toggle=new Button(this);toggle.setText("SMART UNLOCK UMSCHALTEN");outer.addView(toggle);Button refresh=new Button(this);refresh.setText("↻ Status aktualisieren");outer.addView(refresh);
  outer.addView(text("TRUST-ÜBERWACHUNG",14,red));outer.addView(text("Nach der ersten Entsperrung überwacht eine sichtbare Hintergrundbenachrichtigung den Vertrauensstatus, solange das Smart-Unlock-Profil aktiv ist. Android kann trotzdem eine PIN verlangen. Es erfolgt kein automatischer Profilwechsel.",14,muted));
  raw=text("",11,muted);outer.addView(raw);setContentView(sc);
  toggle.setOnClickListener(v->run("toggle"));refresh.setOnClickListener(v->run("status"));
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=getPackageManager().PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},10);
 }
 @Override protected void onResume(){super.onResume();android.app.KeyguardManager k=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);if(k!=null&&!k.isDeviceLocked())TrustMonitor.setUnlocked(this,true);run("status");}
 void run(String action){toggle.setEnabled(false);state.setText("PRÜFE…");RootExec.run(this,action,r->{State s=State.parse(r);show(s);Widget.update(this,s);TrustMonitor.sync(this,s);toggle.setEnabled(true);});}
 void show(State s){state.setText(s.label);state.setTextColor(s.error?Color.YELLOW:s.on?red:Color.WHITE);detail.setText(s.detail);raw.setText(s.error?s.raw:"Smart Unlock v1.4 · Profil und Android-Vertrauen werden separat geprüft.");toggle.setText(s.on?"SMART UNLOCK DEAKTIVIEREN":"SMART UNLOCK AKTIVIEREN");}
}
