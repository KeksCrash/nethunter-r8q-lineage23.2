package de.nethunter.imsicatcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ScriptRunner {

    public interface OutputCallback {
        void onOutput(String line);
        void onFinished(int exitCode);
    }

    private Process process;
    private volatile boolean running;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public boolean isRunning() {
        return running;
    }

    public void run(Context ctx, String mode, OutputCallback callback) {
        if (running) return;
        running = true;

        new Thread(() -> {
            int exitCode = -1;
            try {
                File scriptFile = extractScript(ctx);
                Log.d("IMSI", "Script extracted to: " + scriptFile.getAbsolutePath()
                        + " exists=" + scriptFile.exists() + " size=" + scriptFile.length());
                String cmd = "sh " + scriptFile.getAbsolutePath() + " --" + mode;
                Log.d("IMSI", "Running: su -c " + cmd);

                ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
                pb.redirectErrorStream(true);
                process = pb.start();
                Log.d("IMSI", "Process started");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!running) break;
                    final String l = stripAnsi(line);
                    mainHandler.post(() -> callback.onOutput(l));
                }
                exitCode = process.waitFor();
            } catch (Exception e) {
                Log.e("IMSI", "Run failed", e);
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

    private File extractScript(Context ctx) throws Exception {
        File dir = new File(ctx.getFilesDir(), "scripts");
        dir.mkdirs();
        File out = new File(dir, "imsi-catcher-detect.sh");

        InputStream is = ctx.getAssets().open("imsi-catcher-detect.sh");
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        is.close();
        out.setExecutable(true);

        return out;
    }

    private String stripAnsi(String s) {
        return s.replaceAll("\033\\[[0-9;]*m", "");
    }
}
