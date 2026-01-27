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
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.preference.LayoutPreference;

import java.io.File;

public class CameraAppFragment extends SettingsBasePreferenceFragment
        implements SelectorWithWidgetPreference.OnClickListener {

    private static final String KEY_OPLUS_CAMERA = "camera_oplus";
    private static final String KEY_GCAM = "camera_gcam";
    private static final String KEY_CAMERA_CATEGORY = "camera_app_category";

    private static final int PROGRESS_POLL_INTERVAL_MS = 500;

    // Gcam card state
    private static final int STATE_NOT_INSTALLED = 0;
    private static final int STATE_DOWNLOADING = 1;
    private static final int STATE_DOWNLOADED = 2;
    private static final int STATE_INSTALLING = 3;
    private static final int STATE_INSTALLED = 4;

    private SelectorWithWidgetPreference mOplusCameraPreference;
    private LayoutPreference mGcamCard;

    // GCam card views
    private View mGcamCardRoot;
    private View mContent;
    private ProgressBar mProgressBar;
    private RadioButton mGcamRadio;
    private TextView mGcamTitle;
    private TextView mGcamSummary;
    private Button mInstallButton;
    private Button mRetryButton;

    private GCamDownloader mGCamDownloader;
    private boolean mIsGcamInstalled;
    private Handler mHandler;
    private boolean mIsDownloading;
    private int mGcamState = STATE_NOT_INSTALLED;

    // Receiver for install status updates from InstallSessionReceiver
    private final BroadcastReceiver mInstallStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (InstallSessionReceiver.ACTION_INSTALL_STATUS_CHANGED.equals(intent.getAction())) {
                boolean success = intent.getBooleanExtra(
                        InstallSessionReceiver.EXTRA_INSTALL_SUCCESS, false);
                onInstallStatusChanged(success);
            }
        }
    };

    private final Runnable mProgressPoller = new Runnable() {
        @Override
        public void run() {
            if (mGCamDownloader == null || !mIsDownloading) return;

            int progress = mGCamDownloader.queryProgress();
            if (progress >= 0) {
                updateDownloadProgress(progress);
                if (progress < 100) {
                    mHandler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS);
                }
            } else {
                mHandler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.camera_app_preferences);

        PreferenceCategory category = findPreference(KEY_CAMERA_CATEGORY);

        mOplusCameraPreference = findPreference(KEY_OPLUS_CAMERA);
        mGcamCard = findPreference(KEY_GCAM);
        mHandler = new Handler(Looper.getMainLooper());

        // Setup Oplus Camera preference
        if (mOplusCameraPreference != null) {
            if (CameraAppUtils.isPackageInstalled(getContext(), CameraAppController.OPLUS_CAMERA_PKG)) {
                mOplusCameraPreference.setOnClickListener(this);
            } else {
                category.removePreference(mOplusCameraPreference);
                mOplusCameraPreference = null;
            }
        }

        // Setup GCam card
        if (mGcamCard != null) {
            initGcamCardViews();
        }

        mIsGcamInstalled = CameraAppUtils.isPackageInstalled(getContext(), CameraAppController.GCAM_PKG);
        updateGcamCardState();
        updateCheckedState();

        // Register for install status broadcasts
        IntentFilter filter = new IntentFilter(InstallSessionReceiver.ACTION_INSTALL_STATUS_CHANGED);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mInstallStatusReceiver, filter);
    }

    private void onInstallStatusChanged(boolean success) {
        if (success) {
            mIsGcamInstalled = true;
            mIsDownloading = false;
            stopProgressPolling();
            setGcamState(STATE_INSTALLED);
            updateCheckedState();
        } else {
            // Install failed, reset to downloaded state so user can retry
            mIsDownloading = false;
            setGcamState(STATE_DOWNLOADED);
        }
    }

    private void initGcamCardViews() {
        mGcamCardRoot = mGcamCard.findViewById(R.id.gcam_card_root);
        mContent = mGcamCard.findViewById(R.id.gcam_content);
        mProgressBar = mGcamCard.findViewById(R.id.gcam_progress);
        mGcamRadio = mGcamCard.findViewById(R.id.gcam_radio);
        mGcamTitle = mGcamCard.findViewById(R.id.gcam_title);
        mGcamSummary = mGcamCard.findViewById(R.id.gcam_summary);
        mInstallButton = mGcamCard.findViewById(R.id.gcam_install_btn);
        mRetryButton = mGcamCard.findViewById(R.id.gcam_retry_btn);

        if (mGcamCardRoot != null) {
            mGcamCardRoot.setOnClickListener(v -> onGcamCardClicked());
        }

        if (mInstallButton != null) {
            mInstallButton.setOnClickListener(v -> onInstallClicked());
        }

        if (mRetryButton != null) {
            mRetryButton.setOnClickListener(v -> {
                mRetryButton.setVisibility(View.GONE);
                startGcamDownload();
            });
        }
    }

    private void onGcamCardClicked() {
        if (mIsGcamInstalled) {
            selectGcam();
        } else if (mGcamState == STATE_NOT_INSTALLED) {
            File downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File apkFile = new File(downloadDir, CameraAppController.GCAM_APK_NAME);
            if (apkFile.exists()) {
                setGcamState(STATE_DOWNLOADED);
            } else {
                startGcamDownload();
            }
        }
        // Ignore clicks during downloading/installing/downloaded states
    }

    private void selectGcam() {
        if (CameraAppUtils.setSelectedCamera(getContext(), CameraAppController.GCAM_PKG)) {
            updateCheckedState();
            Toast.makeText(getContext(),
                    getString(R.string.camera_app_switched, getString(R.string.camera_gcam_title)),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(),
                    R.string.camera_app_switch_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void onInstallClicked() {
        if (getActivity() == null) return;

        setGcamState(STATE_INSTALLING);
        GCamDownloader.promptInstallFromFile(getActivity());
    }

    private void setGcamState(int state) {
        mGcamState = state;
        applyGcamState();
    }

    private void applyGcamState() {
        if (mProgressBar == null || mContent == null
                || mGcamRadio == null || mGcamSummary == null
                || mInstallButton == null || mRetryButton == null) {
            return;
        }

        mRetryButton.setVisibility(View.GONE);

        switch (mGcamState) {
            case STATE_NOT_INSTALLED:
                mProgressBar.setVisibility(View.GONE);
                mContent.setAlpha(0.5f);
                mGcamRadio.setChecked(false);
                mGcamSummary.setText(R.string.camera_gcam_not_installed);
                mInstallButton.setVisibility(View.GONE);
                break;

            case STATE_DOWNLOADING:
                showDeterminateProgress(0);
                mContent.setAlpha(0.5f);
                mGcamRadio.setChecked(false);
                mGcamSummary.setText(getString(R.string.gcam_downloading_percent_format, 0));
                mInstallButton.setVisibility(View.GONE);
                break;

            case STATE_DOWNLOADED:
                mProgressBar.setVisibility(View.GONE);
                mContent.setAlpha(1.0f);
                mGcamRadio.setChecked(false);
                mGcamSummary.setText(R.string.gcam_download_complete);
                mInstallButton.setVisibility(View.VISIBLE);
                break;

            case STATE_INSTALLING:
                showIndeterminateProgress();
                mContent.setAlpha(0.7f);
                mGcamRadio.setChecked(false);
                mGcamSummary.setText(R.string.gcam_installing);
                mInstallButton.setVisibility(View.GONE);
                break;

            case STATE_INSTALLED:
                mProgressBar.setVisibility(View.GONE);
                mContent.setAlpha(1.0f);
                mGcamRadio.setChecked(
                        CameraAppController.GCAM_PKG.equals(
                                CameraAppUtils.getSelectedCamera(getContext())));
                mGcamSummary.setText(R.string.camera_gcam_summary);
                mInstallButton.setVisibility(View.GONE);
                break;
        }
    }

    private void showDeterminateProgress(int percent) {
        if (mProgressBar == null) return;

        mProgressBar.setIndeterminate(false);
        mProgressBar.setProgressDrawable(
                getContext().getDrawable(R.drawable.circular_progress_drawable));
        mProgressBar.setProgress(percent);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void showIndeterminateProgress() {
        if (mProgressBar == null) return;

        mProgressBar.setIndeterminate(true);
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(
                android.R.attr.colorAccent, typedValue, true);
        mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(typedValue.data));
        mProgressBar.setVisibility(View.VISIBLE);
    }

    private void updateDownloadProgress(int progress) {
        if (mGcamState != STATE_DOWNLOADING) return;

        if (mProgressBar != null) {
            mProgressBar.setProgress(progress);
        }

        if (mGcamSummary != null) {
            mGcamSummary.setText(getString(R.string.gcam_downloading_percent_format, progress));
        }

        // Interpolate content alpha from 0.5 to 1.0 based on progress
        if (mContent != null) {
            float alpha = 0.5f + (0.5f * progress / 100.0f);
            mContent.setAlpha(alpha);
        }
    }

    private void updateGcamCardState() {
        if (mIsGcamInstalled) {
            setGcamState(STATE_INSTALLED);
        } else {
            // Check if APK is already downloaded
            File downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File apkFile = new File(downloadDir, CameraAppController.GCAM_APK_NAME);
            if (apkFile.exists()) {
                setGcamState(STATE_DOWNLOADED);
            } else {
                setGcamState(STATE_NOT_INSTALLED);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean wasInstalled = mIsGcamInstalled;
        mIsGcamInstalled = CameraAppUtils.isPackageInstalled(getContext(), CameraAppController.GCAM_PKG);

        if (mIsGcamInstalled) {
            mIsDownloading = false;
            stopProgressPolling();
            setGcamState(STATE_INSTALLED);
        } else if (!mIsDownloading) {
            updateGcamCardState();
        }

        updateCheckedState();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopProgressPolling();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopProgressPolling();

        // Unregister install status receiver
        if (getContext() != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mInstallStatusReceiver);
        }
    }

    private void updateCheckedState() {
        String selected = CameraAppUtils.getSelectedCamera(getContext());

        if (mOplusCameraPreference != null) {
            mOplusCameraPreference.setChecked(
                    CameraAppController.OPLUS_CAMERA_PKG.equals(selected));
        }

        if (mGcamRadio != null && mGcamState == STATE_INSTALLED) {
            mGcamRadio.setChecked(CameraAppController.GCAM_PKG.equals(selected));
        }
    }

    @Override
    public void onRadioButtonClicked(SelectorWithWidgetPreference preference) {
        String key = preference.getKey();

        if (KEY_OPLUS_CAMERA.equals(key)) {
            if (CameraAppUtils.setSelectedCamera(getContext(), CameraAppController.OPLUS_CAMERA_PKG)) {
                updateCheckedState();
                Toast.makeText(getContext(),
                        getString(R.string.camera_app_switched, preference.getTitle()),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(),
                        R.string.camera_app_switch_failed,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startGcamDownload() {
        if (mGCamDownloader == null) {
            mGCamDownloader = new GCamDownloader(getContext());
        }

        setGcamState(STATE_DOWNLOADING);

        mGCamDownloader.startDownload(new GCamDownloader.DownloadCompleteListener() {
            @Override
            public void onDownloadComplete(boolean success, Uri apkUri) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        mIsDownloading = false;
                        stopProgressPolling();

                        if (success) {
                            updateDownloadProgress(100);
                            setGcamState(STATE_DOWNLOADED);
                        } else {
                            setGcamState(STATE_NOT_INSTALLED);
                            showRetryButton();
                        }
                    });
                }
            }
        });

        mIsDownloading = true;
        startProgressPolling();
    }

    private void showRetryButton() {
        if (mRetryButton != null) {
            mRetryButton.setVisibility(View.VISIBLE);
        }
        if (mGcamSummary != null) {
            mGcamSummary.setText(R.string.gcam_download_failed);
        }
    }

    private void startProgressPolling() {
        mHandler.postDelayed(mProgressPoller, PROGRESS_POLL_INTERVAL_MS);
    }

    private void stopProgressPolling() {
        mHandler.removeCallbacks(mProgressPoller);
    }
}
