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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

public class HBMFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "HBMFragment";
    private static final String KEY_HBM_MODE = "hbm_mode";
    private static final String KEY_AUTO_HBM = "auto_hbm";
    private static final String KEY_AUTO_HBM_THRESHOLD = "auto_hbm_threshold";

    private SwitchPreferenceCompat mHBMPreference;
    private SwitchPreferenceCompat mAutoHBMPreference;
    private SeekBarPreference mAutoHBMThresholdPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.hbm_settings);

        mHBMPreference = findPreference(KEY_HBM_MODE);
        mAutoHBMPreference = findPreference(KEY_AUTO_HBM);
        mAutoHBMThresholdPreference = findPreference(KEY_AUTO_HBM_THRESHOLD);

        if (mHBMPreference != null) {
            mHBMPreference.setOnPreferenceChangeListener(this);
        }

        if (mAutoHBMPreference != null) {
            mAutoHBMPreference.setOnPreferenceChangeListener(this);
        }

        if (mAutoHBMThresholdPreference != null) {
            mAutoHBMThresholdPreference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_HBM_MODE.equals(key)) {
            boolean enabled = (Boolean) newValue;
            HBMController.setHBMEnabled(enabled);
            Log.d(TAG, "HBM manually " + (enabled ? "enabled" : "disabled"));
            return true;
        } else if (KEY_AUTO_HBM.equals(key)) {
            boolean enabled = (Boolean) newValue;
            Intent serviceIntent = new Intent(getContext(), AutoHBMService.class);
            if (enabled) {
                getContext().startService(serviceIntent);
                Log.d(TAG, "AutoHBM service started");
            } else {
                getContext().stopService(serviceIntent);
                Log.d(TAG, "AutoHBM service stopped");
            }
            return true;
        } else if (KEY_AUTO_HBM_THRESHOLD.equals(key)) {
            int threshold = (Integer) newValue;
            Log.d(TAG, "AutoHBM threshold changed to " + threshold);
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            if (prefs.getBoolean(KEY_AUTO_HBM, false)) {
                Intent serviceIntent = new Intent(getContext(), AutoHBMService.class);
                getContext().stopService(serviceIntent);
                getContext().startService(serviceIntent);
            }
            return true;
        }

        return false;
    }
}
