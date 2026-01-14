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

import android.os.Bundle;

import androidx.preference.ListPreference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

public class SmartChargingFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_SMART_CHARGING_ENABLE = "smart_charging_enabled";
    private static final String KEY_CHARGING_SPEED = "charging_speed";

    private MainSwitchPreference mEnableSwitch;
    private ListPreference mChargingSpeedPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.smart_charging_settings);

        mEnableSwitch = findPreference(KEY_SMART_CHARGING_ENABLE);
        mChargingSpeedPref = findPreference(KEY_CHARGING_SPEED);

        if (mEnableSwitch != null) {
            mEnableSwitch.setChecked(SmartChargingUtils.isEnabled(getContext()));
            mEnableSwitch.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = (boolean) newValue;
                boolean success = SmartChargingUtils.setEnabled(getContext(), enabled);
                if (success) {
                    updatePreferenceStates(enabled);
                }
                return success;
            });
        }

        if (mChargingSpeedPref != null) {
            mChargingSpeedPref.setValue(SmartChargingUtils.getChargingSpeed(getContext()));
            mChargingSpeedPref.setOnPreferenceChangeListener((pref, newValue) -> {
                SmartChargingUtils.setChargingSpeed(getContext(), (String) newValue);
                return true;
            });
            if (!SmartChargingUtils.isSpeedControlSupported()) {
                mChargingSpeedPref.setEnabled(false);
                mChargingSpeedPref.setSummary("Not supported on this device");
            }
        }

        // Set initial state
        updatePreferenceStates(SmartChargingUtils.isEnabled(getContext()));
    }

    private void updatePreferenceStates(boolean enabled) {
        if (mChargingSpeedPref != null && SmartChargingUtils.isSpeedControlSupported()) {
            mChargingSpeedPref.setEnabled(enabled);
        }
    }
}
