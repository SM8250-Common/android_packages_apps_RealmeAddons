/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.battery;

import android.content.Context;

public class BypassChargingUtils {

    private static final int[] VALID_THRESHOLDS = {30, 40, 50, 60};
    private static final int DEFAULT_THRESHOLD = 30;

    /**
     * Snaps a threshold value to the nearest valid option.
     * Valid options are: 30, 40, 50, 60
     */
    public static int snapToValidThreshold(int threshold) {
        int nearest = DEFAULT_THRESHOLD;
        int minDiff = Integer.MAX_VALUE;
        for (int valid : VALID_THRESHOLDS) {
            int diff = Math.abs(threshold - valid);
            if (diff < minDiff) {
                minDiff = diff;
                nearest = valid;
            }
        }
        return nearest;
    }

    /**
     * Checks if a threshold value is one of the valid options.
     */
    public static boolean isValidThreshold(int threshold) {
        for (int valid : VALID_THRESHOLDS) {
            if (valid == threshold) {
                return true;
            }
        }
        return false;
    }

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
        int threshold = BypassChargingController.getInstance(context).getThreshold();
        // Coerce legacy or invalid values to nearest valid option
        if (!isValidThreshold(threshold)) {
            threshold = snapToValidThreshold(threshold);
            setThreshold(context, threshold);
        }
        return threshold;
    }

    public static void setThreshold(Context context, int threshold) {
        // Validate and snap to nearest valid threshold
        int validThreshold = snapToValidThreshold(threshold);
        BypassChargingController.getInstance(context).setThreshold(validThreshold);
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
