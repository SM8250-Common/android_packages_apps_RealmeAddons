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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.lineageos.settings.device.R;

import java.io.File;

/**
 * Receives install session results from PackageInstaller Session API.
 * Handles success, user confirmation required, and various failure cases.
 */
public class InstallSessionReceiver extends BroadcastReceiver {

    private static final String TAG = "InstallSessionReceiver";

    // Action broadcast locally when install status changes
    public static final String ACTION_INSTALL_STATUS_CHANGED =
            "org.lineageos.settings.device.camera.INSTALL_STATUS_CHANGED";
    public static final String EXTRA_INSTALL_SUCCESS = "install_success";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!GCamDownloader.INSTALL_ACTION.equals(intent.getAction())) {
            return;
        }

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Install status: " + status + ", message: " + message);

        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                handleInstallSuccess(context);
                break;

            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // User needs to confirm the installation
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;

            case PackageInstaller.STATUS_FAILURE:
                handleInstallFailure(context, R.string.gcam_install_failed);
                break;

            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                handleInstallFailure(context, R.string.gcam_install_blocked);
                break;

            case PackageInstaller.STATUS_FAILURE_ABORTED:
                handleInstallFailure(context, R.string.gcam_install_cancelled);
                break;

            case PackageInstaller.STATUS_FAILURE_INVALID:
                handleInstallFailure(context, R.string.gcam_install_invalid);
                break;

            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                handleInstallFailure(context, R.string.gcam_install_conflict);
                break;

            case PackageInstaller.STATUS_FAILURE_STORAGE:
                handleInstallFailure(context, R.string.gcam_install_storage);
                break;

            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                handleInstallFailure(context, R.string.gcam_install_incompatible);
                break;

            default:
                handleInstallFailure(context, R.string.gcam_install_failed);
                break;
        }
    }

    private void handleInstallSuccess(Context context) {
        Log.d(TAG, "GCam installed successfully via Session API");

        CameraAppController controller = CameraAppController.getInstance(context);
        controller.setGcamDownloadPending(false);

        // Auto-select GCam
        if (controller.setSelectedCamera(CameraAppController.GCAM_PKG)) {
            Toast.makeText(context, R.string.gcam_installed_selected, Toast.LENGTH_LONG).show();
        }

        // Clean up downloaded APK
        cleanupApk();

        // Notify fragment to update UI
        broadcastInstallStatus(context, true);
    }

    private void handleInstallFailure(Context context, int messageResId) {
        Log.e(TAG, "GCam install failed: " + context.getString(messageResId));

        CameraAppController.getInstance(context).setGcamDownloadPending(false);
        Toast.makeText(context, messageResId, Toast.LENGTH_LONG).show();

        // Notify fragment to update UI
        broadcastInstallStatus(context, false);
    }

    private void broadcastInstallStatus(Context context, boolean success) {
        Intent intent = new Intent(ACTION_INSTALL_STATUS_CHANGED);
        intent.putExtra(EXTRA_INSTALL_SUCCESS, success);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void cleanupApk() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File apkFile = new File(downloadDir, CameraAppController.GCAM_APK_NAME);
        if (apkFile.exists()) {
            if (apkFile.delete()) {
                Log.d(TAG, "Cleaned up APK file");
            }
        }
    }
}
