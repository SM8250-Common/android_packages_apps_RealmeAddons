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

package org.lineageos.settings.device.charginganimation;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ChargingAnimationFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_ENABLED = "charging_animation_enabled";
    private static final String KEY_STYLE = "charging_animation_style";
    private static final String KEY_POSITION = "charging_animation_position";
    private static final String KEY_SIZE = "charging_animation_size";
    private static final String KEY_BACKGROUND = "charging_animation_background";
    private static final String KEY_SHOW_TEXT = "charging_animation_show_text";
    private static final String KEY_SHOW_BOLT = "charging_animation_show_bolt";
    private static final String KEY_IMPORT = "charging_animation_import";

    public static final String CUSTOM_LOTTIE_FILENAME = "custom_charging_animation.json";

    private SwitchPreferenceCompat mEnabledPref;
    private ListPreference mStylePref;
    private ListPreference mPositionPref;
    private ListPreference mSizePref;
    private ListPreference mBackgroundPref;
    private SwitchPreferenceCompat mShowTextPref;
    private SwitchPreferenceCompat mShowBoltPref;
    private Preference mImportPref;

    private ActivityResultLauncher<Intent> mFilePickerLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize file picker launcher
        mFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            importLottieFile(uri);
                        }
                    }
                });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.charging_animation_settings);

        mEnabledPref = findPreference(KEY_ENABLED);
        mStylePref = findPreference(KEY_STYLE);
        mPositionPref = findPreference(KEY_POSITION);
        mSizePref = findPreference(KEY_SIZE);
        mBackgroundPref = findPreference(KEY_BACKGROUND);
        mShowTextPref = findPreference(KEY_SHOW_TEXT);
        mShowBoltPref = findPreference(KEY_SHOW_BOLT);
        mImportPref = findPreference(KEY_IMPORT);

        if (mStylePref != null) {
            mStylePref.setOnPreferenceChangeListener((pref, newValue) -> {
                String style = (String) newValue;
                // Check if custom is selected but no custom animation exists
                if ("custom".equals(style) && !hasCustomAnimation()) {
                    Toast.makeText(getContext(), R.string.charging_animation_no_custom,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                updateStyleSummary(style);
                updateClassicOnlyPreferences(style);
                updateImportVisibility(style);
                return true;
            });
            updateStyleSummary(mStylePref.getValue());
            updateClassicOnlyPreferences(mStylePref.getValue());
            updateImportVisibility(mStylePref.getValue());
        }

        if (mImportPref != null) {
            mImportPref.setOnPreferenceClickListener(pref -> {
                openFilePicker();
                return true;
            });
            updateImportSummary();
        }

        if (mPositionPref != null) {
            mPositionPref.setOnPreferenceChangeListener((pref, newValue) -> {
                updatePositionSummary((String) newValue);
                return true;
            });
            updatePositionSummary(mPositionPref.getValue());
        }

        if (mSizePref != null) {
            mSizePref.setOnPreferenceChangeListener((pref, newValue) -> {
                updateSizeSummary((String) newValue);
                return true;
            });
            updateSizeSummary(mSizePref.getValue());
        }

        if (mBackgroundPref != null) {
            mBackgroundPref.setOnPreferenceChangeListener((pref, newValue) -> {
                updateBackgroundSummary((String) newValue);
                return true;
            });
            updateBackgroundSummary(mBackgroundPref.getValue());
        }
    }

    private void updatePositionSummary(String value) {
        if (mPositionPref != null && value != null) {
            String summary;
            switch (value) {
                case "top":
                    summary = "Top";
                    break;
                case "bottom":
                    summary = "Bottom";
                    break;
                case "center":
                default:
                    summary = "Center";
                    break;
            }
            mPositionPref.setSummary(summary);
        }
    }

    private void updateSizeSummary(String value) {
        if (mSizePref != null && value != null) {
            String summary;
            switch (value) {
                case "small":
                    summary = "Small";
                    break;
                case "large":
                    summary = "Large";
                    break;
                case "xlarge":
                    summary = "Extra Large";
                    break;
                case "medium":
                default:
                    summary = "Medium";
                    break;
            }
            mSizePref.setSummary(summary);
        }
    }

    private void updateBackgroundSummary(String value) {
        if (mBackgroundPref != null && value != null) {
            String summary;
            switch (value) {
                case "blur_light":
                    summary = getString(R.string.charging_animation_bg_blur_light);
                    break;
                case "blur_medium":
                    summary = getString(R.string.charging_animation_bg_blur_medium);
                    break;
                case "blur_heavy":
                    summary = getString(R.string.charging_animation_bg_blur_heavy);
                    break;
                case "dim_light":
                    summary = getString(R.string.charging_animation_bg_dim_light);
                    break;
                case "dim_heavy":
                    summary = getString(R.string.charging_animation_bg_dim_heavy);
                    break;
                case "none":
                    summary = getString(R.string.charging_animation_bg_none);
                    break;
                case "dim_medium":
                default:
                    summary = getString(R.string.charging_animation_bg_dim_medium);
                    break;
            }
            mBackgroundPref.setSummary(summary);
        }
    }

    private void updateStyleSummary(String value) {
        if (mStylePref != null && value != null) {
            String summary;
            switch (value) {
                case "charging_status":
                    summary = getString(R.string.charging_animation_style_charging_status);
                    break;
                case "energy_bolt":
                    summary = getString(R.string.charging_animation_style_energy_bolt);
                    break;
                case "fast_thunder":
                    summary = getString(R.string.charging_animation_style_fast_thunder);
                    break;
                case "loading":
                    summary = getString(R.string.charging_animation_style_loading);
                    break;
                case "renewable_energy":
                    summary = getString(R.string.charging_animation_style_renewable_energy);
                    break;
                case "custom":
                    summary = getString(R.string.charging_animation_style_custom);
                    break;
                case "classic":
                default:
                    summary = getString(R.string.charging_animation_style_classic);
                    break;
            }
            mStylePref.setSummary(summary);
        }
    }

    private void updateClassicOnlyPreferences(String style) {
        // Show text and bolt options now apply to all animation styles
        // Keep them always visible
        if (mShowTextPref != null) {
            mShowTextPref.setVisible(true);
        }
        if (mShowBoltPref != null) {
            mShowBoltPref.setVisible(true);
        }
    }

    private void updateImportVisibility(String style) {
        // Show import option only when custom style is selected or always show it
        if (mImportPref != null) {
            mImportPref.setVisible(true);
        }
    }

    private void updateImportSummary() {
        if (mImportPref != null) {
            if (hasCustomAnimation()) {
                mImportPref.setSummary(R.string.charging_animation_import_summary_set);
            } else {
                mImportPref.setSummary(R.string.charging_animation_import_summary);
            }
        }
    }

    private boolean hasCustomAnimation() {
        if (getContext() == null) return false;
        File customFile = new File(getContext().getFilesDir(), CUSTOM_LOTTIE_FILENAME);
        return customFile.exists();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        mFilePickerLauncher.launch(intent);
    }

    private void importLottieFile(Uri uri) {
        if (getContext() == null) return;

        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(getContext(), R.string.charging_animation_import_error,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            File outputFile = new File(getContext().getFilesDir(), CUSTOM_LOTTIE_FILENAME);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            Toast.makeText(getContext(), R.string.charging_animation_import_success,
                    Toast.LENGTH_SHORT).show();
            updateImportSummary();

            // Auto-select custom style after import
            if (mStylePref != null) {
                mStylePref.setValue("custom");
                updateStyleSummary("custom");
            }

        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.charging_animation_import_error,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
