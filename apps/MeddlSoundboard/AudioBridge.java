import java.io.InputStream;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Looper;

public final class AudioBridge {
    private static final int RATE = 48000;
    private static final int PCM = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SHELL_UID = 2000;
    static final int GLOBAL_MIC = -1;
    private static final int[] MICROPHONE_SOURCES = {
        MediaRecorder.AudioSource.DEFAULT, MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.VOICE_PERFORMANCE
    };

    public static void main(String[] args) {
        try {
            if (args.length < 2 || args.length > 3) {
                throw new IllegalArgumentException("inject UID [SEKUNDEN] oder capture SEKUNDEN [QUELLE]");
            }
            Looper.prepareMainLooper();
            int value = Integer.parseInt(args[1]);
            if (args[0].equals("inject") && (value == SHELL_UID || value >= 10000)) {
                int seconds = args.length == 3 ? Integer.parseInt(args[2]) : 0;
                if (seconds < 0 || seconds > 15) {
                    throw new IllegalArgumentException("Ungültige Testdauer");
                }
                injectStream(System.in, value, seconds, () -> {});
            } else if (args[0].equals("capture") && value >= 1 && value <= 15) {
                int source = args.length == 3 ? Integer.parseInt(args[2]) : MediaRecorder.AudioSource.MIC;
                if (java.util.Arrays.stream(MICROPHONE_SOURCES).noneMatch(item -> item == source)) {
                    throw new IllegalArgumentException("Ungültige Aufnahmequelle");
                }
                capture(value, source);
            } else {
                throw new IllegalArgumentException("Ungültiger Modus oder Wert");
            }
        } catch (Exception error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
        System.exit(0);
    }

    static void injectStream(InputStream input, int uid, int seconds, Runnable ready) throws Exception {
        String prefix = "android.media.audiopolicy.";
        Class<?> ruleType = Class.forName(prefix + "AudioMixingRule");
        Class<?> ruleBuilderType = Class.forName(prefix + "AudioMixingRule$Builder");
        Object ruleBuilder = ruleBuilderType.getConstructor().newInstance();
        ruleBuilderType.getMethod("setTargetMixRole", int.class).invoke(
            ruleBuilder, ruleType.getField("MIX_ROLE_INJECTOR").getInt(null));
        if (uid == GLOBAL_MIC) {
            var addRule = ruleBuilderType.getMethod("addRule", AudioAttributes.class, int.class);
            var setPreset = AudioAttributes.Builder.class.getMethod("setInternalCapturePreset", int.class);
            int presetRule = ruleType.getField("RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET").getInt(null);
            for (int source : MICROPHONE_SOURCES) {
                var attributes = new AudioAttributes.Builder();
                setPreset.invoke(attributes, source);
                addRule.invoke(ruleBuilder, attributes.build(), presetRule);
            }
        } else {
            ruleBuilderType.getMethod("addMixRule", int.class, Object.class).invoke(
                ruleBuilder, ruleType.getField("RULE_MATCH_UID").getInt(null), uid);
        }
        Object rule = ruleBuilderType.getMethod("build").invoke(ruleBuilder);

        Class<?> mixType = Class.forName(prefix + "AudioMix");
        Class<?> mixBuilderType = Class.forName(prefix + "AudioMix$Builder");
        Object mixBuilder = mixBuilderType.getConstructor(ruleType).newInstance(rule);
        mixBuilderType.getMethod("setRouteFlags", int.class).invoke(
            mixBuilder, mixType.getField("ROUTE_FLAG_LOOP_BACK").getInt(null));
        mixBuilderType.getMethod("setFormat", AudioFormat.class).invoke(
            mixBuilder, monoFormat());
        Object mix = mixBuilderType.getMethod("build").invoke(mixBuilder);

        Class<?> policyType = Class.forName(prefix + "AudioPolicy");
        Class<?> policyBuilderType = Class.forName(prefix + "AudioPolicy$Builder");
        Object policyBuilder = policyBuilderType.getConstructor(Context.class).newInstance((Object) null);
        policyBuilderType.getMethod("addMix", mixType).invoke(policyBuilder, mix);
        Object policy = policyBuilderType.getMethod("build").invoke(policyBuilder);

        var register = AudioManager.class.getDeclaredMethod("registerAudioPolicyStatic", policyType);
        register.setAccessible(true);
        int status = (int) register.invoke(null, policy);
        if (status != 0) {
            throw new IllegalStateException("AudioPolicy-Registrierung: " + status);
        }
        try {
            AudioTrack track = (AudioTrack) policyType.getMethod("createAudioTrackSource", mixType)
                .invoke(policy, mix);
            if (track == null) {
                throw new IllegalStateException("AudioPolicy hat keinen AudioTrack bereitgestellt");
            }
            try {
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioTrack konnte nicht initialisiert werden");
                }
                track.play();
                System.err.println((uid == GLOBAL_MIC ? "AudioPolicy systemweit bereit"
                    : "AudioPolicy bereit für UID " + uid) + ", PCM16LE mono 48000 Hz");
                ready.run();
                byte[] buffer = new byte[1920];
                long remaining = seconds == 0 ? Long.MAX_VALUE : seconds * RATE * 2L;
                while (remaining > 0) {
                    int bytes = input.readNBytes(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (bytes == 0) {
                        break;
                    }
                    remaining -= bytes;
                    if ((bytes & 1) != 0) {
                        throw new IllegalStateException("Unvollständiges PCM16-Sample");
                    }
                    int written = 0;
                    // Don't accumulate seconds of stale audio while no recorder is active.
                    long deadline = System.nanoTime() + 18_000_000L;
                    while (written < bytes && System.nanoTime() < deadline) {
                        int n = track.write(buffer, written, bytes - written, AudioTrack.WRITE_NON_BLOCKING);
                        if (n < 0) {
                            throw new IllegalStateException("AudioTrack.write: " + n);
                        }
                        if (n == 0) {
                            Thread.sleep(3);
                        }
                        written += n;
                    }
                }
                track.pause();
                track.flush();
            } finally {
                track.release();
            }
        } finally {
            var unregister = AudioManager.class.getDeclaredMethod("unregisterAudioPolicyAsyncStatic", policyType);
            unregister.setAccessible(true);
            unregister.invoke(null, policy);
        }
    }

    private static AudioFormat monoFormat() {
        return new AudioFormat.Builder().setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(PCM).build();
    }

    private static void capture(int seconds, int source) {
        AudioRecord record = new AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(monoFormat())
            .setBufferSizeInBytes(RATE / 2)
            .build();
        try {
            record.startRecording();
            AudioDeviceInfo route = record.getRoutedDevice();
            if (route == null || route.getType() != AudioDeviceInfo.TYPE_REMOTE_SUBMIX) {
                throw new IllegalStateException("Keine Remote-Submix-Route für AudioRecord");
            }
            System.err.println("AudioRecord verwendet Remote Submix");
            byte[] buffer = new byte[1920];
            int remaining = seconds * RATE * 2;
            while (remaining > 0) {
                int n = record.read(buffer, 0, Math.min(remaining, buffer.length),
                                    AudioRecord.READ_BLOCKING);
                if (n <= 0) {
                    throw new IllegalStateException("AudioRecord.read: " + n);
                }
                System.out.write(buffer, 0, n);
                remaining -= n;
            }
            System.out.flush();
            record.stop();
        } finally {
            record.release();
        }
    }
}
