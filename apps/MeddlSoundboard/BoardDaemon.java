import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public final class BoardDaemon {
    private static final int PORT = 39771;
    private static final int FRAME = 1920;
    private static final int MAX_CLIP = 30 * 48000 * 2;
    private static final String HOST = "127.0.0.1";
    private static final Path TOKEN = Path.of("/data/local/tmp/meddl-soundboard/token");
    private static final ExecutorService CLIENTS = Executors.newFixedThreadPool(4);
    private final int uid;
    private final ArrayBlockingQueue<byte[]> pc = new ArrayBlockingQueue<>(4);
    private final java.util.concurrent.atomic.AtomicBoolean pcConnected =
        new java.util.concurrent.atomic.AtomicBoolean();
    private int route = AudioBridge.GLOBAL_MIC;
    private MixStream mixer;
    private volatile boolean ready;
    private Thread injector;

    private BoardDaemon(int uid) {
        this.uid = uid;
    }

    public static void main(String[] args) throws Exception {
        String secret = Files.readString(TOKEN).strip();
        if (!secret.matches("[0-9a-f]{64}")) throw new IOException("Ungültiger Installationsschlüssel");
        if (args.length == 2 && args[0].equals("serve")) {
            int uid = Integer.parseInt(args[1]);
            if (uid != 2000 && uid < 10000) throw new IllegalArgumentException("Ungültige Ziel-UID");
            android.os.Looper.prepareMainLooper();
            new BoardDaemon(uid).serve(secret);
        } else if (args.length == 1 && args[0].matches("start(-global|-tiktok)?|mic-on(-global|-tiktok)?|mic-off|stop|status|state|pcm")) {
            client(secret, args[0]);
        } else {
            throw new IllegalArgumentException("serve UID | start-global | start-tiktok | stop | status | state | pcm");
        }
    }

    private void serve(String secret) throws IOException {
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(HOST, PORT), 8);
            System.err.println("Meddl-Soundboard bereit, Ziel-UID " + uid);
            while (true) {
                Socket socket = server.accept();
                CLIENTS.execute(() -> handle(socket, secret));
            }
        }
    }

    private void handle(Socket socket, String secret) {
        try (socket;
             var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            socket.setSoTimeout(4000);
            if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII),
                                       in.readUTF().getBytes(StandardCharsets.US_ASCII))) return;
            String command = in.readUTF();
            switch (command) {
                case "start", "start-global", "start-tiktok" -> {
                    start(target(command));
                    reply(out, routeMessage());

                }
                case "stop" -> {
                    stop();
                    reply(out, "Einspeisung beendet");
                }
                case "mic-on", "mic-on-global", "mic-on-tiktok" -> {
                    reply(out, startMic(target(command)));
                }
                case "mic-off" -> {
                    stopMic();
                    reply(out, "Handy-Mikrofon aus");
                }
                case "status" -> reply(out, routeMessage());
                case "state" -> reply(out, state());
                case "clip", "clip-global", "clip-tiktok" -> {
                    int size = in.readInt();
                    if (size <= 0 || size > MAX_CLIP || size % 2 != 0) {
                        throw new IOException("Ungültige Clipgröße");
                    }
                    byte[] clip = new byte[size];
                    in.readFully(clip);
                    start(target(command)).play(clip);
                    reply(out, "Clip wird eingespeist");
                }
                case "pcm" -> {
                    if (!pcConnected.compareAndSet(false, true)) {
                        reply(out, "Fehler: PC-Audiostream läuft bereits");
                        return;
                    }
                    try {
                        startForPc();
                        reply(out, "PC-Audio verbunden");
                        while (true) {
                            byte[] frame = in.readNBytes(FRAME);
                            if (frame.length != FRAME) break;
                            if (!pc.offer(frame)) {
                                pc.poll();
                                pc.offer(frame);
                            }
                        }
                    } finally {
                        pc.clear();
                        pcConnected.set(false);
                    }
                }
                default -> reply(out, "Fehler: unbekannter Befehl");
            }
        } catch (IOException error) {
            if (!(error instanceof EOFException)) System.err.println(error.getMessage());
        } catch (Exception error) {
            System.err.println(error);
        }
    }

    private static void reply(DataOutputStream out, String text) throws IOException {
        out.writeUTF(text);
        out.flush();
    }

    private int target(String command) {
        return command.endsWith("-tiktok") ? uid : AudioBridge.GLOBAL_MIC;
    }

    private synchronized void startForPc() throws Exception {
        if (!ready) start(route);
    }

    private synchronized String routeMessage() {
        if (!ready) return "Brücke bereit";
        return route == AudioBridge.GLOBAL_MIC ? "Systemweites Mikrofon aktiv" : "TikTok-Mikrofon aktiv";
    }

    private synchronized String startMic(int target) throws Exception {
        boolean opensRoute = !ready;
        if (opensRoute) start(target);
        PhoneMic mic = mixer.phoneMic;
        mic.start();
        if (!opensRoute) return "Handy-Mikrofon wird zugeschaltet";
        long deadline = System.nanoTime() + 1_800_000_000L;
        while (System.nanoTime() < deadline && mic.state().matches("starting|muted")) {
            Thread.sleep(40);
        }
        if (mic.state().equals("on")) return "Handy-Mikrofon aktiv";
        stop();
        return "Handy-Mikrofon nicht verfügbar. Normale Mikrofonroute wieder frei.";
    }

    private synchronized void stopMic() {
        if (mixer != null) mixer.phoneMic.stop();
    }

    private synchronized String state() {
        String active = !ready ? "off" : route == AudioBridge.GLOBAL_MIC ? "global" : "tiktok";
        return active + "|" + pcConnected.get() + "|" + (ready && mixer.isPlaying())
            + "|" + (ready ? mixer.phoneMic.state() : "off");
    }

    private synchronized MixStream start(int target) throws Exception {
        if (ready && route == target && mixer != null && !mixer.closed) return mixer;
        stop();
        route = target;
        pc.clear();
        MixStream next = new MixStream(pc);
        CountDownLatch registered = new CountDownLatch(1);
        mixer = next;
        injector = new Thread(() -> {
            try {
                AudioBridge.injectStream(next, target, 0, () -> {
                    ready = true;
                    registered.countDown();
                });
            } catch (Exception error) {
                System.err.println(error);
            } finally {
                ready = false;
                next.close();
                registered.countDown();
            }
        }, "Meddl-Einspeisung");
        injector.start();
        if (!registered.await(3, TimeUnit.SECONDS) || !ready) {
            stop();
            throw new IOException("AudioPolicy konnte nicht gestartet werden");
        }
        return next;
    }

    private synchronized void stop() throws InterruptedException, IOException {
        if (mixer == null) return;
        mixer.close();
        injector.join(2000);
        if (injector.isAlive()) throw new IOException("AudioPolicy wird noch beendet");
        mixer = null;
    }

    private static void client(String secret, String command) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             var in = new DataInputStream(socket.getInputStream());
             var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            socket.setSoTimeout(5000);
            out.writeUTF(secret);
            out.writeUTF(command);
            out.flush();
            String status = in.readUTF();
            System.err.println(status);
            if (status.startsWith("Fehler:")) throw new IOException(status);
            if (command.equals("pcm")) {
                byte[] buffer = new byte[FRAME];
                int n;
                while ((n = System.in.readNBytes(buffer, 0, buffer.length)) == FRAME) {
                    out.write(buffer, 0, n);
                    out.flush();
                }
            }
        }
    }

    private static final class MixStream extends InputStream {
        private final ArrayBlockingQueue<byte[]> pc;
        private final PhoneMic phoneMic = new PhoneMic(FRAME);
        private byte[] clip;
        private int clipOffset;
        private long deadline;
        private volatile boolean closed;

        MixStream(ArrayBlockingQueue<byte[]> pc) {
            this.pc = pc;
        }

        synchronized boolean isPlaying() {
            return clip != null;
        }

        synchronized void play(byte[] data) {
            clip = data;
            clipOffset = 0;
        }

        @Override public int read() throws IOException {
            throw new IOException("PCM erfordert Blocklesezugriff");
        }

        @Override public int read(byte[] out, int offset, int count) throws IOException {
            if (count == 0) return 0;
            if (count != FRAME) throw new IOException("Ungültige PCM-Blockgröße");
            if (closed) return -1;
            long now = System.nanoTime();
            deadline = Math.max(deadline + 20_000_000L, now);
            while (!closed && (now = System.nanoTime()) < deadline) {
                LockSupport.parkNanos(deadline - now);
            }
            if (closed) return -1;
            byte[] speech = pc.poll();
            byte[] local = phoneMic.poll();
            Arrays.fill(out, offset, offset + count, (byte) 0);
            synchronized (this) {
                for (int i = 0; i < count; i += 2) {
                    int sample = speech == null ? 0 : pcm(speech, i) * 3 / 4;
                    if (local != null) sample += pcm(local, i) * 3 / 4;
                    if (clip != null) {
                        sample += pcm(clip, clipOffset) * 3 / 4;
                        clipOffset += 2;
                        if (clipOffset == clip.length) clip = null;
                    }
                    sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
                    out[offset + i] = (byte) sample;
                    out[offset + i + 1] = (byte) (sample >> 8);
                }
            }
            return count;
        }

        private static int pcm(byte[] source, int at) {
            return (short) ((source[at] & 255) | (source[at + 1] << 8));
        }

        @Override public void close() {
            closed = true;
            phoneMic.stop();
        }
    }
}
