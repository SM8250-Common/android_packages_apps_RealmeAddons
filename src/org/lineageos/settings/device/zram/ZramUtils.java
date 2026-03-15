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

package org.lineageos.settings.device.zram;

import org.lineageos.settings.device.utils.FileUtils;

public class ZramUtils {

    private static final String ZRAM_SYSFS = "/sys/block/zram0";
    private static final String ZRAM_SIZE = ZRAM_SYSFS + "/disksize";
    private static final String MEMINFO_TOTAL = "/proc/meminfo";
    private static final String PROC_SWAPS = "/proc/swaps";
    private static final String ZRAM_BLOCK_DEVICE = "/dev/block/zram0";

    public static boolean isSupported() {
        return FileUtils.fileExists(ZRAM_SYSFS);
    }

    public static boolean isEnabled() {
        String swaps = FileUtils.getFileValue(PROC_SWAPS, "");
        return swaps.contains(ZRAM_BLOCK_DEVICE);
    }

    public static boolean setEnabled(boolean enabled) {
        if (enabled) {
            String currentSize = FileUtils.readOneLine(ZRAM_SIZE);
            if (currentSize == null || currentSize.equals("0")) {
                long totalMem = getTotalMemoryMB();
                long zramSize = Math.max(1024, totalMem / 2);
                return FileUtils.writeLine(ZRAM_SIZE, (zramSize * 1024 * 1024) + "");
            }
            return true;
        } else {
            return FileUtils.writeLine(ZRAM_SIZE, "0");
        }
    }

    public static long getCurrentSizeMB() {
        String size = FileUtils.readOneLine(ZRAM_SIZE);
        if (size != null) {
            try {
                return Long.parseLong(size.trim()) / (1024 * 1024);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public static boolean setSize(long sizeMB) {
        return FileUtils.writeLine(ZRAM_SIZE, (sizeMB * 1024 * 1024) + "");
    }

    private static long getTotalMemoryMB() {
        String memInfo = FileUtils.readOneLine(MEMINFO_TOTAL);
        if (memInfo != null) {
            try {
                String[] parts = memInfo.split("\\s+");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[1]) / 1024;
                }
            } catch (NumberFormatException e) {
                return 2048;
            }
        }
        return 2048;
    }
}
