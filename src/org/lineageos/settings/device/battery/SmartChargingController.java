/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device.battery;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.device.utils.FileUtils;

public class SmartChargingController {
    private static final String TAG = "SmartChargingController";
    private static final boolean DEBUG = true;

    // Cool down nodes (for charging speed control)
    private static final String COOL_DOWN_NODE = "/sys/class/power_supply/battery/cool_down";
    private static final String COOL_DOWN_NODE_NEW = "/sys/devices/virtual/oplus_chg/battery/cool_down";

    public static final String KEY_SMART_CHARGING_ENABLED = "smart_charging_enabled";
    public static final String KEY_CHARGING_SPEED = "charging_speed";
    public static final String KEY_CHARGING_LIMIT = "charging_limit";

    public static final int DEFAULT_CHARGING_LIMIT = 100;

    private static final int MAX_WRITE_RETRIES = 3;

    private static SmartChargingController sInstance;
    private static final Object sLock = new Object();
    private static Boolean sSupportedCached = null;
    private static String sCoolDownNode = null;

    private final Context mContext;
    private final SharedPreferences mPrefs;

    private boolean mSmartChargingEnabled = false;
    private String mChargingSpeed = "0";
    private int mChargingLimit = DEFAULT_CHARGING_LIMIT;

    private SmartChargingController(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadState();
    }

