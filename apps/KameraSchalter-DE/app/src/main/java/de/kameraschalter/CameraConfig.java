package de.kameraschalter;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CameraConfig {
    interface Done { void result(boolean ok, String message); }
    private static final String PATH = "/sdcard/DCIM/Camera1/cs_config.json";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static void switchCamera(boolean real, Done done) {
        WORK.execute(() -> {
            boolean ok = false; String message;
            try {
                String original = runRead();
                if (original.trim().isEmpty()) throw new IOException("CamSwap-Konfiguration fehlt: " + PATH);
                JSONObject config = new JSONObject(original);
                config.put("use_original_camera", real);
                writeRoot(config.toString());
                String after = runRead();
                if (new JSONObject(after).optBoolean("use_original_camera", !real) != real)
                    throw new IOException("Konfiguration konnte nicht bestätigt werden");
                ok = true;
                message = real ? "Echte Kamera ausgewählt" : "Virtuelle Kamera ausgewählt";
            } catch (Exception e) { message = "Fehler: " + e.getMessage(); }
            final boolean success = ok; final String msg = message;
            MAIN.post(() -> done.result(success, msg));
        });
    }
    private static String runRead() throws Exception {
        Process p = new ProcessBuilder("su", "-c", "cat " + PATH).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf,0,n);
        }
        if (p.waitFor() != 0) throw new IOException(out.toString("UTF-8"));
        return out.toString("UTF-8");
    }
    private static void writeRoot(String json) throws Exception {
        // Avoid shell interpolation of JSON. su reads the contents through stdin.
        Process p = new ProcessBuilder("su", "-c", "cat > " + PATH).redirectErrorStream(true).start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.write('\n');
        }
        if (p.waitFor() != 0) throw new IOException("Root-Schreibzugriff fehlgeschlagen");
    }
}
