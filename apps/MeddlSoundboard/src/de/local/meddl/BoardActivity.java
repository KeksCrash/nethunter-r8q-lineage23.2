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
        add(utilities, "MP3 oder Audiodatei hinzufügen", () -> {
            var picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("audio/*");
            startActivityForResult(picker, PICK_AUDIO);
        });
        add(utilities, "Instant-Buttons im Browser suchen", this::browseSites);
        add(utilities, "Instant-Button per Link hinzufügen", this::askLink);
        add(utilities, "Kategorien verwalten", this::manageCategories);
        add(utilities, "Identische Sounds entfernen", this::removeDuplicates);
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
        runLibraryTask(() -> "Hinzugefügt: " + task.call());
    }

    private void runLibraryTask(Callable<String> task) {
        IMPORTS.execute(() -> {
            String result;
            try {
                result = task.call();
            } catch (Exception error) {
                result = "Soundboard: " + error.getMessage();
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

    private void manageCategories() {
        var categories = ClipStore.categories(this);
        categories.remove(ClipStore.UNSORTED);
        categories.add(0, "+ Neue Kategorie");
        new AlertDialog.Builder(this).setTitle("Kategorien")
            .setItems(categories.toArray(String[]::new), (dialog, index) ->
                categoryDialog(index == 0 ? null : categories.get(index)))
            .setNegativeButton("Schließen", null).show();
    }

    private void categoryDialog(String previous) {
        var name = new EditText(this);
        name.setSingleLine(true);
        if (previous != null) name.setText(previous);
        var builder = new AlertDialog.Builder(this).setTitle(previous == null ? "Neue Kategorie" : "Kategorie bearbeiten")
            .setView(name).setNegativeButton("Abbrechen", null).setPositiveButton("Speichern", null);
        if (previous != null) {
            builder.setNeutralButton("Löschen", (dialog, which) -> new AlertDialog.Builder(this)
                .setTitle("Kategorie löschen?").setMessage("Die Sounds werden nach Unsortiert verschoben.")
                .setNegativeButton("Abbrechen", null).setPositiveButton("Löschen", (confirm, button) -> {
                    ClipStore.editCategory(this, previous, null);
                    refreshClips();
                }).show());
        }
        var dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                ClipStore.editCategory(this, previous, name.getText().toString());
                refreshClips();
                dialog.dismiss();
            } catch (IllegalArgumentException error) {
                name.setError(error.getMessage());
            }
        });
    }

    private void removeDuplicates() {
        new AlertDialog.Builder(this).setTitle("Identische Sounds entfernen?")
            .setMessage("Pro identischem Audiosignal bleibt ein Sound erhalten.")
            .setNegativeButton("Abbrechen", null).setPositiveButton("Bereinigen", (dialog, button) ->
                runLibraryTask(() -> "Entfernte Duplikate: " + ClipStore.removeDuplicates(getApplicationContext())))
            .show();
    }

    private void refreshClips() {
        clips.removeAllViews();
        for (var group : ClipStore.groups(this).entrySet()) {
            if (group.getValue().isEmpty()) continue;
            String name = group.getKey();
            int color = name.equals("Andreas") ? BoardTheme.AMBER
                : name.equals("Importe") || name.endsWith("· KI") ? BoardTheme.VIOLET : BoardTheme.TEAL;
            BoardTheme.row(clips, BoardTheme.label(this, name, 13, color, true), 14);
            for (ClipStore.Clip clip : group.getValue()) {
                Button button = BoardTheme.button(this, clip.title(), BoardTheme.SURFACE, color,
                    () -> request("clip", clip));
                BoardTheme.row(clips, button, 7);
                button.setOnLongClickListener(view -> {
                    clipMenu(clip);
                    return true;
                });
            }
        }
    }

    private void clipMenu(ClipStore.Clip clip) {
        new AlertDialog.Builder(this).setTitle(clip.title())
            .setItems(new String[] {"In Kategorie verschieben", "Sound entfernen"}, (dialog, index) -> {
                if (index == 0) {
                    var categories = ClipStore.categories(this);
                    new AlertDialog.Builder(this).setTitle("Zielkategorie")
                        .setItems(categories.toArray(String[]::new), (chooser, choice) -> {
                            ClipStore.move(this, clip, categories.get(choice));
                            refreshClips();
                        }).show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Sound entfernen?")
                    .setMessage(clip.title()).setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Entfernen", (confirm, button) -> {
                        ClipStore.remove(this, clip);
                        refreshClips();
                    }).show();
            }).setNegativeButton("Schließen", null).show();
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
