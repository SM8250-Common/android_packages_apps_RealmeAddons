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

public class CameraAppUtils {

    public static boolean setSelectedCamera(Context context, String packageName) {
        return CameraAppController.getInstance(context).setSelectedCamera(packageName);
    }

    public static String getSelectedCamera(Context context) {
        return CameraAppController.getInstance(context).getSelectedCamera();
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        return CameraAppController.getInstance(context).isPackageInstalled(packageName);
    }

    public static boolean isPackageEnabled(Context context, String packageName) {
        return CameraAppController.getInstance(context).isPackageEnabled(packageName);
    }

    public static void restore(Context context) {
        CameraAppController.getInstance(context).restore();
    }

    public static boolean isSupported(Context context) {
        // At least one camera app must be installed
        return isPackageInstalled(context, CameraAppController.OPLUS_CAMERA_PKG)
                || isPackageInstalled(context, CameraAppController.GCAM_PKG);
    }
}
