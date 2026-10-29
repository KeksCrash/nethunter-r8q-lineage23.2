package de.nethunter.imsicatcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView logOutput;
    private TextView statusText;
    private View statusDot;
    private ScrollView logScroll;
    private Button btnStop;
    private ScriptRunner runner;

    private static final int MAX_LOG_CHARS = 50000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getResources().getIdentifier("activity_main", "layout", getPackageName()));

        logOutput = findViewById(getResId("logOutput"));
        statusText = findViewById(getResId("statusText"));
        statusDot = findViewById(getResId("statusDot"));
        logScroll = findViewById(getResId("logScroll"));
        btnStop = findViewById(getResId("btnStop"));

        runner = new ScriptRunner();

        setupButton("btnScan", "scan");
        setupButton("btnMonitor", "monitor-scan");
        setupButton("btnImsi", "imsi");
        setupButton("btnWifi", "wifi");
        setupButton("btnBt", "bt");
        setupButton("btnDevices", "devices");
        setupButton("btnStatus", "status");

        Button btnClear = findViewById(getResId("btnClear"));
        btnClear.setOnClickListener(v -> {
            logOutput.setText("IMSI Catcher Detection v2\nReady.\n");
        });

        btnStop.setOnClickListener(v -> {
            runner.stop();
            setStatus("Stopped", 0xFF666666);
            btnStop.setEnabled(false);
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String mode = intent.getStringExtra("mode");
        if (mode != null && !mode.isEmpty()) {
            runMode(mode);
        }
    }

    private void setupButton(String idName, String mode) {
        Button btn = findViewById(getResId(idName));
        btn.setOnClickListener(v -> runMode(mode));
    }

    private void runMode(String mode) {
        if (runner.isRunning()) {
            runner.stop();
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        logOutput.append("\n--- " + mode.toUpperCase() + " ---\n");
        setStatus("Running: " + mode, 0xFF00ff41);
        btnStop.setEnabled(true);

        runner.run(this, mode, new ScriptRunner.OutputCallback() {
            @Override
            public void onOutput(String line) {
                logOutput.append(line + "\n");
                trimLog();
                logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            }

            @Override
            public void onFinished(int exitCode) {
                logOutput.append("[exit: " + exitCode + "]\n");
                setStatus("Done (" + exitCode + ")", exitCode == 0 ? 0xFF00ff41 : 0xFFe94560);
                btnStop.setEnabled(false);
            }
        });
    }

    private void setStatus(String text, int color) {
        statusText.setText(text);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setSize(12, 12);
        statusDot.setBackground(dot);
    }

    private void trimLog() {
        CharSequence cs = logOutput.getText();
        if (cs.length() > MAX_LOG_CHARS) {
            logOutput.setText(cs.subSequence(cs.length() - MAX_LOG_CHARS, cs.length()));
        }
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (runner != null) runner.stop();
    }
}
