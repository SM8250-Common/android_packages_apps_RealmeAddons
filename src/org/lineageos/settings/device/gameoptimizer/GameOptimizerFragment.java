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

package org.lineageos.settings.device.gameoptimizer;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.battery.BypassChargingUtils;
import org.lineageos.settings.device.gamemode.GameModeSwitch;

public class GameOptimizerFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_BYPASS_CHARGING = "bypass_charging_settings";
    private static final String KEY_GAME_MODE = "game_mode_enable";
    private static final String KEY_GAME_OPTIMIZER_CATEGORY = "game_optimizer_category";

    private SwitchPreferenceCompat mGameModePreference;
    private Preference mBypassChargingPreference;
    private PreferenceCategory mGameOptimizerCategory;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.game_optimizer_preferences);

        mGameModePreference = findPreference(KEY_GAME_MODE);
        if (mGameModePreference != null) {
            if (GameModeSwitch.isSupported()) {
                mGameModePreference.setOnPreferenceChangeListener(
                    new GameModeSwitch(getContext()));
            } else {
                getPreferenceScreen().removePreference(mGameModePreference);
            }
        }

        // Remove bypass charging preference if not supported
        mGameOptimizerCategory = findPreference(KEY_GAME_OPTIMIZER_CATEGORY);
        mBypassChargingPreference = findPreference(KEY_BYPASS_CHARGING);
        if (mBypassChargingPreference != null && !BypassChargingUtils.isSupported()) {
            mGameOptimizerCategory.removePreference(mBypassChargingPreference);
        }
    }
}
