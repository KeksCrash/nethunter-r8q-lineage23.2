package de.local.karicamera;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class LauncherActivity extends Activity {
    private static final String CAMERA_PACKAGE = "com.droidrooter.virtualcamera";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent camera = getPackageManager().getLaunchIntentForPackage(CAMERA_PACKAGE);
        try {
            if (camera == null) {
                throw new ActivityNotFoundException();
            }
            startActivity(camera);
        } catch (ActivityNotFoundException unavailable) {
            Toast.makeText(this, "DRVCAM ist auf diesem Gerät nicht verfügbar.", Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
