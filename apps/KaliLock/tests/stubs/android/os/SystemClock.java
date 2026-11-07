package android.os;
/** Host-test clock only; never included in the APK. */
public final class SystemClock {
 public static long now;
 public static long elapsedRealtime(){return now;}
}
