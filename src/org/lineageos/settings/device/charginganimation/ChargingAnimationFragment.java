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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ChargingAnimationFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_ENABLED = "charging_animation_enabled";
    private static final String KEY_STYLE = "charging_animation_style";
    private static final String KEY_PILL_COLOR = "charging_animation_pill_color";
    private static final String KEY_POSITION = "charging_animation_position";
    private static final String KEY_SIZE = "charging_animation_size";
    private static final String KEY_BACKGROUND = "charging_animation_background";
    private static final String KEY_SHOW_TEXT = "charging_animation_show_text";
    private static final String KEY_SHOW_BOLT = "charging_animation_show_bolt";
    private static final String KEY_IMPORT = "charging_animation_import";

    public static final String CUSTOM_LOTTIE_FILENAME = "custom_charging_animation.json";

    private static final long PREVIEW_HIDE_DELAY_MS = 2000;

    private SwitchPreferenceCompat mEnabledPref;
    private ListPreference mStylePref;
    private ListPreference mPillColorPref;
    private SeekBarPreference mPositionPref;
    private ListPreference mSizePref;
    private ListPreference mBackgroundPref;
    private SwitchPreferenceCompat mShowTextPref;
    private SwitchPreferenceCompat mShowBoltPref;
    private Preference mImportPref;

    private ActivityResultLauncher<Intent> mFilePickerLauncher;
    private final Handler mPreviewHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHidePreviewRunnable = this::hidePositionPreview;

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
        // Migrate old String position preference to int for SeekBarPreference
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            try {
                prefs.getInt(KEY_POSITION, 50);
            } catch (ClassCastException e) {
                String old = prefs.getString(KEY_POSITION, "50");
                int value = 50;
                try {
                    value = Integer.parseInt(old);
                } catch (NumberFormatException ignored) {
                }
                prefs.edit().remove(KEY_POSITION).putInt(KEY_POSITION, value).apply();
            }
        }
        addPreferencesFromResource(R.xml.charging_animation_settings);

        mEnabledPref = findPreference(KEY_ENABLED);
        mStylePref = findPreference(KEY_STYLE);
        mPillColorPref = findPreference(KEY_PILL_COLOR);
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
                updatePillColorVisibility(style);
                return true;
            });
            updateStyleSummary(mStylePref.getValue());
            updateClassicOnlyPreferences(mStylePref.getValue());
            updateImportVisibility(mStylePref.getValue());
            updatePillColorVisibility(mStylePref.getValue());
        }

        if (mPillColorPref != null) {
            mPillColorPref.setOnPreferenceChangeListener((pref, newValue) -> {
                updatePillColorSummary((String) newValue);
                return true;
            });
            updatePillColorSummary(mPillColorPref.getValue());
        }

        if (mImportPref != null) {
            mImportPref.setOnPreferenceClickListener(pref -> {
                openFilePicker();
                return true;
            });
            updateImportSummary();
        }

        if (mPositionPref != null) {
            mPositionPref.setMin(0);
            mPositionPref.setUpdatesContinuously(true);
            mPositionPref.setOnPreferenceChangeListener((pref, newValue) -> {
                int percent = (int) newValue;
                updatePositionSummary(percent);
                showPositionPreview(percent);
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

    private void updatePositionSummary(int percent) {
        if (mPositionPref != null) {
            mPositionPref.setSummary(getString(R.string.charging_animation_position_format, percent));
        }
    }

    private void showPositionPreview(int percent) {
        if (getContext() == null) return;
        mPreviewHandler.removeCallbacks(mHidePreviewRunnable);
        ChargingAnimationOverlay.getInstance(getContext()).showPreview(percent);
        mPreviewHandler.postDelayed(mHidePreviewRunnable, PREVIEW_HIDE_DELAY_MS);
    }

    private void hidePositionPreview() {
        if (getContext() == null) return;
        ChargingAnimationOverlay.getInstance(getContext()).hidePreview();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPreviewHandler.removeCallbacks(mHidePreviewRunnable);
        hidePositionPreview();
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
                case "pill":
                    summary = getString(R.string.charging_animation_style_pill);
                    break;
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

    private void updatePillColorVisibility(String style) {
        // Show pill color option only when pill style is selected
        if (mPillColorPref != null) {
            mPillColorPref.setVisible("pill".equals(style));
        }
    }

    private void updatePillColorSummary(String colorHex) {
        if (mPillColorPref != null && colorHex != null) {
            String summary;
            switch (colorHex) {
                case "#2196F3":
                    summary = getString(R.string.pill_color_blue);
                    break;
                case "#9C27B0":
                    summary = getString(R.string.pill_color_purple);
                    break;
                case "#E91E63":
                    summary = getString(R.string.pill_color_pink);
                    break;
                case "#F44336":
                    summary = getString(R.string.pill_color_red);
                    break;
                case "#FF9800":
                    summary = getString(R.string.pill_color_orange);
                    break;
                case "#FFEB3B":
                    summary = getString(R.string.pill_color_yellow);
                    break;
                case "#4CAF50":
                    summary = getString(R.string.pill_color_green);
                    break;
                case "#00BFA5":
                default:
                    summary = getString(R.string.pill_color_teal);
                    break;
            }
            mPillColorPref.setSummary(summary);
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
