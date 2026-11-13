package de.local.meddl;

import android.app.*;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public final class FloatingBoard extends Service {
    private WindowManager windows;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout panel;
    private LinearLayout controls;
    private ScrollView expanded;
    private TextView status;
    private Button toggle;
    private Button micButton;
    private BoardLink.State snapshot = new BoardLink.State(false, "off", false, false, "off");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::refreshStatus;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        var notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(
            new NotificationChannel("soundboard", "Schwebendes Soundboard", NotificationManager.IMPORTANCE_LOW));
        var launch = PendingIntent.getActivity(this, 0, new Intent(this, BoardActivity.class),
                                              PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "soundboard")
            .setContentTitle("Meddl-Soundboard").setContentText("Schwebende Tasten aktiv")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(launch).build());
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (panel == null) show();
        return START_NOT_STICKY;
    }

    private void show() {
        windows = getSystemService(WindowManager.class);
        panel = BoardTheme.card(this, BoardTheme.BG);
        BoardTheme.pad(panel, 6, 6);
        expanded = new ScrollView(this);
        expanded.setVisibility(ScrollView.GONE);
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        expanded.addView(controls);
        toggle = BoardTheme.button(this, "Meddl ♫", BoardTheme.VIOLET, BoardTheme.BG, () -> {
            if (expanded.getVisibility() == ScrollView.VISIBLE) {
                expanded.setVisibility(ScrollView.GONE);
                toggle.setText("Meddl ♫");
            } else {
                refreshControls();
                expanded.setVisibility(ScrollView.VISIBLE);
                toggle.setText("− Minimieren");
            }
            refreshStatus();
        });
        panel.addView(toggle);
        panel.addView(expanded);
        var metrics = getResources().getDisplayMetrics();
        layoutParams = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = metrics.widthPixels - BoardTheme.dp(this, 120);
        layoutParams.y = metrics.heightPixels / 2;
        windows.addView(panel, layoutParams);
        toggle.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean dragging;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = layoutParams.x; startY = layoutParams.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        dragging = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - touchX);
                        int dy = (int)(e.getRawY() - touchY);
                        if (!dragging && Math.abs(dx) + Math.abs(dy) > 15) dragging = true;
                        if (dragging) {
                            layoutParams.x = startX + dx;
                            layoutParams.y = startY + dy;
                            windows.updateViewLayout(panel, layoutParams);
                        }
                        return dragging;
                    case MotionEvent.ACTION_UP:
                        if (dragging) { v.performClick(); return true; }
                        return false;
                }
                return false;
            }
        });
        refreshStatus();
    }

    private void refreshControls() {
        controls.removeAllViews();
        status = BoardTheme.label(this, "Brücke wird geprüft …", 13, BoardTheme.TEAL, true);
        BoardTheme.row(controls, status, 7);
        micButton = BoardTheme.button(this, micButtonLabel(), BoardTheme.SURFACE, BoardTheme.TEAL,
            () -> request(snapshot.micEnabled() ? "mic-off" : "mic-on", null));
        BoardTheme.row(controls, micButton, 6);
        int rows = 0;
        for (var group : ClipStore.groups(this).entrySet()) {
            if (group.getValue().isEmpty()) continue;
            BoardTheme.row(controls, BoardTheme.label(this, group.getKey(), 12, BoardTheme.VIOLET, true), 8);
            rows++;
            for (ClipStore.Clip clip : group.getValue()) {
                BoardTheme.row(controls, BoardTheme.button(this, clip.title(), BoardTheme.SURFACE, BoardTheme.TEXT,
                    () -> request("clip", clip)), 6);
                rows++;
            }
        }
        BoardTheme.row(controls, BoardTheme.button(this, "Einspeisung aus", 0xff4a2939,
                                                   BoardTheme.RED, () -> request("stop", null)), 6);
        BoardTheme.row(controls, BoardTheme.button(this, "Ausblenden", BoardTheme.SURFACE,
                                                   BoardTheme.TEXT, this::stopSelf), 6);
        int height = Math.min(getResources().getDisplayMetrics().heightPixels / 2,
                              BoardTheme.dp(this, (rows + 4) * 54));
        expanded.setLayoutParams(new LinearLayout.LayoutParams(BoardTheme.dp(this, 230), height));
        refreshStatus();
    }

    private String micButtonLabel() {
        return snapshot.micEnabled() ? "Handy-Mikrofon aus" : "Handy-Mikrofon an";
    }

    private void refreshStatus() {
        if (panel == null) return;
        BoardLink.state(this, state -> {
            if (panel == null) return;
            snapshot = state;
            BoardTheme.style(toggle, !state.reachable() ? BoardTheme.RED :
                             state.active() ? BoardTheme.TEAL : BoardTheme.VIOLET, BoardTheme.BG);
            toggle.setContentDescription((expanded.getVisibility() == ScrollView.VISIBLE
                ? "Soundboard minimieren · " : "Soundboard öffnen · ") + state.label());
            if (status != null) {
                status.setText(state.label() + "\n" + (state.pc() ? "PC-RVC verbunden" : "PC-RVC getrennt")
                               + (state.clip() ? " · Clip läuft" : "") + "\n" + state.micLabel());
                micButton.setText(micButtonLabel());
            }
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, 1500);
        });
    }

    private void request(String command, ClipStore.Clip asset) {
        BoardLink.request(this, command, asset,
            message -> {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                refreshStatus();
            });
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(poll);
        if (panel != null) {
            windows.removeView(panel);
            panel = null;
        }
        super.onDestroy();
    }
}
