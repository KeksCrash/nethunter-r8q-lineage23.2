package de.local.meddl;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

final class ClipStore {
    static final int RATE = 48000;
    private static final int SECONDS = 30;
    private static final int MAX_INPUT = 32 * 1024 * 1024;
    static final String UNSORTED = "Unsortiert";
    private static final Set<String> INITIAL_GROUPS = Set.of(
        "Drachenlord · Klassiker", "Drachenlord · Sprüche", "Drachenlord · KI", "Andreas", "Importe");
    private static final Set<String> HOSTS = Set.of(
        "myinstants.com", "www.myinstants.com", "instantsbutton.com",
        "www.instantsbutton.com", "storage.instantsbutton.com");
    private static final Pattern AUDIO = Pattern.compile(
        "<meta\\s+property=[\"']og:audio[\"']\\s+content=[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);

    record Clip(String title, String id, boolean builtin) {
        byte[] read(Context context) throws IOException {
            try (var in = builtin ? context.getAssets().open(id)
                                  : new FileInputStream(new File(context.getFilesDir(), id))) {
                return in.readAllBytes();
            }
        }
    }

    static List<Clip> list(Context context) {
        var clips = new ArrayList<>(List.of(new Clip("Meddl Leute", "metall-leute.pcm", true),
            new Clip("Etzela", "etzela.pcm", true), new Clip("A Frosch", "a-frosch.pcm", true),
            new Clip("Andreas · Halt Stopp!", "halt-stopp.pcm", true),
            new Clip("Andreas · Halt Stopp! (Variante)", "halt-stopp-variante.pcm", true),
            new Clip("Andreas · Alles bleibt so, wie es ist", "alles-bleibt.pcm", true)));
        try {
            var assets = new HashSet<>(Arrays.asList(context.getAssets().list("")));
            for (Clip clip : List.of(
                    new Clip("100 Sachen 80", "100-sachen-80.pcm", true),
                    new Clip("Was zitterstn so?", "was-zitterstn-so.pcm", true),
                    new Clip("Das glaub ich dir gerne", "das-glaub-ich-dir-gerne.pcm", true),
                    new Clip("Hagebuddne!", "hagebuddne_fmRCw67.pcm", true),
                    new Clip("Ja oder nein", "ja-oder-nein.pcm", true),
                    new Clip("So Leude, Forza", "so-leude-forza.pcm", true),
                    new Clip("Der wollte seine 7 abgeben", "ki-ronaldo.pcm", true),
                    new Clip("Auf der anderen Seite stand noch eins", "ki-ronaldo-anderes-tor.pcm", true),
                    new Clip("Ich spiel gar kein Fußball", "ki-ronaldo-kein-fussball.pcm", true))) {
                if (assets.contains(clip.id())) clips.add(clip);
            }
        } catch (IOException ignored) {
            // The mandatory clips are already listed.
        }
        var custom = new ArrayList<Clip>();
        for (var entry : context.getSharedPreferences("clips", 0).getAll().entrySet()) {
            if (new File(context.getFilesDir(), entry.getKey()).isFile()) {
                custom.add(new Clip(entry.getValue().toString(), entry.getKey(), false));
            }
        }
        custom.sort(Comparator.comparing(Clip::title, String.CASE_INSENSITIVE_ORDER));
        clips.addAll(custom);
        var library = context.getSharedPreferences("library", 0);
        clips.removeIf(clip -> library.getBoolean("hidden:" + clip.id(), false));
        return clips;
    }

    static List<String> categories(Context context) {
        var stored = context.getSharedPreferences("library", 0).getStringSet("categories", INITIAL_GROUPS);
        var categories = new ArrayList<>(stored);
        categories.sort(String.CASE_INSENSITIVE_ORDER);
        categories.add(0, UNSORTED);
        return categories;
    }

    private static String category(Context context, Clip clip, List<String> categories) {
        String initial = !clip.builtin() ? "Importe"
            : clip.title().startsWith("Andreas ·") ? "Andreas"
            : clip.id().startsWith("ki-") ? "Drachenlord · KI"
            : Set.of("metall-leute.pcm", "100-sachen-80.pcm", "so-leude-forza.pcm").contains(clip.id())
                ? "Drachenlord · Klassiker" : "Drachenlord · Sprüche";
        String selected = context.getSharedPreferences("library", 0).getString("category:" + clip.id(), initial);
        return categories.contains(selected) ? selected : UNSORTED;
    }

    static Map<String, List<Clip>> groups(Context context) {
        List<String> categories = categories(context);
        var groups = new LinkedHashMap<String, List<Clip>>();
        for (String name : categories) groups.put(name, new ArrayList<>());
        for (Clip clip : list(context)) groups.get(category(context, clip, categories)).add(clip);
        return groups;
    }

    static void move(Context context, Clip clip, String category) {
        if (!categories(context).contains(category)) throw new IllegalArgumentException("Kategorie fehlt");
        context.getSharedPreferences("library", 0).edit().putString("category:" + clip.id(), category).apply();
    }

    static void editCategory(Context context, String previous, String next) {
        List<String> categories = categories(context);
        if (UNSORTED.equals(previous)) throw new IllegalArgumentException("Unsortiert bleibt erhalten");
        if (next != null) {
            next = next.strip();
            if (next.isEmpty() || next.length() > 48) throw new IllegalArgumentException("1 bis 48 Zeichen eingeben");
            if (next.equals(previous)) return;
            for (String existing : categories) {
                if (!existing.equals(previous) && existing.equalsIgnoreCase(next)) {
                    throw new IllegalArgumentException("Diese Kategorie gibt es bereits");
                }
            }
        }
        var edit = context.getSharedPreferences("library", 0).edit();
        if (previous != null) {
            for (Clip clip : groups(context).getOrDefault(previous, List.of())) {
                edit.putString("category:" + clip.id(), next == null ? UNSORTED : next);
            }
            categories.remove(previous);
        }
        categories.remove(UNSORTED);
        if (next != null) categories.add(next);
        edit.putStringSet("categories", new HashSet<>(categories)).apply();
    }

    static synchronized void remove(Context context, Clip clip) {
        var edit = context.getSharedPreferences("library", 0).edit().remove("category:" + clip.id());
        if (clip.builtin()) {
            edit.putBoolean("hidden:" + clip.id(), true).apply();
            return;
        }
        edit.apply();
        context.getSharedPreferences("clips", 0).edit().remove(clip.id()).apply();
        new File(context.getFilesDir(), clip.id()).delete();
    }

    private static String fingerprint(byte[] pcm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pcm));
    }

    static synchronized int removeDuplicates(Context context) throws Exception {
        var seen = new HashSet<String>();
        var duplicates = new ArrayList<Clip>();
        for (Clip clip : list(context)) {
            if (!seen.add(fingerprint(clip.read(context)))) duplicates.add(clip);
        }
        for (Clip clip : duplicates) remove(context, clip);
        return duplicates.size();
    }

    static String fromFile(Context context, Uri uri) throws Exception {
        String title = "Eigener Sound";
        var resolver = context.getContentResolver();
        try (var result = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (result != null && result.moveToFirst() && result.getString(0) != null) {
                title = result.getString(0);
            }
        }
        try (var in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Datei ist nicht lesbar");
            return importClip(context, title, in);
        }
    }

    static String fromLink(Context context, String link) throws Exception {
        URI uri = checkedUri(link.strip());
        if (!uri.getPath().matches("(?i).*\\.(mp3|wav|ogg|m4a|aac|flac|opus)$")) {
            String html;
            try (InputStream in = download(uri)) {
                html = new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
            }
            var match = AUDIO.matcher(html);
            if (!match.find()) throw new IOException("Auf der Seite fehlt eine Audiodatei");
            uri = checkedUri(uri.resolve(match.group(1).replace("&amp;", "&")).toString());
        }
        String name = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1).replace('-', ' ');
        try (InputStream in = download(uri)) {
            return importClip(context, name, in);
        }
    }

    private static URI checkedUri(String address) throws IOException {
        try {
            URI uri = URI.create(address);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IOException("Bitte einen HTTPS-Link von Myinstants oder InstantsButton verwenden");
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new IOException("Ungültiger Link", error);
        }
    }

    private static InputStream download(URI uri) throws IOException {
        for (int redirect = 0; redirect < 4; redirect++) {
            var connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String target = connection.getHeaderField("Location");
                connection.disconnect();
                if (target == null) break;
                uri = checkedUri(uri.resolve(target).toString());
            } else if (status == 200) {
                return new FilterInputStream(connection.getInputStream()) {
                    @Override public void close() throws IOException {
                        try { super.close(); } finally { connection.disconnect(); }
                    }
                };
            } else {
                connection.disconnect();
                throw new IOException("Download fehlgeschlagen (HTTP " + status + ")");
            }
        }
        throw new IOException("Audio-Download konnte nicht geöffnet werden");
    }

    private static String importClip(Context context, String title, InputStream in) throws Exception {
        File source = File.createTempFile("sound-", ".audio", context.getCacheDir());
        try {
            try (var out = new FileOutputStream(source)) {
                byte[] buffer = new byte[16384];
                int total = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_INPUT) throw new IOException("Datei ist größer als 32 MB");
                    out.write(buffer, 0, n);
                }
            }
            byte[] pcm = decode(source);
            String label = title.replaceFirst("\\.[^.]{1,5}$", "").strip();
            if (label.isEmpty()) label = "Eigener Sound";
            if (label.length() > 70) label = label.substring(0, 70);
            return save(context, label, pcm);
        } finally {
            source.delete();
        }
    }

    private static synchronized String save(Context context, String label, byte[] pcm) throws Exception {
        String digest = fingerprint(pcm);
        for (Clip clip : list(context)) {
            if (digest.equals(fingerprint(clip.read(context)))) {
                throw new IOException("Bereits vorhanden: " + clip.title());
            }
        }
        String id = "clip-" + UUID.randomUUID() + ".pcm";
        File target = new File(context.getFilesDir(), id);
        Files.write(target.toPath(), pcm);
        if (!context.getSharedPreferences("clips", 0).edit().putString(id, label).commit()) {
            target.delete();
            throw new IOException("Sound konnte nicht gespeichert werden");
        }
        return label;
    }

    private static byte[] decode(File source) throws Exception {
        var extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                var candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    format = candidate;
                    break;
                }
            }
            if (format == null) throw new IOException("Kein unterstützter Audiotrack gefunden");
            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();
            var output = new ByteArrayOutputStream();
            var info = new MediaCodec.BufferInfo();
            boolean fedAll = false;
            boolean drained = false;
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (!drained && output.size() < rate * SECONDS * 2) {
                if (System.nanoTime() > deadline) throw new IOException("Zeitlimit beim Dekodieren");
                if (!fedAll) {
                    int slot = decoder.dequeueInputBuffer(10000);
                    if (slot >= 0) {
                        var buffer = decoder.getInputBuffer(slot);
                        buffer.clear();
                        int n = extractor.readSampleData(buffer, 0);
                        fedAll = n < 0;
                        decoder.queueInputBuffer(slot, 0, Math.max(0, n),
                            fedAll ? 0 : extractor.getSampleTime(),
                            fedAll ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        if (!fedAll) extractor.advance();
                    }
                }
                int slot = decoder.dequeueOutputBuffer(info, 10000);
                if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = decoder.getOutputFormat();
                    rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding = decoded.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                } else if (slot >= 0) {
                    ByteBuffer buffer = decoder.getOutputBuffer(slot).order(ByteOrder.LITTLE_ENDIAN);
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        appendMono(output, buffer, channels, encoding, rate * SECONDS * 2);
                    }
                    drained = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(slot, false);
                }
            }
            if (output.size() < 4) throw new IOException("Die Datei enthält kein abspielbares Audio");
            return resample(output.toByteArray(), rate);
        } finally {
            if (decoder != null) decoder.release();
            extractor.release();
        }
    }

    private static void appendMono(ByteArrayOutputStream out, ByteBuffer pcm, int channels,
                                   int encoding, int limit) throws IOException {
        boolean floating = encoding == AudioFormat.ENCODING_PCM_FLOAT;
        if (!floating && encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw new IOException("Das PCM-Format wird nicht unterstützt");
        }
        if (channels <= 0) throw new IOException("Ungültige Kanalzahl");
        int frame = channels * (floating ? 4 : 2);
        while (pcm.remaining() >= frame && out.size() < limit) {
            double sum = 0;
            for (int i = 0; i < channels; i++) {
                sum += floating ? pcm.getFloat() * 32768 : pcm.getShort();
            }
            int sample = Math.max(-32768, Math.min(32767, (int) (sum / channels)));
            out.write(sample & 255);
            out.write((sample >> 8) & 255);
        }
    }

    private static byte[] resample(byte[] source, int rate) {
        if (rate == RATE) return source;
        int count = source.length / 2;
        int output = (int) ((long) count * RATE / rate);
        var from = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        var to = ByteBuffer.allocate(output * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < output; i++) {
            double at = (double) i * rate / RATE;
            int base = (int) at;
            double fraction = at - base;
            int a = from.get(base);
            int b = from.get(Math.min(base + 1, count - 1));
            to.putShort((short) Math.round(a + (b - a) * fraction));
        }
        return to.array();
    }
}
