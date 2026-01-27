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
import android.util.Log;
import android.widget.Toast;

import org.lineageos.settings.device.R;

public class PackageInstallReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageInstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            return;
        }

        String packageName = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
        if (packageName == null) {
            return;
        }

        Log.d(TAG, "Package installed: " + packageName);

        // Check if GCam was installed
        if (CameraAppController.GCAM_PKG.equals(packageName)) {
            CameraAppController controller = CameraAppController.getInstance(context);

            // Check if we were waiting for this installation
            if (controller.isGcamDownloadPending()) {
                Log.d(TAG, "GCam installed after download, auto-selecting");
                controller.setGcamDownloadPending(false);

                // Auto-select GCam
                if (controller.setSelectedCamera(CameraAppController.GCAM_PKG)) {
                    Toast.makeText(context, R.string.gcam_installed_selected, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
