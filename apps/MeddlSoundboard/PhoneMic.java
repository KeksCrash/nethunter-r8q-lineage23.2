import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

final class PhoneMic {
    private static final int RATE = 48000;
    private final int frameSize;
    private final ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(3);
    private volatile boolean enabled;
    private volatile String state = "off";
    private Thread reader;

    PhoneMic(int frameSize) {
        this.frameSize = frameSize;
    }

    synchronized void start() throws InterruptedException {
        if (reader != null && reader.isAlive()) {
            if (enabled) return;
            reader.join(450);
            if (reader.isAlive()) {
                state = "error";
                return;
            }
        }
        frames.clear();
        enabled = true;
        state = "starting";
        reader = new Thread(this::capture, "Meddl-Handymikrofon");
        reader.start();
    }

    synchronized void stop() {
        enabled = false;
        state = "off";
        frames.clear();
        if (reader != null) reader.interrupt();
    }

    byte[] poll() {
        return enabled ? frames.poll() : null;
    }

    String state() {
        return state;
    }

    private void capture() {
        AudioRecord record = null;
        try {
            var devices = (AudioDeviceInfo[]) AudioManager.class.getMethod("getDevicesStatic", int.class)
                .invoke(null, AudioManager.GET_DEVICES_INPUTS);
            var builtin = Arrays.stream(devices)
                .filter(device -> device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC)
                .findFirst().orElseThrow(() -> new IllegalStateException("Kein internes Mikrofon"));
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                                                   AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder()
                // Use the virtual preset for Android's concurrency policy.
                // Only frames actually routed from the internal hardware mic enter the mix.
                .setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX)
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(frameSize * 4, min))
                .build();
            if (!record.setPreferredDevice(builtin)) {
                throw new IllegalStateException("Handy-Mikrofon kann nicht ausgewählt werden");
            }
            if (!enabled) return;
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Handy-Mikrofon startet nicht");
            }
            byte[] frame = new byte[frameSize];
            int offset = 0;
            long routeDeadline = System.nanoTime() + 1_200_000_000L;
            long configCheck = 0;
            boolean silenced = false;
            while (enabled) {
                int count = record.read(frame, offset, frame.length - offset, AudioRecord.READ_NON_BLOCKING);
                if (count < 0) throw new IllegalStateException("AudioRecord.read: " + count);
                if (count == 0) {
                    Thread.sleep(4);
                    continue;
                }
                offset += count;
                if (offset < frame.length) continue;
                offset = 0;
                AudioDeviceInfo routed = record.getRoutedDevice();
                if (routed == null || routed.getType() != AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    frames.clear();
                    state = "starting";
                    if (System.nanoTime() > routeDeadline) {
                        throw new IllegalStateException("Hardware-Mikrofonroute fehlt");
                    }
                    continue;
                }
                routeDeadline = System.nanoTime() + 1_200_000_000L;
                long now = System.nanoTime();
                if (now >= configCheck) {
                    var config = record.getActiveRecordingConfiguration();
                    silenced = config != null && config.isClientSilenced();
                    configCheck = now + 150_000_000L;
                }
                if (silenced) {
                    frames.clear();
                    state = "muted";
                    continue;
                }
                if (!enabled) break;
                state = "on";
                if (!frames.offer(frame)) {
                    frames.poll();
                    frames.offer(frame);
                }
                frame = new byte[frameSize];
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (enabled) {
                state = "error";
                System.err.println("Handy-Mikrofon: " + error);
            }
        } finally {
            if (record != null) record.release();
            enabled = false;
            frames.clear();
        }
    }
}
