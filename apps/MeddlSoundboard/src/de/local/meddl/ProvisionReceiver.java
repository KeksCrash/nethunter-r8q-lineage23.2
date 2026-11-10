package de.local.meddl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ProvisionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String token = intent.getStringExtra("token");
        if (token != null && token.matches("[0-9a-f]{64}")
                && context.getSharedPreferences("bridge", 0).edit().putString("token", token).commit()) {
            setResultCode(1);
        }
    }
}
