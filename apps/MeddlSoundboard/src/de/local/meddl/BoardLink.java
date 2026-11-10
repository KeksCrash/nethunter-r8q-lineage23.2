package de.local.meddl;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

final class BoardLink {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static AudioTrack monitor;

    record State(boolean reachable, String route, boolean pc, boolean clip, String mic) {
        boolean active() { return !route.equals("off"); }

        boolean micEnabled() {
            return mic.equals("on") || mic.equals("starting") || mic.equals("muted");
        }

        String micLabel() {
            return switch (mic) {
                case "on" -> "Handy-Mikrofon · aktiv";
                case "starting" -> "Handy-Mikrofon · startet …";
                case "muted" -> "Handy-Mikrofon · von Android stumm";
                case "error" -> "Handy-Mikrofon · nicht verfügbar";
                default -> "Handy-Mikrofon · aus";
            };
        }

        String label() {
            if (!reachable) return "Brücke offline";
            return switch (route) {
                case "global" -> "Systemweit aktiv";
                case "tiktok" -> "Nur TikTok aktiv";
                default -> "Brücke bereit · Mikrofon aus";
            };
        }
    }

    static boolean global(Context context) {
        return context.getSharedPreferences("bridge", 0).getBoolean("global", true);
    }

    static void selectGlobal(Context context, boolean global) {
        context.getSharedPreferences("bridge", 0).edit().putBoolean("global", global).apply();
    }

    static void state(Context context, Consumer<State> result) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            State state = new State(false, "off", false, false, "off");
            try {
                String[] fields = send(app, "state", null).split("\\|");
                if ((fields.length == 3 || fields.length == 4) && fields[0].matches("global|tiktok|off")) {
                    state = new State(true, fields[0], Boolean.parseBoolean(fields[1]),
                                      Boolean.parseBoolean(fields[2]), fields.length == 4 ? fields[3] : "off");
                }
            } catch (IOException ignored) {
            }
            State snapshot = state;
            MAIN.post(() -> result.accept(snapshot));
        });
    }

    static boolean monitorEnabled(Context context) {
        return context.getSharedPreferences("bridge", 0).getBoolean("monitor", true);
    }

    static void setMonitorEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("bridge", 0).edit().putBoolean("monitor", enabled).apply();
        if (!enabled) stopMonitor();
    }

    static void request(Context context, String command, ClipStore.Clip clip, Consumer<String> result) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            byte[] pcm = null;
            String message;
            try {
                pcm = clip == null ? null : clip.read(app);
                String wire = command;
                if (command.equals("clip") || command.equals("start") || command.equals("mic-on")) {
                    wire += global(app) ? "-global" : "-tiktok";
                }
                message = send(app, wire, pcm);
            } catch (IOException e) {
                message = "Audiobrücke nicht erreichbar.";
            }
            byte[] preview = pcm;
            String reply = message;
            MAIN.post(() -> {
                if (preview != null && monitorEnabled(app)) {
                    try {
                        playMonitor(preview);
                    } catch (RuntimeException error) {
                        result.accept(reply + " · Mithören fehlgeschlagen.");
                        return;
                    }
                } else if (command.equals("stop")) {
                    stopMonitor();
                }
                result.accept(reply);
            });
        });
    }

    private static String send(Context context, String command, byte[] pcm) throws IOException {
        String token = context.getSharedPreferences("bridge", 0).getString("token", "");
        if (token.isEmpty()) return "Audiobrücke ist noch nicht eingerichtet.";
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 39771), 1500);
            socket.setSoTimeout(5000);
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeUTF(token);
            out.writeUTF(command);
            if (pcm != null) {
                out.writeInt(pcm.length);
                out.write(pcm);
            }
            out.flush();
            return in.readUTF();
        }
    }

    private static void playMonitor(byte[] pcm) {
        stopMonitor();
        var track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(ClipStore.RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.length)
            .build();
        try {
            if (track.write(pcm, 0, pcm.length) != pcm.length) {
                throw new IllegalStateException("Unvollständige Wiedergabe");
            }
            track.setVolume(0.75f);
            track.play();
            monitor = track;
        } catch (RuntimeException error) {
            track.release();
            throw error;
        }
        MAIN.postDelayed(() -> {
            if (monitor == track) stopMonitor();
        }, pcm.length * 1000L / (2L * ClipStore.RATE) + 200);
    }

    private static void stopMonitor() {
        if (monitor != null) {
            monitor.release();
            monitor = null;
        }
    }
}
