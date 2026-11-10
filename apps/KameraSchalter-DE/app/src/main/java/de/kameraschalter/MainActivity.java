package de.kameraschalter;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status;
    private LinearLayout root;
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,48,36,36); root.setBackgroundColor(Color.rgb(20,22,29));
        TextView heading = new TextView(this); heading.setText("KameraSchalter DE"); heading.setTextSize(26); heading.setTextColor(Color.WHITE); root.addView(heading);
        TextView subtitle = new TextView(this); subtitle.setText("Kostenlos · Ohne Login · Root + bestehendes CamSwap-Modul erforderlich"); subtitle.setTextColor(0xffc9ced8); subtitle.setPadding(0,14,0,28); root.addView(subtitle);
        status = new TextView(this); status.setText("Bereit. CamSwap muss im LSPosed-Bereich von TikTok aktiv sein."); status.setTextColor(Color.WHITE); root.addView(status);
        button("Echte Kamera", () -> update(true));
        button("Virtuelle Kamera", () -> update(false));
        button("Schwebenden Schalter einblenden", () -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                status.setText("Bitte 'Über anderen Apps einblenden' erlauben und erneut tippen.");
            } else { startService(new Intent(this, BubbleService.class)); status.setText("Schwebender Schalter aktiv."); }
        });
        button("Schwebenden Schalter ausblenden", () -> stopService(new Intent(this,BubbleService.class)));
        setContentView(root);
    }
    private void update(boolean real) { status.setText("Umschalten …"); CameraConfig.switchCamera(real,(ok,msg)->status.setText(msg + (ok ? " (bereits geöffnete TikTok-Kamera nicht garantiert)" : ""))); }
    private void button(String text, Runnable r) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=14; root.addView(b,lp); b.setOnClickListener(v->r.run()); }
}
