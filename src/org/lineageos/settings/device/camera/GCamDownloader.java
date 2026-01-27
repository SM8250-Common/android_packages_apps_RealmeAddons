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

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.lineageos.settings.device.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class GCamDownloader {

    private static final String TAG = "GCamDownloader";

    // Public so InstallSessionReceiver can reference it
    public static final String INSTALL_ACTION = "org.lineageos.settings.device.INSTALL_COMPLETE";

    private final Context mContext;
    private final DownloadManager mDownloadManager;
    private long mDownloadId = -1;
    private DownloadCompleteListener mListener;

    public interface DownloadCompleteListener {
        void onDownloadComplete(boolean success, Uri apkUri);
    }

    public GCamDownloader(Context context) {
        mContext = context.getApplicationContext();
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public void startDownload(DownloadCompleteListener listener) {
        mListener = listener;

        // Delete existing file if present
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File apkFile = new File(downloadDir, CameraAppController.GCAM_APK_NAME);
        if (apkFile.exists()) {
            apkFile.delete();
        }

        // Mark download as pending
        CameraAppController.getInstance(mContext).setGcamDownloadPending(true);

        // Create download request
        DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(CameraAppController.GCAM_DOWNLOAD_URL));
        request.setTitle(mContext.getString(R.string.gcam_download_title));
        request.setDescription(mContext.getString(R.string.gcam_download_description));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                CameraAppController.GCAM_APK_NAME);
        request.setMimeType("application/vnd.android.package-archive");

        // Register receiver for download complete
        mContext.registerReceiver(mDownloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED);

        // Start download
        mDownloadId = mDownloadManager.enqueue(request);
        Log.d(TAG, "Started download with ID: " + mDownloadId);

        Toast.makeText(mContext, R.string.gcam_download_started, Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver mDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadId != mDownloadId) {
                return;
            }

            // Unregister receiver
            try {
                mContext.unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
                // Already unregistered
            }

            // Check download status
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(mDownloadId);
            Cursor cursor = mDownloadManager.query(query);

            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Log.d(TAG, "Download completed successfully");

                    // Get the downloaded file URI
                    File downloadDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File apkFile = new File(downloadDir, CameraAppController.GCAM_APK_NAME);

                    if (apkFile.exists()) {
                        // Verify APK integrity
                        if (verifyApkIntegrity(apkFile)) {
                            Uri apkUri = Uri.fromFile(apkFile);
                            if (mListener != null) {
                                mListener.onDownloadComplete(true, apkUri);
                            }
                        } else {
                            Log.e(TAG, "APK verification failed");
                            CameraAppController.getInstance(mContext).setGcamDownloadPending(false);
                            Toast.makeText(mContext, R.string.gcam_verify_failed, Toast.LENGTH_LONG).show();
                            // Delete corrupted file
                            apkFile.delete();
                            if (mListener != null) {
                                mListener.onDownloadComplete(false, null);
                            }
                        }
                    } else {
                        Log.e(TAG, "Downloaded file not found");
                        CameraAppController.getInstance(mContext).setGcamDownloadPending(false);
                        if (mListener != null) {
                            mListener.onDownloadComplete(false, null);
                        }
                    }
                } else {
                    Log.e(TAG, "Download failed with status: " + status);
                    CameraAppController.getInstance(mContext).setGcamDownloadPending(false);
                    Toast.makeText(mContext, R.string.gcam_download_failed, Toast.LENGTH_SHORT).show();
                    if (mListener != null) {
                        mListener.onDownloadComplete(false, null);
                    }
                }
                cursor.close();
            }
        }
    };

    /**
     * Verify APK integrity using SHA256 hash.
     * Returns true if verification passes or if no hash is configured (skip verification).
     */
    private boolean verifyApkIntegrity(File apkFile) {
        String expectedHash = CameraAppController.GCAM_APK_SHA256;

        // Skip verification if no hash is configured
        if (expectedHash == null || expectedHash.isEmpty()) {
            Log.d(TAG, "No SHA256 hash configured, skipping verification");
            return true;
        }

        Toast.makeText(mContext, R.string.gcam_verifying, Toast.LENGTH_SHORT).show();

        try {
            String actualHash = computeSha256(apkFile);
            boolean matches = expectedHash.equalsIgnoreCase(actualHash);

            if (matches) {
                Log.d(TAG, "APK integrity verified successfully");
            } else {
                Log.e(TAG, "APK hash mismatch! Expected: " + expectedHash + ", Got: " + actualHash);
            }

            return matches;
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to compute APK hash", e);
            return false;
        }
    }

    /**
     * Compute SHA256 hash of a file.
     */
    private String computeSha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Install APK using PackageInstaller Session API.
     * This is the recommended method for Android 12+ as it avoids URI permission issues.
     */
    private static void installApk(Context context, File apkFile) {
        // Check if we have permission to install packages
        if (!canInstallPackages(context)) {
            Log.w(TAG, "Install permission not granted, prompting user");
            Toast.makeText(context, R.string.gcam_install_permission_required, Toast.LENGTH_LONG).show();
            promptInstallPermission(context);
            return;
        }

        try {
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(CameraAppController.GCAM_PKG);

            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            // Write APK to session
            try (InputStream in = new FileInputStream(apkFile);
                 OutputStream out = session.openWrite("gcam.apk", 0, apkFile.length())) {
                byte[] buffer = new byte[65536];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                session.fsync(out);
            }

            // Create intent for install result - will be received by InstallSessionReceiver
            Intent intent = new Intent(INSTALL_ACTION);
            intent.setPackage(context.getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            // Commit the session (this triggers the install UI)
            session.commit(pendingIntent.getIntentSender());
            Log.d(TAG, "Install session committed");

        } catch (IOException e) {
            Log.e(TAG, "Failed to install APK", e);
            CameraAppController.getInstance(context).setGcamDownloadPending(false);
            Toast.makeText(context, R.string.gcam_install_failed, Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during install", e);
            CameraAppController.getInstance(context).setGcamDownloadPending(false);
            Toast.makeText(context, R.string.gcam_install_blocked, Toast.LENGTH_LONG).show();
        }
    }

    public long getDownloadId() {
        return mDownloadId;
    }

    public boolean isDownloading() {
        return mDownloadId != -1;
    }

    public int queryProgress() {
        if (mDownloadId == -1) return -1;

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(mDownloadId);
        Cursor cursor = mDownloadManager.query(query);

        if (cursor != null && cursor.moveToFirst()) {
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusIndex);

            if (status == DownloadManager.STATUS_RUNNING ||
                    status == DownloadManager.STATUS_PENDING) {
                int bytesIndex = cursor.getColumnIndex(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalIndex = cursor.getColumnIndex(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                long bytesDownloaded = cursor.getLong(bytesIndex);
                long totalBytes = cursor.getLong(totalIndex);
                cursor.close();

                if (totalBytes > 0) {
                    return (int) ((bytesDownloaded * 100) / totalBytes);
                }
                return 0;
            } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                cursor.close();
                return 100;
            }
            cursor.close();
        }
        return -1;
    }

    /**
     * Prompt user to install from an already-downloaded APK file.
     * Called when user clicks the Install button after download.
     */
    public static void promptInstallFromFile(Context context) {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File apkFile = new File(downloadDir, CameraAppController.GCAM_APK_NAME);

        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: " + apkFile.getAbsolutePath());
            Toast.makeText(context, R.string.gcam_download_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // Verify integrity before installing (for manual install button clicks)
        String expectedHash = CameraAppController.GCAM_APK_SHA256;
        if (expectedHash != null && !expectedHash.isEmpty()) {
            try {
                GCamDownloader downloader = new GCamDownloader(context);
                String actualHash = downloader.computeSha256(apkFile);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    Log.e(TAG, "APK hash mismatch on manual install");
                    Toast.makeText(context, R.string.gcam_verify_failed, Toast.LENGTH_LONG).show();
                    apkFile.delete();
                    return;
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                Log.e(TAG, "Failed to verify APK", e);
                Toast.makeText(context, R.string.gcam_verify_failed, Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Mark as pending before install
        CameraAppController.getInstance(context).setGcamDownloadPending(true);

        // Install using Session API
        installApk(context, apkFile);
    }

    /**
     * Check if the app has permission to install packages.
     * Note: System apps (with android.uid.system) always have this permission.
     */
    private static boolean canInstallPackages(Context context) {
        // System apps always have install permission
        if (android.os.Process.myUid() == android.os.Process.SYSTEM_UID) {
            return true;
        }
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Prompt user to grant install permission via system settings.
     */
    private static void promptInstallPermission(Context context) {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open install permission settings", e);
        }
    }
}