    public static SmartChargingController getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new SmartChargingController(context);
                }
            }
        }
        return sInstance;
    }

    private static void detectNodes() {
        synchronized (sLock) {
            if (sCoolDownNode == null) {
                // Try new node path first, then legacy
                if (FileUtils.fileExists(COOL_DOWN_NODE_NEW)) {
                    sCoolDownNode = COOL_DOWN_NODE_NEW;
                    if (DEBUG) Log.d(TAG, "Detected cool_down node (new): " + sCoolDownNode);
                } else if (FileUtils.fileExists(COOL_DOWN_NODE)) {
                    sCoolDownNode = COOL_DOWN_NODE;
                    if (DEBUG) Log.d(TAG, "Detected cool_down node (legacy): " + sCoolDownNode);
                } else {
                    // Debug: log why detection failed
                    Log.w(TAG, "Cool down node detection failed:");
                    Log.w(TAG, "  " + COOL_DOWN_NODE_NEW + " exists=" + FileUtils.fileExists(COOL_DOWN_NODE_NEW));
                    Log.w(TAG, "  " + COOL_DOWN_NODE + " exists=" + FileUtils.fileExists(COOL_DOWN_NODE));
                }
            }
        }
    }

    public static String getCoolDownNode() {
        synchronized (sLock) {
            detectNodes();
            return sCoolDownNode;
        }
    }

    public static boolean isSupported() {
        synchronized (sLock) {
            if (sSupportedCached != null) {
                return sSupportedCached;
            }
            detectNodes();
            if (sCoolDownNode == null) {
                Log.e(TAG, "Smart charging not supported - no cool_down node");
                sSupportedCached = false;
                return false;
            }
            if (DEBUG) Log.d(TAG, "Smart charging supported");
            sSupportedCached = true;
            return true;
        }
    }

    public static boolean isSpeedControlSupported() {
        synchronized (sLock) {
            detectNodes();
            return sCoolDownNode != null;
        }
    }

    public boolean enableSmartCharging() {
        synchronized (sLock) {
            if (!isSupported()) {
                Log.e(TAG, "Smart charging not supported");
                return false;
            }

            mSmartChargingEnabled = true;
            saveState();

            // Apply speed limit if supported
            if (isSpeedControlSupported()) {
                applyChargingSpeed(mChargingSpeed);
            }

            // Start the service for battery monitoring
            SmartChargingService.start(mContext);

            if (DEBUG) Log.d(TAG, "Smart charging enabled");
            return true;
        }
    }

    public boolean disableSmartCharging() {
        synchronized (sLock) {
            mSmartChargingEnabled = false;
            saveState();

            // Reset speed to default
            if (isSpeedControlSupported()) {
                applyChargingSpeed("0");
            }

            // Stop the service
            SmartChargingService.stop(mContext);

            if (DEBUG) Log.d(TAG, "Smart charging disabled");
            return true;
        }
    }

    public boolean setChargingSpeed(String speed) {
        synchronized (sLock) {
            if (speed == null || speed.isEmpty()) {
                speed = "0";
            }

            mChargingSpeed = speed;
            saveState();

            if (mSmartChargingEnabled && isSpeedControlSupported()) {
                return applyChargingSpeed(speed);
            }

            return true;
        }
    }

    public boolean setChargingLimit(int limit) {
        synchronized (sLock) {
            if (limit < 70) limit = 70;
            if (limit > 100) limit = 100;

            mChargingLimit = limit;
            saveState();

            if (DEBUG) Log.d(TAG, "Charging limit set to: " + limit + "%");
            return true;
        }
    }

    public boolean isSmartChargingEnabled() {
        synchronized (sLock) {
            return mSmartChargingEnabled;
        }
    }

    public String getChargingSpeed() {
        synchronized (sLock) {
            return mChargingSpeed;
        }
    }

    public int getChargingLimit() {
        synchronized (sLock) {
            return mChargingLimit;
        }
    }

    private boolean applyChargingSpeed(String speed) {
        String node = getCoolDownNode();
        if (node == null) {
            Log.e(TAG, "No cool_down node available");
            return false;
        }

        for (int retry = 0; retry < MAX_WRITE_RETRIES; retry++) {
            try {
                if (FileUtils.writeLine(node, speed)) {
                    if (DEBUG) Log.d(TAG, "Applied charging speed: " + speed);
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply charging speed (attempt " + (retry + 1) + ")", e);
            }

            if (retry < MAX_WRITE_RETRIES - 1) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        Log.e(TAG, "Failed to apply charging speed after " + MAX_WRITE_RETRIES + " retries");
        return false;
    }

    private void loadState() {
        synchronized (sLock) {
            mSmartChargingEnabled = mPrefs.getBoolean(KEY_SMART_CHARGING_ENABLED, false);
            mChargingSpeed = mPrefs.getString(KEY_CHARGING_SPEED, "0");

            Object limitValue = mPrefs.getAll().get(KEY_CHARGING_LIMIT);
            try {
                if (limitValue instanceof String) {
                    mChargingLimit = Integer.parseInt((String) limitValue);
                    mPrefs.edit()
                            .putInt(KEY_CHARGING_LIMIT, mChargingLimit)
                            .apply();
                } else if (limitValue instanceof Number) {
                    mChargingLimit = ((Number) limitValue).intValue();
                } else {
                    mChargingLimit = DEFAULT_CHARGING_LIMIT;
                }
            } catch (NumberFormatException e) {
                mChargingLimit = DEFAULT_CHARGING_LIMIT;
                if (DEBUG) Log.w(TAG, "Invalid charging limit format", e);
            }

            if (DEBUG) {
                Log.d(TAG, "Loaded state: enabled=" + mSmartChargingEnabled +
                        " speed=" + mChargingSpeed +
                        " limit=" + mChargingLimit + "%");
            }
        }
    }

    private void saveState() {
        synchronized (sLock) {
            mPrefs.edit()
                    .putBoolean(KEY_SMART_CHARGING_ENABLED, mSmartChargingEnabled)
                    .putString(KEY_CHARGING_SPEED, mChargingSpeed)
                    .putInt(KEY_CHARGING_LIMIT, mChargingLimit)
                    .apply();
            if (DEBUG) Log.d(TAG, "Saved state: enabled=" + mSmartChargingEnabled +
                    " speed=" + mChargingSpeed + " limit=" + mChargingLimit + "%");
        }
    }

    public void restore() {
        synchronized (sLock) {
            if (!isSupported()) {
                Log.w(TAG, "Smart charging not supported on this device");
                return;
            }

            loadState();

            if (mSmartChargingEnabled) {
                if (isSpeedControlSupported()) {
                    applyChargingSpeed(mChargingSpeed);
                }
                SmartChargingService.start(mContext);
            } else {
                if (isSpeedControlSupported()) {
                    applyChargingSpeed("0");
                }
            }

            if (DEBUG) {
                Log.d(TAG, "Restored state: enabled=" + mSmartChargingEnabled +
                        " speed=" + mChargingSpeed + " limit=" + mChargingLimit + "%");
            }
        }
    }
}
