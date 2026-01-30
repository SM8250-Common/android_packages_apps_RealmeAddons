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

package org.lineageos.settings.device.display;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class AutoHBMService extends Service implements SensorEventListener {

    private static final String TAG = "AutoHBMService";
    private static final String HBM_MODE_KEY = "hbm_mode";
    private static final String AUTO_HBM_KEY = "auto_hbm";
    private static final String AUTO_HBM_THRESHOLD_KEY = "auto_hbm_threshold";
    private static final int DEFAULT_THRESHOLD = 20000;

    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mSharedPrefs;
    private boolean mAutoHBMEnabled = false;
    private int mThreshold = DEFAULT_THRESHOLD;
    private boolean mHBMCurrentlyActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AutoHBMService created");

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (mLightSensor == null) {
            Log.e(TAG, "Light sensor not found!");
            stopSelf();
            return;
        }

        loadSettings();
        registerLightSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AutoHBMService started");
        loadSettings();
        updateSensorRegistration();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AutoHBMService destroyed");
        unregisterLightSensor();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadSettings() {
        mAutoHBMEnabled = mSharedPrefs.getBoolean(AUTO_HBM_KEY, false);
        mThreshold = mSharedPrefs.getInt(AUTO_HBM_THRESHOLD_KEY, DEFAULT_THRESHOLD);
        Log.d(TAG, "Settings loaded - AutoHBM: " + mAutoHBMEnabled + ", Threshold: " + mThreshold);
    }

    private void updateSensorRegistration() {
        if (mAutoHBMEnabled) {
            registerLightSensor();
        } else {
            unregisterLightSensor();
            if (mHBMCurrentlyActive) {
                HBMController.setHBMEnabled(false);
                mHBMCurrentlyActive = false;
            }
        }
    }

    private void registerLightSensor() {
        if (mLightSensor != null) {
            mSensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Light sensor registered");
        }
    }

    private void unregisterLightSensor() {
        mSensorManager.unregisterListener(this);
        Log.d(TAG, "Light sensor unregistered");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            handleLightSensorUpdate(lux);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void handleLightSensorUpdate(float lux) {
        if (!mAutoHBMEnabled) {
            return;
        }

        boolean manualHBMEnabled = mSharedPrefs.getBoolean(HBM_MODE_KEY, false);
        if (manualHBMEnabled) {
            return;
        }

        boolean shouldEnableHBM = lux >= mThreshold;

        if (shouldEnableHBM && !mHBMCurrentlyActive) {
            Log.d(TAG, "Light level " + lux + " lux exceeds threshold " + mThreshold + " lux, enabling HBM");
            HBMController.setHBMEnabled(true);
            mHBMCurrentlyActive = true;
        } else if (!shouldEnableHBM && mHBMCurrentlyActive) {
            Log.d(TAG, "Light level " + lux + " lux below threshold " + mThreshold + " lux, disabling HBM");
            HBMController.setHBMEnabled(false);
            mHBMCurrentlyActive = false;
        }
    }
}
