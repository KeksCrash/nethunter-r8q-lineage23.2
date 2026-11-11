package de.nethunter.smartunlock;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){String a=i.getAction();if(Intent.ACTION_BOOT_COMPLETED.equals(a)){TrustMonitor.setUnlocked(c,false);Widget.refresh(c);}else if(Intent.ACTION_USER_PRESENT.equals(a)){TrustMonitor.setUnlocked(c,true);Widget.refresh(c);}}
}
