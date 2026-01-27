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

package org.lineageos.settings.device.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class CameraAppController {

    private static final String TAG = "CameraAppController";
    private static final boolean DEBUG = true;

    public static final String OPLUS_CAMERA_PKG = "com.oplus.camera";
    public static final String GCAM_PKG = "com.android.MGC_8_9_097";

    public static final String GCAM_DOWNLOAD_URL =
            "https://github.com/SM8250-Common/GCamRelease/releases/download/v1.0/GCam.apk";
    public static final String GCAM_APK_NAME = "GCam.apk";

    // SHA256 hash of the GCam APK for integrity verification
    // Set to null to skip verification, or compute hash using:
    // sha256sum GCam.apk | awk '{print toupper($1)}'
    public static final String GCAM_APK_SHA256 =
            "B5D196A4201398DAFC5A1B01B233EC7CBDE9D32C51E8B7848A9535AD4DC163E8";

    private static final String PREF_SELECTED_CAMERA = "selected_camera_app";
    private static final String PREF_GCAM_DOWNLOAD_PENDING = "gcam_download_pending";

    private static CameraAppController sInstance;
    private static final Object sLock = new Object();

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final SharedPreferences mPrefs;

    private CameraAppController(Context context) {
        mContext = context.getApplicationContext();
        mPackageManager = mContext.getPackageManager();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static CameraAppController getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new CameraAppController(context);
                }
            }
        }
        return sInstance;
    }

    public boolean setSelectedCamera(String packageName) {
        if (DEBUG) Log.d(TAG, "setSelectedCamera: " + packageName);

        // Validate package name
        if (!OPLUS_CAMERA_PKG.equals(packageName) && !GCAM_PKG.equals(packageName)) {
            Log.e(TAG, "Invalid camera package: " + packageName);
            return false;
        }

        // Check if selected package is installed
        if (!isPackageInstalled(packageName)) {
            Log.e(TAG, "Selected camera package not installed: " + packageName);
            return false;
        }

        // Save preference
        mPrefs.edit().putString(PREF_SELECTED_CAMERA, packageName).apply();

        // Enable selected, disable other
        boolean success;
        if (OPLUS_CAMERA_PKG.equals(packageName)) {
            success = setPackageEnabled(OPLUS_CAMERA_PKG, true);
            if (isPackageInstalled(GCAM_PKG)) {
                success &= setPackageEnabled(GCAM_PKG, false);
            }
        } else {
            success = setPackageEnabled(GCAM_PKG, true);
            if (isPackageInstalled(OPLUS_CAMERA_PKG)) {
                success &= setPackageEnabled(OPLUS_CAMERA_PKG, false);
            }
        }

        return success;
    }

    private boolean setPackageEnabled(String pkg, boolean enabled) {
        if (DEBUG) Log.d(TAG, "setPackageEnabled: " + pkg + " -> " + enabled);

        try {
            int state = enabled
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

            mPackageManager.setApplicationEnabledSetting(pkg, state, 0);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set package enabled state: " + pkg, e);
            return false;
        }
    }

    public String getSelectedCamera() {
        return mPrefs.getString(PREF_SELECTED_CAMERA, OPLUS_CAMERA_PKG);
    }

    public boolean isPackageInstalled(String pkg) {
        try {
            mPackageManager.getPackageInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean isPackageEnabled(String pkg) {
        try {
            int state = mPackageManager.getApplicationEnabledSetting(pkg);
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        } catch (Exception e) {
            return false;
        }
    }

    public void restore() {
        if (DEBUG) Log.d(TAG, "restore: Restoring camera app selection");

        // Check if this is first boot (no preference set yet)
        boolean isFirstBoot = !mPrefs.contains(PREF_SELECTED_CAMERA);
        if (isFirstBoot) {
            if (DEBUG) Log.d(TAG, "First boot detected, applying default camera: " + OPLUS_CAMERA_PKG);
        }

        String selected = getSelectedCamera();

        // If selected camera is not installed, fall back to the other
        if (!isPackageInstalled(selected)) {
            if (DEBUG) Log.d(TAG, "Selected camera not installed, falling back");
            if (OPLUS_CAMERA_PKG.equals(selected) && isPackageInstalled(GCAM_PKG)) {
                selected = GCAM_PKG;
            } else if (GCAM_PKG.equals(selected) && isPackageInstalled(OPLUS_CAMERA_PKG)) {
                selected = OPLUS_CAMERA_PKG;
            } else {
                Log.w(TAG, "No camera apps found");
                return;
            }
        }

        // Always apply selection on first boot or if camera states are inconsistent
        if (isFirstBoot || !isCorrectCameraState(selected)) {
            setSelectedCamera(selected);
        }
    }

    private boolean isCorrectCameraState(String selected) {
        // Check if the current enabled/disabled state matches the selection
        if (OPLUS_CAMERA_PKG.equals(selected)) {
            return isPackageEnabled(OPLUS_CAMERA_PKG) &&
                    (!isPackageInstalled(GCAM_PKG) || !isPackageEnabled(GCAM_PKG));
        } else {
            return isPackageEnabled(GCAM_PKG) &&
                    (!isPackageInstalled(OPLUS_CAMERA_PKG) || !isPackageEnabled(OPLUS_CAMERA_PKG));
        }
    }

    public void setGcamDownloadPending(boolean pending) {
        mPrefs.edit().putBoolean(PREF_GCAM_DOWNLOAD_PENDING, pending).apply();
    }

    public boolean isGcamDownloadPending() {
        return mPrefs.getBoolean(PREF_GCAM_DOWNLOAD_PENDING, false);
    }
}
