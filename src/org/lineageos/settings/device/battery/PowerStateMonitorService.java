/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.battery;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

public class PowerStateMonitorService extends Service {

    private static final String TAG = "PowerStateMonitorService";
    private static final boolean DEBUG = false;

    private BypassChargingController mController;

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                if (DEBUG) Log.d(TAG, "Power connected");
                mController.handlePowerConnected();
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                if (DEBUG) Log.d(TAG, "Power disconnected");
                mController.handlePowerDisconnected();
            }
        }
    };

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int batteryPct = (level * 100) / scale;
                mController.handleBatteryLevelChanged(batteryPct);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) Log.d(TAG, "Service starting");

        mController = BypassChargingController.getInstance(this);

        // Register receivers
        IntentFilter powerFilter = new IntentFilter();
        powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        powerFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mPowerReceiver, powerFilter);

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(mBatteryReceiver, batteryFilter);

        // Initial state evaluation on service start
        evaluateInitialState(batteryStatus);
    }

    /**
     * Evaluate initial power and battery state on service start.
     * This handles boot scenarios and service restarts.
     */
    private void evaluateInitialState(Intent batteryStatus) {
        if (batteryStatus == null) {
            if (DEBUG) Log.d(TAG, "No battery status available");
            return;
        }

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int batteryPct = (level * 100) / scale;

        if (DEBUG) {
            Log.d(TAG, "Initial state: charging=" + isCharging + " battery=" + batteryPct);
        }

        // Sync controller state with actual hardware state
        if (isCharging) {
            mController.handlePowerConnected();
        } else {
            mController.handlePowerDisconnected();
        }
        mController.handleBatteryLevelChanged(batteryPct);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "Service stopping");

        try {
            unregisterReceiver(mPowerReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(mBatteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
