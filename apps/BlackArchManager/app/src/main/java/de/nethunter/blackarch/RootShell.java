package de.nethunter.blackarch;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class RootShell {

    private static final String TAG = "BlackArch";

    public interface OutputCallback {
        void onOutput(String line);
        void onFinished(int exitCode);
    }

    private Process process;
    private volatile boolean running;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public boolean isRunning() { return running; }

    public void run(String command, OutputCallback callback) {
        if (running) return;
        running = true;

        new Thread(() -> {
            int exitCode = -1;
            try {
                Log.d(TAG, "Running: " + command);
                ProcessBuilder pb = new ProcessBuilder("su");
                pb.redirectErrorStream(true);
                process = pb.start();

                DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
                stdin.writeBytes(command + "\n");
                stdin.writeBytes("exit $?\n");
                stdin.flush();
                stdin.close();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!running) break;
                    final String l = line.replaceAll("\033\\[[0-9;]*m", "");
                    Log.d(TAG, "OUT: " + l);
                    mainHandler.post(() -> callback.onOutput(l));
                }
                exitCode = process.waitFor();
                Log.d(TAG, "Exit: " + exitCode);
            } catch (Exception e) {
                Log.e(TAG, "Run failed", e);
                final String err = e.getMessage();
                mainHandler.post(() -> callback.onOutput("[ERROR] " + err));
            } finally {
                running = false;
                final int code = exitCode;
                mainHandler.post(() -> callback.onFinished(code));
            }
        }).start();
    }

    public void stop() {
        running = false;
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }
}
