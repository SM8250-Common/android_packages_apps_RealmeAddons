/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.battery;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.device.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;

public class BypassChargingController {
    private static final String TAG = "BypassChargingController";
    private static final boolean DEBUG = false;

    private static final String BYPASS_CHARGING_NODE = "/sys/devices/virtual/oplus_chg/battery/mmi_charging_enable";
    private static final String BYPASS_CHARGING_KEY = "bypass_charging";
    private static final String MIN_BATTERY_KEY = "bypass_charging_threshold";

    // Threshold bounds
    private static final int MIN_BATTERY_LOWER_BOUND = 30;
    private static final int MIN_BATTERY_UPPER_BOUND = 60;
    private static final int MIN_BATTERY_DEFAULT = 30;

    // Hardware values (inverted: 0 = bypass ON, 1 = bypass OFF)
    private static final String BYPASS_ENABLED = "0";
    private static final String BYPASS_DISABLED = "1";

    private static BypassChargingController sInstance;
    private static final Object sLock = new Object();

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final List<StateChangeListener> mListeners = new ArrayList<>();

    private boolean mIsPowerConnected = false;
    private boolean mBypassEnabled = false;
    private int mMinBatteryForBypass = MIN_BATTERY_DEFAULT;
    private int mCurrentBatteryLevel = -1;
    private boolean mBypassPausedDueToLowBattery = false;

    public interface StateChangeListener {
        void onStateChanged(boolean bypassEnabled, boolean powerConnected);
    }

    private BypassChargingController(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadState();
    }

