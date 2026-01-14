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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;

public class SmartChargingService extends Service {
    private static final String TAG = "SmartChargingService";
    private static final boolean DEBUG = true;

    private static final float THERMAL_THRESHOLD_CELSIUS = 35.0f;
    private static final float CRITICAL_TEMP_CELSIUS = 42.0f;

    private SmartChargingController mController;
    private PowerManager.WakeLock mWakeLock;
    private boolean mBatteryReceiverRegistered = false;
    private boolean mConnectionReceiverRegistered = false;
    private boolean mIsChargingSpeedApplied = false;

    // Receiver for power connect/disconnect events
    private final BroadcastReceiver mConnectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                if (DEBUG) Log.d(TAG, "Power connected");
                mIsChargingSpeedApplied = false;
                registerBatteryReceiver();
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                if (DEBUG) Log.d(TAG, "Power disconnected");
                unregisterBatteryReceiver();
                if (mIsChargingSpeedApplied) {
                    removeChargingSpeedLimit();
                    mIsChargingSpeedApplied = false;
                }
            }
        }
    };

    // Receiver for battery change events (only active when power connected)
    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                handleBatteryChanged(intent);
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Service created");

        mController = SmartChargingController.getInstance(this);

        // Acquire WakeLock to keep service running
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartCharging::WakeLock");
        mWakeLock.acquire();

        // Register for power connect/disconnect
        IntentFilter connectionFilter = new IntentFilter();
        connectionFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        connectionFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mConnectionReceiver, connectionFilter);
        mConnectionReceiverRegistered = true;

        // If already connected to power, register battery receiver
        if (isPowerConnected()) {
            registerBatteryReceiver();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Service started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "Service destroyed");

        // Unregister receivers
        unregisterBatteryReceiver();
        if (mConnectionReceiverRegistered) {
            try {
                unregisterReceiver(mConnectionReceiver);
            } catch (IllegalArgumentException ignored) {}
            mConnectionReceiverRegistered = false;
        }

        // Release WakeLock
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerBatteryReceiver() {
        if (!mBatteryReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(mBatteryReceiver, filter);
            mBatteryReceiverRegistered = true;
            if (DEBUG) Log.d(TAG, "Battery receiver registered");
        }
    }

    private void unregisterBatteryReceiver() {
        if (mBatteryReceiverRegistered) {
            try {
                unregisterReceiver(mBatteryReceiver);
            } catch (IllegalArgumentException ignored) {}
            mBatteryReceiverRegistered = false;
            if (DEBUG) Log.d(TAG, "Battery receiver unregistered");
        }
    }

    private boolean isPowerConnected() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus != null) {
            int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            return plugged != 0;
        }
        return false;
    }

    private void handleBatteryChanged(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int batteryPercent = (level * 100) / scale;

        int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        float tempCelsius = tempRaw / 10.0f;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING);

        if (DEBUG) {
            Log.d(TAG, "Battery: " + batteryPercent + "%, Temp: " + tempCelsius + "°C, Charging: " + isCharging);
        }

        if (!isCharging) {
            return;
        }

        boolean smartChargingEnabled = mController.isSmartChargingEnabled();
        boolean shouldApplyLimit = false;

        if (tempCelsius >= CRITICAL_TEMP_CELSIUS) {
            shouldApplyLimit = true;
            if (DEBUG) Log.d(TAG, "Critical temperature detected, forcing thermal limit");
        } else if (smartChargingEnabled && tempCelsius >= THERMAL_THRESHOLD_CELSIUS) {
            shouldApplyLimit = true;
            if (DEBUG) Log.d(TAG, "Temperature above threshold, applying smart charging");
        } else if (smartChargingEnabled && tempCelsius < THERMAL_THRESHOLD_CELSIUS) {
            shouldApplyLimit = false;
            if (DEBUG) Log.d(TAG, "Temperature below threshold, not applying limit");
        }

        if (shouldApplyLimit && !mIsChargingSpeedApplied) {
            applyChargingSpeedLimit();
            mIsChargingSpeedApplied = true;
        } else if (!shouldApplyLimit && mIsChargingSpeedApplied) {
            removeChargingSpeedLimit();
            mIsChargingSpeedApplied = false;
        }

        int chargingLimit = mController.getChargingLimit();
        if (smartChargingEnabled && batteryPercent >= chargingLimit) {
            if (DEBUG) Log.d(TAG, "Battery reached limit: " + chargingLimit + "%");
        }
    }

    private void applyChargingSpeedLimit() {
        String speed = mController.getChargingSpeed();
        if (SmartChargingController.isSpeedControlSupported()) {
            if (DEBUG) Log.d(TAG, "Applying charging speed limit: " + speed);
            mController.setChargingSpeed(speed);
        }
    }

    private void removeChargingSpeedLimit() {
        if (SmartChargingController.isSpeedControlSupported()) {
            if (DEBUG) Log.d(TAG, "Removing charging speed limit");
            String node = SmartChargingController.getCoolDownNode();
            if (node != null) {
                org.lineageos.settings.device.utils.FileUtils.writeLine(node, "0");
            }
        }
    }

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, SmartChargingService.class);
            context.startServiceAsUser(intent, UserHandle.CURRENT);
            if (DEBUG) Log.d(TAG, "Starting service");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service", e);
        }
    }

    public static void stop(Context context) {
        try {
            Intent intent = new Intent(context, SmartChargingService.class);
            context.stopServiceAsUser(intent, UserHandle.CURRENT);
            if (DEBUG) Log.d(TAG, "Stopping service");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop service", e);
        }
    }
}
