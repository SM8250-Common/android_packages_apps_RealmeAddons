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

import android.content.Context;

/**
 * Helper class for smart charging
 * Delegates to SmartChargingController
 */
public class SmartChargingUtils {

    /**
     * Check if smart charging is supported on this device
     */
    public static boolean isSupported() {
        return SmartChargingController.isSupported();
    }

    /**
     * Check if charging speed control is supported
     */
    public static boolean isSpeedControlSupported() {
        return SmartChargingController.isSpeedControlSupported();
    }

    /**
     * Enable or disable smart charging
     */
    public static boolean setEnabled(Context context, boolean enabled) {
        SmartChargingController controller = SmartChargingController.getInstance(context);
        return enabled ? controller.enableSmartCharging() : controller.disableSmartCharging();
    }

    /**
     * Get current smart charging state
     */
    public static boolean isEnabled(Context context) {
        return SmartChargingController.getInstance(context).isSmartChargingEnabled();
    }

    /**
     * Set charging speed
     */
    public static boolean setChargingSpeed(Context context, String speed) {
        SmartChargingController controller = SmartChargingController.getInstance(context);
        return controller.setChargingSpeed(speed);
    }

    /**
     * Get current charging speed
     */
    public static String getChargingSpeed(Context context) {
        return SmartChargingController.getInstance(context).getChargingSpeed();
    }

    /**
     * Set charging limit (percentage 70-100)
     */
    public static boolean setChargingLimit(Context context, int limit) {
        SmartChargingController controller = SmartChargingController.getInstance(context);
        return controller.setChargingLimit(limit);
    }

    /**
     * Get current charging limit (percentage)
     */
    public static int getChargingLimit(Context context) {
        return SmartChargingController.getInstance(context).getChargingLimit();
    }

    /**
     * Restore smart charging state from shared preferences (for boot)
     */
    public static void restore(Context context) {
        if (!isSupported()) {
            return;
        }
        SmartChargingController.getInstance(context).restore();
    }
}
