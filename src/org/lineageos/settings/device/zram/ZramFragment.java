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

package org.lineageos.settings.device.zram;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

public class ZramFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ZramFragment";
    private static final String KEY_ZRAM_ENABLED = "zram_enabled";
    private static final String KEY_ZRAM_SIZE = "zram_size";

    private SwitchPreferenceCompat mZramEnabledPreference;
    private SeekBarPreference mZramSizePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.zram_settings);

        mZramEnabledPreference = findPreference(KEY_ZRAM_ENABLED);
        mZramSizePreference = findPreference(KEY_ZRAM_SIZE);

        if (mZramEnabledPreference != null) {
            mZramEnabledPreference.setOnPreferenceChangeListener(this);
        }

        if (mZramSizePreference != null) {
            mZramSizePreference.setOnPreferenceChangeListener(this);
        }

        syncPreferenceState();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncPreferenceState();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_ZRAM_ENABLED.equals(key)) {
            boolean enabled = (Boolean) newValue;
            boolean success = ZramController.setZramEnabled(enabled);
            if (success) {
                Log.d(TAG, "Zram " + (enabled ? "enabled" : "disabled"));
                if (mZramSizePreference != null) {
                    mZramSizePreference.setEnabled(enabled);
                }
            } else {
                Log.e(TAG, "Failed to " + (enabled ? "enable" : "disable") + " zram");
            }
            return success;
        } else if (KEY_ZRAM_SIZE.equals(key)) {
            int size = (Integer) newValue;
            boolean success = ZramController.setZramSize(size);
            if (success) {
                Log.d(TAG, "Zram size set to " + size + " MB");
            } else {
                Log.e(TAG, "Failed to set zram size to " + size + " MB");
            }
            return success;
        }

        return false;
    }

    private void syncPreferenceState() {
        boolean enabled = ZramController.isZramEnabled();
        int sizeMb = (int) ZramController.getCurrentZramSizeMB();

        if (mZramEnabledPreference != null) {
            mZramEnabledPreference.setChecked(enabled);
        }

        if (mZramSizePreference != null) {
            if (sizeMb > 0) {
                mZramSizePreference.setValue(sizeMb);
            }
            mZramSizePreference.setEnabled(enabled);
        }
    }
}
