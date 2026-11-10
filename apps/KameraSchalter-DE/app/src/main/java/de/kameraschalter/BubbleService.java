package de.kameraschalter;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.*;

public class BubbleService extends Service {
    private WindowManager manager; private LinearLayout overlay;
    @Override public void onCreate() {
        super.onCreate(); if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        overlay=new LinearLayout(this); overlay.setOrientation(LinearLayout.VERTICAL); overlay.setPadding(10,10,10,10); overlay.setBackgroundColor(0xee181c29);
        add("ECHT",true); add("VIDEO",false);
        Button close=new Button(this); close.setText("×"); overlay.addView(close); close.setOnClickListener(v->stopSelf());
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.END; lp.x=8; lp.y=180; manager.addView(overlay,lp);
    }
    private void add(String title, boolean real) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false); overlay.addView(b);
        b.setOnClickListener(v->{ b.setText("..."); CameraConfig.switchCamera(real,(ok,msg)->{ b.setText(title); Toast.makeText(this,msg,Toast.LENGTH_SHORT).show(); }); });
    }
    @Override public void onDestroy() { if(manager!=null && overlay!=null) manager.removeView(overlay); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
