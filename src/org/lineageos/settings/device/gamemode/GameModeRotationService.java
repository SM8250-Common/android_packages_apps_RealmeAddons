package org.lineageos.settings.device.gamemode;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
import android.view.Surface;
import android.view.WindowManager;

import org.lineageos.settings.device.utils.FileUtils;

public class GameModeRotationService extends Service {

    private static final String TP_DIRECTION = "/proc/touchpanel/oplus_tp_direction";

    private void updateTpDirection() {
        WindowManager wm = (WindowManager) getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        int tpDirection = 0;

        if (rotation == Surface.ROTATION_90) {
            tpDirection = 1;
        } else if (rotation == Surface.ROTATION_270) {
            tpDirection = 2;
        }

        FileUtils.writeLine(TP_DIRECTION, String.valueOf(tpDirection));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        updateTpDirection();
    }

    @Override
    public void onDestroy() {
        FileUtils.writeLine(TP_DIRECTION, "0");
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTpDirection();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
