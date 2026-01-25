/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.battery;

import android.content.Context;

public class BypassChargingUtils {

    public static boolean isSupported() {
        return BypassChargingController.isSupported();
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        BypassChargingController controller = BypassChargingController.getInstance(context);
        return enabled ? controller.enableBypassCharging() : controller.disableBypassCharging();
    }

    public static boolean isCurrentlyEnabled(Context context) {
        return BypassChargingController.getInstance(context).isBypassEnabled();
    }

    public static boolean isPowerConnected(Context context) {
        return BypassChargingController.getInstance(context).isPowerConnected();
    }

    public static int getThreshold(Context context) {
        return BypassChargingController.getInstance(context).getThreshold();
    }

    public static void setThreshold(Context context, int threshold) {
        BypassChargingController.getInstance(context).setThreshold(threshold);
    }

    public static boolean isBypassPaused(Context context) {
        return BypassChargingController.getInstance(context).isBypassPaused();
    }

    public static boolean canActivateBypass(Context context) {
        return BypassChargingController.getInstance(context).canActivateBypass();
    }

    public static void restore(Context context) {
        if (!isSupported()) {
            return;
        }
        BypassChargingController.getInstance(context).restore();
    }
}
