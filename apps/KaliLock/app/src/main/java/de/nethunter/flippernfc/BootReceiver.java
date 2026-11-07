package de.nethunter.flippernfc;
import android.content.*;
/** Restores the user's enabled NFC automation once credential storage is available. */
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c, Intent i) {
  String action=i.getAction();
  if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
  if(!DirectBootUnlock.wanted(c))return;
  try{c.startForegroundService(new Intent(c,NfcWatchService.class));}
  catch(Exception e){NfcWatchService.log(c,"BOOT_SERVICE_FAILED "+e.getClass().getSimpleName());}
 }
}
