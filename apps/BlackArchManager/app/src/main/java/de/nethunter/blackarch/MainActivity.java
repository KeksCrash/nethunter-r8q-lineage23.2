package de.nethunter.blackarch;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String TAG = "BlackArch";
    private static final String CHROOT = "/data/local/nhsystem/blackarch-arm64";
    private static final String BOOT = "/data/adb/service.d/bootblackarch";
    private static final String KILL = "/data/adb/service.d/killblackarch";
    private static final String ENV = "PATH=/usr/bin:/usr/sbin:/bin:/sbin";
    private static final int MAX_LOG = 50000;

    private final RootShell shell = new RootShell();
    private final StringBuilder log = new StringBuilder();

    private View statusDot;
    private TextView statusText, terminal;
    private ScrollView scroll;
    private EditText packageInput;
    private Button btnStart, btnKill, btnShell, btnPackages, btnInstall, btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResources().getIdentifier("activity_main", "layout", getPackageName()));

        statusDot = fv("statusDot");
        statusText = (TextView) fv("statusText");
        terminal = (TextView) fv("terminalOutput");
        scroll = (ScrollView) fv("scrollView");
        packageInput = (EditText) fv("packageInput");
        btnStop = (Button) fv("btnStop");
        btnStart = (Button) fv("btnStart");
        btnKill = (Button) fv("btnKill");
        btnShell = (Button) fv("btnShell");
        btnPackages = (Button) fv("btnPackages");
        btnInstall = (Button) fv("btnInstall");

        btnStop.setOnClickListener(v -> {
            shell.stop();
            append("[Stopped]");
            setButtons(true);
        });

        btnStart.setOnClickListener(v -> exec("sh " + BOOT, true));

        btnKill.setOnClickListener(v -> exec("sh " + KILL, true));

        btnShell.setOnClickListener(v -> exec(
            "chroot " + CHROOT + " /usr/bin/env " + ENV
            + " bash -c 'echo \"=== BlackArch Shell ===\""
            + " && uname -a"
            + " && echo \"---\""
            + " && cat /etc/os-release"
            + " && echo \"---\""
            + " && pacman --version"
            + " && echo \"---\""
            + " && echo \"Installed: $(pacman -Q | wc -l) packages\"'",
            false));

        btnPackages.setOnClickListener(v -> installPackages());
        btnInstall.setOnClickListener(v -> installPackages());

        append("BlackArch Manager started.");
        checkStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!shell.isRunning()) checkStatus();
    }

    private void installPackages() {
        String p = packageInput.getText().toString().trim();
        if (p.isEmpty()) { append("[!] Enter package names first."); return; }
        if (!p.matches("[a-zA-Z0-9._+\\-\\s]+")) { append("[!] Invalid package name."); return; }
        exec("chroot " + CHROOT + " /usr/bin/env " + ENV + " pacman -S --noconfirm " + p, false);
    }

    private View fv(String name) {
        return findViewById(getResources().getIdentifier(name, "id", getPackageName()));
    }

    private void setStatus(String text, int color) {
        statusText.setText(text);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        statusDot.setBackground(d);
    }

    private void setButtons(boolean enabled) {
        btnStart.setEnabled(enabled);
        btnKill.setEnabled(enabled);
        btnShell.setEnabled(enabled);
        btnPackages.setEnabled(enabled);
        btnInstall.setEnabled(enabled);
        btnStop.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void exec(String cmd, boolean refresh) {
        if (shell.isRunning()) {
            append("[!] A command is already running.");
            return;
        }
        Log.d(TAG, "exec: " + cmd);
        append("$ " + cmd.replace(CHROOT, "$CHROOT"));
        setButtons(false);
        shell.run(cmd, new RootShell.OutputCallback() {
            @Override public void onOutput(String line) { append(line); }
            @Override public void onFinished(int code) {
                setButtons(true);
                append("[exit " + code + "]");
                if (refresh) checkStatus();
            }
        });
    }

    private void checkStatus() {
        if (shell.isRunning()) return;
        Log.d(TAG, "checkStatus");
        final StringBuilder out = new StringBuilder();
        shell.run(
            "if [ ! -d " + CHROOT + " ]; then echo MISSING; exit 0; fi; "
            + "mountpoint -q " + CHROOT + "/dev && echo MOUNTED || echo UNMOUNTED",
            new RootShell.OutputCallback() {
                @Override public void onOutput(String line) {
                    Log.d(TAG, "status: " + line);
                    out.append(line.trim());
                }
                @Override public void onFinished(int code) {
                    Log.d(TAG, "status exit=" + code + " out=" + out);
                    String s = out.toString();
                    if (s.contains("MISSING")) {
                        setStatus("Chroot not found", Color.parseColor("#e94560"));
                        append("[!] Chroot not found at " + CHROOT + "\n"
                            + "  Extract a BlackArch ARM64 rootfs there (as root)\n"
                            + "  and install bootblackarch + killblackarch to\n"
                            + "  /data/adb/service.d/");
                    } else if (s.contains("MOUNTED")) {
                        setStatus("Running", Color.parseColor("#00ff41"));
                    } else if (s.contains("UNMOUNTED")) {
                        setStatus("Stopped", Color.parseColor("#888888"));
                    } else {
                        setStatus("Unknown (root?)", Color.parseColor("#ffaa00"));
                        append("[!] Could not check status. Root access needed.");
                    }
                    setButtons(true);
                }
            });
    }

    private void append(String line) {
        log.append(line).append('\n');
        if (log.length() > MAX_LOG) log.delete(0, log.length() - MAX_LOG);
        terminal.setText(log);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shell.stop();
    }
}
