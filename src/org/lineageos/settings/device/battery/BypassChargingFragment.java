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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.preference.SegmentedButtonPreference;

public class BypassChargingFragment extends SettingsBasePreferenceFragment
        implements OnPreferenceChangeListener, BypassChargingController.StateChangeListener {

    private static final String KEY_BYPASS_CHARGING = "bypass_charging";
    private static final String KEY_BYPASS_THRESHOLD = "bypass_charging_threshold";

    private SwitchPreferenceCompat mBypassChargingPreference;
    private SegmentedButtonPreference mThresholdPreference;
    private BypassChargingController mController;

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                mController.handlePowerConnected();
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                mController.handlePowerDisconnected();
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.bypass_charging_preferences);

        mController = BypassChargingController.getInstance(getContext());

        mBypassChargingPreference = findPreference(KEY_BYPASS_CHARGING);
        mThresholdPreference = findPreference(KEY_BYPASS_THRESHOLD);

        if (mBypassChargingPreference != null) {
            mBypassChargingPreference.setOnPreferenceChangeListener(this);
        }

        if (mThresholdPreference != null) {
            int currentThreshold = BypassChargingUtils.getThreshold(getContext());
            mThresholdPreference.setValue(currentThreshold);
            updateThresholdSummary(currentThreshold);

            mThresholdPreference.setOnValueChangedListener(newValue -> {
                BypassChargingUtils.setThreshold(getContext(), newValue);
                updateThresholdSummary(newValue);
            });
        }

        updateBypassChargingState();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register power state receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        getContext().registerReceiver(mPowerReceiver, filter);

        // Register controller listener
        mController.registerListener(this);

        updateBypassChargingState();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            getContext().unregisterReceiver(mPowerReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }

        mController.unregisterListener(this);
    }

    @Override
    public void onStateChanged(boolean bypassEnabled, boolean powerConnected) {
        if (mBypassChargingPreference == null || !isAdded() || getActivity() == null) {
            return;
        }

        // Ensure UI updates happen on the main thread
        getActivity().runOnUiThread(() -> {
            if (mBypassChargingPreference == null || !isAdded()) {
                return;
            }
            // Update checked state
            mBypassChargingPreference.setChecked(bypassEnabled);

            // Update enabled state and summary based on power connection
            mBypassChargingPreference.setEnabled(powerConnected);
            if (!powerConnected) {
                mBypassChargingPreference.setSummary(R.string.bypass_charging_unavailable_summary);
            } else {
                mBypassChargingPreference.setSummary(R.string.bypass_charging_summary);
            }
        });
    }

    private void updateBypassChargingState() {
        if (mBypassChargingPreference == null) {
            return;
        }
        boolean isPowerConnected = BypassChargingUtils.isPowerConnected(getContext());
        boolean isBypassEnabled = BypassChargingUtils.isCurrentlyEnabled(getContext());

        mBypassChargingPreference.setChecked(isBypassEnabled);
        mBypassChargingPreference.setEnabled(isPowerConnected);
        if (!isPowerConnected) {
            mBypassChargingPreference.setSummary(R.string.bypass_charging_unavailable_summary);
        } else {
            mBypassChargingPreference.setSummary(R.string.bypass_charging_summary);
        }

        // Update threshold preference state
        if (mThresholdPreference != null) {
            int threshold = BypassChargingUtils.getThreshold(getContext());
            mThresholdPreference.setValue(threshold);
            updateThresholdSummary(threshold);
        }
    }

    private void updateThresholdSummary(int threshold) {
        if (mThresholdPreference != null) {
            mThresholdPreference.setSummary(
                    getString(R.string.bypass_charging_threshold_summary, threshold));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_BYPASS_CHARGING.equals(key)) {
            boolean enabled = (Boolean) newValue;
            return BypassChargingUtils.setEnabled(getContext(), enabled);
        }
        return false;
    }
}
