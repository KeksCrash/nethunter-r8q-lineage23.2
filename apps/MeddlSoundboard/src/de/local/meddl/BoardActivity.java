package de.local.meddl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.WindowInsets;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

public final class BoardActivity extends Activity {
    private static final int PICK_AUDIO = 1;
    private static final java.util.concurrent.ExecutorService IMPORTS = Executors.newSingleThreadExecutor();
    private LinearLayout panel;
    private LinearLayout clips;
    private TextView status;
    private TextView routeStatus;
    private TextView pcStatus;
    private TextView clipStatus;
    private TextView micStatus;
    private Button micButton;
    private Button globalButton;
    private Button tiktokButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BoardLink.State snapshot = new BoardLink.State(false, "off", false, false, "off");
    private boolean visible;
    private final Runnable poll = this::refreshStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var scroll = new ScrollView(this);
        scroll.setBackgroundColor(BoardTheme.BG);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        BoardTheme.pad(panel, 16, 14);
        scroll.addView(panel);
        setContentView(scroll);

        var hero = BoardTheme.card(this, BoardTheme.SURFACE);
        var gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] {0xff352552, 0xff1e3540});
        gradient.setCornerRadius(BoardTheme.dp(this, 20));
        hero.setBackground(gradient);
        hero.addView(BoardTheme.label(this, "●  AUDIO-STUDIO", 12, BoardTheme.TEAL, true));
        BoardTheme.row(hero, BoardTheme.label(this, "Meddl-Soundboard", 27, BoardTheme.TEXT, true), 5);
        BoardTheme.row(hero, BoardTheme.label(this, "Clips · Mikrofon · PC-RVC", 14, BoardTheme.MUTED, false), 3);
        panel.addView(hero);

        var dashboard = BoardTheme.card(this, BoardTheme.SURFACE);
        dashboard.addView(BoardTheme.label(this, "LIVE-STATUS", 12, BoardTheme.VIOLET, true));
        routeStatus = BoardTheme.label(this, "●  Brücke wird geprüft …", 17, BoardTheme.TEAL, true);
        BoardTheme.row(dashboard, routeStatus, 6);
        pcStatus = BoardTheme.label(this, "PC-RVC · —", 14, BoardTheme.MUTED, false);
        BoardTheme.row(dashboard, pcStatus, 3);
        clipStatus = BoardTheme.label(this, "Soundboard · —", 14, BoardTheme.MUTED, false);
        BoardTheme.row(dashboard, clipStatus, 3);
        micStatus = BoardTheme.label(this, "Handy-Mikrofon · aus", 14, BoardTheme.MUTED, false);
        BoardTheme.row(dashboard, micStatus, 3);
        micButton = BoardTheme.button(this, "Handy-Mikrofon zuschalten", BoardTheme.SURFACE,
            BoardTheme.TEAL, () -> request(snapshot.micEnabled() ? "mic-off" : "mic-on", null));
        BoardTheme.row(dashboard, micButton, 8);
        var monitor = new Switch(this);
        monitor.setText("Selbst hören");
        monitor.setTextColor(BoardTheme.TEXT);
        monitor.setChecked(BoardLink.monitorEnabled(this));
        monitor.setThumbTintList(ColorStateList.valueOf(BoardTheme.TEAL));
        monitor.setOnCheckedChangeListener((view, on) -> BoardLink.setMonitorEnabled(this, on));
        BoardTheme.row(dashboard, monitor, 7);
        BoardTheme.row(panel, dashboard, 12);

        var routing = BoardTheme.card(this, BoardTheme.SURFACE);
        routing.addView(BoardTheme.label(this, "MIKROFONROUTE", 12, BoardTheme.VIOLET, true));
        globalButton = BoardTheme.button(this, "Systemweit", BoardTheme.SURFACE, BoardTheme.TEXT,
            () -> chooseRoute(true));
        tiktokButton = BoardTheme.button(this, "Nur TikTok", BoardTheme.SURFACE, BoardTheme.TEXT,
            () -> chooseRoute(false));
        pair(routing, globalButton, tiktokButton);
        paintRouteChoice();
        BoardTheme.row(routing, BoardTheme.label(this, "Systemweit gilt für gewöhnliche Android-Aufnahmen.",
                                                  13, BoardTheme.MUTED, false), 6);
        pair(routing,
            BoardTheme.button(this, "Einspeisung aktivieren", BoardTheme.TEAL, BoardTheme.BG,
                () -> request("start", null)),
            BoardTheme.button(this, "Einspeisung aus", 0xff4a2939, BoardTheme.RED,
                () -> request("stop", null)));
        BoardTheme.row(panel, routing, 12);

        var utilities = BoardTheme.card(this, BoardTheme.SURFACE);
        utilities.addView(BoardTheme.label(this, "BEDIENUNG & IMPORT", 12, BoardTheme.VIOLET, true));
        add(utilities, "Schwebende Tasten einblenden", () -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                         Uri.parse("package:" + getPackageName())));
                return;
            }
            startForegroundService(new Intent(this, FloatingBoard.class));
            request("start", null);
        });
        add(utilities, "Schwebende Tasten ausblenden", () -> stopService(new Intent(this, FloatingBoard.class)));
        add(utilities, "Eigene Audiodatei hinzufügen", () -> {
            var picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("audio/*");
            startActivityForResult(picker, PICK_AUDIO);
        });
        add(utilities, "Instant-Buttons im Browser suchen", this::browseSites);
        add(utilities, "Instant-Button per Link hinzufügen", this::askLink);
        status = BoardTheme.label(this, "Bereit für einen Sound.", 14, BoardTheme.MUTED, false);
        BoardTheme.row(utilities, status, 8);
        BoardTheme.row(panel, utilities, 12);
        clips = new LinearLayout(this);
        clips.setOrientation(LinearLayout.VERTICAL);
        BoardTheme.row(panel, clips, 10);
        refreshClips();
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        refreshStatus();
    }

    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }

    private void chooseRoute(boolean global) {
        if (BoardLink.global(this) == global) return;
        BoardLink.selectGlobal(this, global);
        paintRouteChoice();
        if (snapshot.active()) request("start", null);
    }

    private void paintRouteChoice() {
        boolean global = BoardLink.global(this);
        BoardTheme.style(globalButton, global ? BoardTheme.VIOLET : BoardTheme.SURFACE,
                         global ? BoardTheme.BG : BoardTheme.MUTED);
        BoardTheme.style(tiktokButton, global ? BoardTheme.SURFACE : BoardTheme.VIOLET,
                         global ? BoardTheme.MUTED : BoardTheme.BG);
        globalButton.setSelected(global);
        tiktokButton.setSelected(!global);
    }

    private void refreshStatus() {
        if (!visible) return;
        BoardLink.state(this, state -> {
            if (!visible) return;
            snapshot = state;
            routeStatus.setText("●  " + state.label());
            routeStatus.setTextColor(!state.reachable() ? BoardTheme.RED
                : state.active() ? BoardTheme.TEAL : BoardTheme.AMBER);
            pcStatus.setText(state.pc() ? "PC-RVC · verbunden" : "PC-RVC · getrennt");
            pcStatus.setTextColor(state.pc() ? BoardTheme.TEAL : BoardTheme.MUTED);
            clipStatus.setText(state.clip() ? "Soundboard · Clip läuft" : "Soundboard · bereit");
            clipStatus.setTextColor(state.clip() ? BoardTheme.AMBER : BoardTheme.MUTED);
            micStatus.setText(state.micLabel());
            micStatus.setTextColor(state.mic().equals("on") ? BoardTheme.TEAL
                : state.mic().matches("muted|error") ? BoardTheme.RED : BoardTheme.MUTED);
            micButton.setText(state.micEnabled() ? "Handy-Mikrofon ausschalten" : "Handy-Mikrofon zuschalten");
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, 1000);
        });
    }

    private void pair(LinearLayout parent, Button first, Button second) {
        var row = new LinearLayout(this);
        var left = new LinearLayout.LayoutParams(0, -1, 1);
        left.rightMargin = BoardTheme.dp(this, 8);
        row.addView(first, left);
        row.addView(second, new LinearLayout.LayoutParams(0, -1, 1));
        BoardTheme.row(parent, row, 8);
    }

    @Override protected void onActivityResult(int requestCode, int result, Intent data) {
        super.onActivityResult(requestCode, result, data);
        if (requestCode == PICK_AUDIO && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            importClip(() -> ClipStore.fromFile(getApplicationContext(), uri));
        }
    }

    private void browseSites() {
        String[] choices = {"Myinstants", "InstantsButton"};
        String[] links = {"https://www.myinstants.com/de/", "https://instantsbutton.com/"};
        new AlertDialog.Builder(this).setTitle("Instant-Buttons im Browser suchen")
            .setItems(choices, (dialog, which) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(links[which])));
                } catch (ActivityNotFoundException error) {
                    status.setText("Kein Browser zum Öffnen gefunden.");
                }
            }).setNegativeButton("Abbrechen", null).show();
    }

    private void askLink() {
        var field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setHint("https://www.myinstants.com/…");
        var form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(field);
        add(form, "Link aus Zwischenablage einfügen", () -> {
            var clipboard = getSystemService(ClipboardManager.class);
            var clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                field.setText(clip.getItemAt(0).coerceToText(this));
                field.setSelection(field.length());
            }
        });
        new AlertDialog.Builder(this).setTitle("Link von Myinstants oder InstantsButton")
            .setView(form).setNegativeButton("Abbrechen", null)
            .setPositiveButton("Hinzufügen", (dialog, button) -> {
                String link = field.getText().toString();
                importClip(() -> ClipStore.fromLink(getApplicationContext(), link));
            }).show();
    }

    private void importClip(Callable<String> task) {
        status.setText("Sound wird importiert …");
        IMPORTS.execute(() -> {
            String result;
            try {
                result = "Hinzugefügt: " + task.call();
            } catch (Exception error) {
                result = "Import fehlgeschlagen: " + error.getMessage();
            }
            String message = result;
            runOnUiThread(() -> {
                if (!isDestroyed()) {
                    status.setText(message);
                    refreshClips();
                }
            });
        });
    }

    private void refreshClips() {
        clips.removeAllViews();
        String group = "";
        for (ClipStore.Clip clip : ClipStore.list(this)) {
            String section = !clip.builtin() ? "IMPORTE"
                : clip.title().startsWith("Andreas ·") ? "ANDREAS" : "DRACHENLORD";
            int color = section.equals("ANDREAS") ? BoardTheme.AMBER
                : section.equals("IMPORTE") ? BoardTheme.VIOLET : BoardTheme.TEAL;
            if (!group.equals(section)) {
                BoardTheme.row(clips, BoardTheme.label(this, section, 13, color, true), 14);
                group = section;
            }
            Button button = BoardTheme.button(this, clip.title(), BoardTheme.SURFACE, color,
                                             () -> request("clip", clip));
            BoardTheme.row(clips, button, 7);
            if (!clip.builtin()) button.setOnLongClickListener(view -> {
                new AlertDialog.Builder(this).setTitle("Sound entfernen?")
                    .setMessage(clip.title()).setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Entfernen", (dialog, which) -> {
                        ClipStore.remove(this, clip);
                        refreshClips();
                    }).show();
                return true;
            });
        }
    }

    private Button add(LinearLayout parent, String label, Runnable action) {
        var button = BoardTheme.button(this, label, BoardTheme.SURFACE, BoardTheme.TEXT, action);
        BoardTheme.row(parent, button, 7);
        return button;
    }

    private void request(String command, ClipStore.Clip clip) {
        BoardLink.request(this, command, clip, message -> {
            status.setText(message);
            refreshStatus();
        });
    }
}