    public static BypassChargingController getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new BypassChargingController(context);
                }
            }
        }
        return sInstance;
    }

    public static boolean isSupported() {
        return FileUtils.fileExists(BYPASS_CHARGING_NODE);
    }

    public void registerListener(StateChangeListener listener) {
        synchronized (sLock) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void unregisterListener(StateChangeListener listener) {
        synchronized (sLock) {
            mListeners.remove(listener);
        }
    }

    /**
     * Called when charger is connected.
     * Evaluates battery level and enables bypass if conditions are met.
     */
    public void handlePowerConnected() {
        synchronized (sLock) {
            mIsPowerConnected = true;
            updateBatteryLevel();

            if (DEBUG) {
                Log.d(TAG, "Power connected: bypass=" + mBypassEnabled +
                        " battery=" + mCurrentBatteryLevel +
                        " minBattery=" + mMinBatteryForBypass);
            }

            if (mBypassEnabled) {
                evaluateBypassState();
            }
            notifyStateChanged();
        }
    }

    /**
     * Called when charger is disconnected.
     * Immediately disables bypass and resets state.
     */
    public void handlePowerDisconnected() {
        synchronized (sLock) {
            if (DEBUG) {
                Log.d(TAG, "Power disconnected: forcing bypass OFF");
            }

            mIsPowerConnected = false;
            mBypassPausedDueToLowBattery = false;

            // Always ensure hardware bypass is disabled when unplugged
            disableHardwareBypass();
            notifyStateChanged();
        }
    }

    /**
     * Called when battery level changes.
     * Re-evaluates bypass state based on threshold.
     */
    public void handleBatteryLevelChanged(int level) {
        synchronized (sLock) {
            int previousLevel = mCurrentBatteryLevel;
            mCurrentBatteryLevel = level;

            if (!mBypassEnabled || !mIsPowerConnected) {
                return;
            }

            if (DEBUG && previousLevel != level) {
                Log.d(TAG, "Battery changed: " + previousLevel + " -> " + level +
                        " minBattery=" + mMinBatteryForBypass +
                        " paused=" + mBypassPausedDueToLowBattery);
            }

            // Below threshold: pause bypass (resume normal charging)
            if (level < mMinBatteryForBypass && !mBypassPausedDueToLowBattery) {
                if (DEBUG) Log.d(TAG, "Battery below threshold, pausing bypass");
                mBypassPausedDueToLowBattery = true;
                disableHardwareBypass();
                notifyStateChanged();
            }
            // At or above threshold: resume bypass
            else if (level >= mMinBatteryForBypass && mBypassPausedDueToLowBattery) {
                if (DEBUG) Log.d(TAG, "Battery at threshold, resuming bypass");
                mBypassPausedDueToLowBattery = false;
                enableHardwareBypass();
                notifyStateChanged();
            }
        }
    }

    /**
     * Enable bypass charging feature.
     * Actual hardware bypass only activates if power connected and battery >= threshold.
     */
    public boolean enableBypassCharging() {
        synchronized (sLock) {
            if (!isSupported()) {
                Log.w(TAG, "Bypass charging not supported on this device");
                return false;
            }

            mBypassEnabled = true;
            saveState();

            if (DEBUG) {
                Log.d(TAG, "Enabling bypass: power=" + mIsPowerConnected +
                        " battery=" + mCurrentBatteryLevel);
            }

            if (mIsPowerConnected) {
                updateBatteryLevel();
                if (!evaluateBypassState()) {
                    // Hardware enable failed
                    mBypassEnabled = false;
                    saveState();
                    return false;
                }
            }
            notifyStateChanged();
            return true;
        }
    }

    /**
     * Disable bypass charging feature.
     * Immediately disables hardware bypass.
     */
    public boolean disableBypassCharging() {
        synchronized (sLock) {
            if (!isSupported()) {
                return false;
            }

            if (DEBUG) Log.d(TAG, "Disabling bypass charging");

            mBypassEnabled = false;
            mBypassPausedDueToLowBattery = false;
            saveState();
            disableHardwareBypass();
            notifyStateChanged();
            return true;
        }
    }

    /**
     * Evaluate and apply correct bypass state based on battery level.
     * @return true if state was applied successfully
     */
    private boolean evaluateBypassState() {
        if (mCurrentBatteryLevel >= 0 && mCurrentBatteryLevel < mMinBatteryForBypass) {
            mBypassPausedDueToLowBattery = true;
            if (DEBUG) Log.d(TAG, "Bypass paused: battery " + mCurrentBatteryLevel +
                    " < threshold " + mMinBatteryForBypass);
            return true;
        } else {
            mBypassPausedDueToLowBattery = false;
            return enableHardwareBypass();
        }
    }

    public boolean isBypassEnabled() {
        synchronized (sLock) {
            return mBypassEnabled;
        }
    }

    public boolean isPowerConnected() {
        synchronized (sLock) {
            return mIsPowerConnected;
        }
    }

    public boolean isBypassPaused() {
        synchronized (sLock) {
            return mBypassPausedDueToLowBattery;
        }
    }

    /**
     * Check if bypass can actually be activated right now.
     * Returns false if battery is below threshold.
     */
    public boolean canActivateBypass() {
        synchronized (sLock) {
            return mIsPowerConnected &&
                   mCurrentBatteryLevel >= 0 &&
                   mCurrentBatteryLevel >= mMinBatteryForBypass;
        }
    }

    public int getThreshold() {
        synchronized (sLock) {
            return mMinBatteryForBypass;
        }
    }

    public void setThreshold(int threshold) {
        synchronized (sLock) {
            // Defensive clamping
            threshold = clampThreshold(threshold);

            if (DEBUG) Log.d(TAG, "Setting threshold: " + mMinBatteryForBypass + " -> " + threshold);

            mMinBatteryForBypass = threshold;
            mPrefs.edit().putInt(MIN_BATTERY_KEY, threshold).apply();

            // Re-evaluate if bypass is active
            if (mBypassEnabled && mIsPowerConnected && mCurrentBatteryLevel >= 0) {
                handleBatteryLevelChanged(mCurrentBatteryLevel);
            }
        }
    }

    public int getCurrentBatteryLevel() {
        synchronized (sLock) {
            return mCurrentBatteryLevel;
        }
    }

    /**
     * Clamp threshold to valid bounds (30-60).
     */
    private int clampThreshold(int value) {
        return Math.max(MIN_BATTERY_LOWER_BOUND, Math.min(value, MIN_BATTERY_UPPER_BOUND));
    }

    private void updateBatteryLevel() {
        BatteryManager bm = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            mCurrentBatteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (DEBUG) Log.d(TAG, "Updated battery level: " + mCurrentBatteryLevel);
        }
    }

    /**
     * Check current power connection state from system.
     */
    private boolean checkPowerConnected() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, filter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

    private boolean enableHardwareBypass() {
        try {
            FileUtils.writeLine(BYPASS_CHARGING_NODE, BYPASS_ENABLED);
            String verify = FileUtils.readOneLine(BYPASS_CHARGING_NODE);
            boolean success = BYPASS_ENABLED.equals(verify);
            if (DEBUG) Log.d(TAG, "Hardware bypass enable: " + (success ? "OK" : "FAILED"));
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable hardware bypass", e);
            return false;
        }
    }

    private boolean disableHardwareBypass() {
        try {
            FileUtils.writeLine(BYPASS_CHARGING_NODE, BYPASS_DISABLED);
            String verify = FileUtils.readOneLine(BYPASS_CHARGING_NODE);
            boolean success = BYPASS_DISABLED.equals(verify);
            if (DEBUG) Log.d(TAG, "Hardware bypass disable: " + (success ? "OK" : "FAILED"));
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable hardware bypass", e);
            return false;
        }
    }

    private void loadState() {
        synchronized (sLock) {
            mBypassEnabled = mPrefs.getBoolean(BYPASS_CHARGING_KEY, false);

            // Threshold may be stored as String (legacy) or int
            int threshold;
            try {
                threshold = mPrefs.getInt(MIN_BATTERY_KEY, MIN_BATTERY_DEFAULT);
            } catch (ClassCastException e) {
                // Handle legacy String value from ListPreference
                String thresholdStr = mPrefs.getString(MIN_BATTERY_KEY, String.valueOf(MIN_BATTERY_DEFAULT));
                try {
                    threshold = Integer.parseInt(thresholdStr);
                } catch (NumberFormatException nfe) {
                    threshold = MIN_BATTERY_DEFAULT;
                }
                // Migrate to int storage
                mPrefs.edit().remove(MIN_BATTERY_KEY).putInt(MIN_BATTERY_KEY, threshold).apply();
            }

            // Defensive clamping on load
            mMinBatteryForBypass = clampThreshold(threshold);

            if (DEBUG) {
                Log.d(TAG, "Loaded state: bypass=" + mBypassEnabled +
                        " minBattery=" + mMinBatteryForBypass);
            }
        }
    }

    private void saveState() {
        mPrefs.edit().putBoolean(BYPASS_CHARGING_KEY, mBypassEnabled).apply();
    }

    private void notifyStateChanged() {
        List<StateChangeListener> listenersCopy;
        synchronized (sLock) {
            listenersCopy = new ArrayList<>(mListeners);
        }
        for (StateChangeListener listener : listenersCopy) {
            listener.onStateChanged(mBypassEnabled, mIsPowerConnected);
        }
        requestTileUpdate();
    }

    private void requestTileUpdate() {
        try {
            TileService.requestListeningState(mContext,
                    new ComponentName(mContext, BypassChargingTileService.class));
        } catch (Exception e) {
            Log.e(TAG, "Failed to request tile update", e);
        }
    }

    /**
     * Restore bypass state on boot or service restart.
     * Re-evaluates power and battery state to ensure hardware matches settings.
     */
    public void restore() {
        synchronized (sLock) {
            if (!isSupported()) {
                if (DEBUG) Log.d(TAG, "Restore skipped: not supported");
                return;
            }

            loadState();

            // Check actual power state from system
            mIsPowerConnected = checkPowerConnected();
            updateBatteryLevel();

            if (DEBUG) {
                Log.d(TAG, "Restore: bypass=" + mBypassEnabled +
                        " power=" + mIsPowerConnected +
                        " battery=" + mCurrentBatteryLevel +
                        " minBattery=" + mMinBatteryForBypass);
            }

            if (mBypassEnabled && mIsPowerConnected) {
                evaluateBypassState();
            } else {
                // Ensure hardware is in correct state
                disableHardwareBypass();
            }

            notifyStateChanged();
        }
    }
}
