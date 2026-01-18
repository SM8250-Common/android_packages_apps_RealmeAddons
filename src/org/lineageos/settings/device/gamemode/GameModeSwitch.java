package org.lineageos.settings.device.gamemode;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;

import androidx.preference.Preference;

import org.lineageos.settings.device.utils.FileUtils;

public class GameModeSwitch implements Preference.OnPreferenceChangeListener {

    private static final String FILE = "/proc/touchpanel/game_switch_enable";
    private static final String TP_LIMIT_ENABLE = "/proc/touchpanel/oplus_tp_limit_enable";

    private Context mContext;

    public GameModeSwitch(Context context) {
        mContext = context;
    }

    public static String getFile() {
        if (FileUtils.isFileWritable(FILE)) {
            return FILE;
        }
        return null;
    }

    public static boolean isSupported() {
        return FileUtils.isFileWritable(getFile());
    }

    public static boolean isCurrentlyEnabled(Context context) {
        String fileContent = FileUtils.getFileValue(getFile(), "0");
        return fileContent.startsWith("1");
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (boolean) newValue;
        FileUtils.writeLine(getFile(), enabled ? "1" : "0");
        FileUtils.writeLine(TP_LIMIT_ENABLE, enabled ? "0" : "1");
        SystemProperties.set("persist.sys.perf_profile", enabled ? "1" : "0");

        if (enabled) {
            mContext.startService(new Intent(mContext, GameModeRotationService.class));
        } else {
            mContext.stopService(new Intent(mContext, GameModeRotationService.class));
        }

        return true;
    }
}
