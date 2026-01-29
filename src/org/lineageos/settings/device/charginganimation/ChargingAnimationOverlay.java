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

package org.lineageos.settings.device.charginganimation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.airbnb.lottie.LottieAnimationView;

import org.lineageos.settings.device.R;

import java.io.File;
import java.io.FileInputStream;

import android.view.MotionEvent;

public class ChargingAnimationOverlay {

    private static final String TAG = "ChargingAnimationOverlay";

    public interface OnTouchListener {
        void onOverlayTouched();
    }

    private static final String PREF_POSITION = "charging_animation_position";
    private static final String PREF_SIZE = "charging_animation_size";
    private static final String PREF_OPACITY = "charging_animation_opacity";
    private static final String PREF_SHOW_TEXT = "charging_animation_show_text";
    private static final String PREF_SHOW_BOLT = "charging_animation_show_bolt";
    private static final String PREF_ANIMATION_STYLE = "charging_animation_style";
    private static final String PREF_BACKGROUND_STYLE = "charging_animation_background";
    private static final String PREF_PILL_COLOR = "charging_animation_pill_color";

    private static ChargingAnimationOverlay sInstance;
    private static final Object sLock = new Object();

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final SharedPreferences mPrefs;

    private View mOverlayView;
    private View mBackgroundOverlay;
    private BatteryView mBatteryView;
    private PillChargingView mPillChargingView;
    private LottieAnimationView mLottieView;
    private FrameLayout mLottieContainer;
    private LinearLayout mLottieBatteryInfo;
    private ImageView mLottieBoltIcon;
    private LinearLayout mLottiePercentageContainer;
    private TextView mLottieBatteryText;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mShowing = false;
    private boolean mPreviewShowing = false;
    private boolean mUsingLottie = false;
    private boolean mUsingPill = false;
    private OnTouchListener mTouchListener;

    public static synchronized ChargingAnimationOverlay getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ChargingAnimationOverlay(context);
        }
        return sInstance;
    }

    private ChargingAnimationOverlay(Context context) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void setOnTouchListener(OnTouchListener listener) {
        mTouchListener = listener;
    }

    public void show() {
        synchronized (sLock) {
            if (mShowing) {
                Log.d(TAG, "Already showing");
                return;
            }

            if (!canDrawOverlays()) {
                Log.e(TAG, "Cannot draw overlays - permission not granted");
                return;
            }

            createOverlay();

            try {
                mWindowManager.addView(mOverlayView, mLayoutParams);
                mShowing = true;
                updateBatteryLevel(getBatteryLevel());
                Log.d(TAG, "Charging animation shown");
            } catch (Exception e) {
                Log.e(TAG, "Failed to show overlay", e);
            }
        }
    }

    public void hide() {
        synchronized (sLock) {
            if (!mShowing || mOverlayView == null) {
                return;
            }

            try {
                if (mLottieView != null) {
                    mLottieView.cancelAnimation();
                }
                mWindowManager.removeView(mOverlayView);
                mOverlayView = null;
                mBackgroundOverlay = null;
                mBatteryView = null;
                mPillChargingView = null;
                mLottieView = null;
                mLottieContainer = null;
                mLottieBatteryInfo = null;
                mLottieBoltIcon = null;
                mLottiePercentageContainer = null;
                mLottieBatteryText = null;
                mShowing = false;
                mUsingPill = false;
                Log.d(TAG, "Charging animation hidden");
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide overlay", e);
            }
        }
    }

    public void showPreview(int positionPercent) {
        synchronized (sLock) {
            if (mShowing) return;

            if (!canDrawOverlays()) {
                Log.e(TAG, "Cannot draw overlays - permission not granted");
                return;
            }

            if (mPreviewShowing) {
                applyPositionPercent(positionPercent);
                return;
            }

            createOverlay();

            // Override position with the preview value
            applyPositionPercent(positionPercent);

            try {
                mWindowManager.addView(mOverlayView, mLayoutParams);
                mPreviewShowing = true;
                updateBatteryLevel(getBatteryLevel());
            } catch (Exception e) {
                Log.e(TAG, "Failed to show preview", e);
            }
        }
    }

    public void hidePreview() {
        synchronized (sLock) {
            if (!mPreviewShowing || mOverlayView == null) return;

            try {
                if (mLottieView != null) {
                    mLottieView.cancelAnimation();
                }
                mWindowManager.removeView(mOverlayView);
                mOverlayView = null;
                mBackgroundOverlay = null;
                mBatteryView = null;
                mPillChargingView = null;
                mLottieView = null;
                mLottieContainer = null;
                mLottieBatteryInfo = null;
                mLottieBoltIcon = null;
                mLottiePercentageContainer = null;
                mLottieBatteryText = null;
                mPreviewShowing = false;
                mUsingPill = false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide preview", e);
            }
        }
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void updateBatteryLevel(int level) {
        if (mShowing || mPreviewShowing) {
            if (mBatteryView != null) {
                mBatteryView.setBatteryLevel(level);
            }
            if (mPillChargingView != null) {
                mPillChargingView.setBatteryLevel(level);
            }
            if (mLottieBatteryText != null) {
                mLottieBatteryText.setText(String.valueOf(level));
            }
        }
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(mContext);
    }

    private void createOverlay() {
        mOverlayView = LayoutInflater.from(mContext).inflate(R.layout.charging_animation_view, null);
        mBackgroundOverlay = mOverlayView.findViewById(R.id.background_overlay);
        mBatteryView = mOverlayView.findViewById(R.id.battery_view);
        mPillChargingView = mOverlayView.findViewById(R.id.pill_charging_view);
        mLottieView = mOverlayView.findViewById(R.id.lottie_view);
        mLottieContainer = mOverlayView.findViewById(R.id.lottie_container);
        mLottieBatteryInfo = mOverlayView.findViewById(R.id.lottie_battery_info);
        mLottieBoltIcon = mOverlayView.findViewById(R.id.lottie_bolt_icon);
        mLottiePercentageContainer = mOverlayView.findViewById(R.id.lottie_percentage_container);
        mLottieBatteryText = mOverlayView.findViewById(R.id.lottie_battery_text);

        // Determine animation style
        String animStyle = mPrefs.getString(PREF_ANIMATION_STYLE, "classic");
        mUsingLottie = !animStyle.equals("classic") && !animStyle.equals("pill");
        mUsingPill = animStyle.equals("pill");

        if (mUsingPill) {
            // Use Pill charging view
            mBatteryView.setVisibility(View.GONE);
            mPillChargingView.setVisibility(View.VISIBLE);
            mLottieContainer.setVisibility(View.GONE);
            applyPillSettings();
        } else if (mUsingLottie) {
            // Use Lottie animation
            mBatteryView.setVisibility(View.GONE);
            mPillChargingView.setVisibility(View.GONE);
            mLottieContainer.setVisibility(View.VISIBLE);
            applyLottieAnimation(animStyle);
            applyLottieBatteryInfo();
        } else {
            // Use classic BatteryView
            mBatteryView.setVisibility(View.VISIBLE);
            mPillChargingView.setVisibility(View.GONE);
            mLottieContainer.setVisibility(View.GONE);
            applySettings();
        }

        // Create layout params - use TYPE_KEYGUARD_DIALOG to show ON TOP of lockscreen
        // Note: FLAG_NOT_TOUCHABLE is NOT used so we can detect touches and hide overlay
        // to allow PIN/password entry on the lockscreen
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);

        // Set up touch listener to notify service when user touches screen
        // The service will handle hiding the overlay to allow PIN/password entry
        mOverlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mTouchListener != null) {
                    mTouchListener.onOverlayTouched();
                }
            }
            return false; // Don't consume - allow touch to pass through after callback
        });

        // Apply position
        applyPosition();

        // Apply background style (opacity or blur)
        applyBackgroundStyle();
    }

    private void applySettings() {
        if (mBatteryView == null) return;

        // Apply size
        String size = mPrefs.getString(PREF_SIZE, "medium");
        float scale = getSizeScale(size);
        mBatteryView.setScale(scale);

        // Apply show text setting
        boolean showText = mPrefs.getBoolean(PREF_SHOW_TEXT, true);
        mBatteryView.setShowText(showText);

        // Apply show bolt setting - always show when charging
        boolean showBolt = mPrefs.getBoolean(PREF_SHOW_BOLT, true);
        mBatteryView.setShowBolt(showBolt);
    }

    private void applyPillSettings() {
        if (mPillChargingView == null) return;

        // Apply size
        String size = mPrefs.getString(PREF_SIZE, "medium");
        float scale = getSizeScale(size);
        mPillChargingView.setScale(scale);

        // Apply gradient color
        String colorHex = mPrefs.getString(PREF_PILL_COLOR, "#00BFA5");
        mPillChargingView.setGradientColor(colorHex);
    }

    private void applyPosition() {
        int percent;
        try {
            percent = mPrefs.getInt(PREF_POSITION, 50);
        } catch (ClassCastException e) {
            // Old value was stored as String from dropdown, migrate to int
            String old = mPrefs.getString(PREF_POSITION, "50");
            percent = Integer.parseInt(old);
            mPrefs.edit().remove(PREF_POSITION).putInt(PREF_POSITION, percent).apply();
        }
        applyPositionPercent(percent);
    }

    private void applyPositionPercent(int percent) {
        View targetView = mUsingPill ? mPillChargingView : (mUsingLottie ? mLottieContainer : mBatteryView);
        if (targetView == null) return;

        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) targetView.getLayoutParams();

        if (params == null) {
            params = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        }

        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int screenHeight = dm.heightPixels;
        int padding = 50;
        int availableHeight = screenHeight - padding * 2;

        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = padding + (int) (availableHeight * percent / 100f);
        params.bottomMargin = 0;

        targetView.setLayoutParams(params);
    }

    private void applyBackgroundStyle() {
        if (mBackgroundOverlay == null) return;

        String backgroundStyle = mPrefs.getString(PREF_BACKGROUND_STYLE, "blur_medium");

        switch (backgroundStyle) {
            case "blur_light":
                applyBlurEffect(15f);
                break;
            case "blur_medium":
                applyBlurEffect(25f);
                break;
            case "blur_heavy":
                applyBlurEffect(40f);
                break;
            case "none":
            default:
                applyNoEffect();
                break;
        }
    }

    private void applyBlurEffect(float blurRadius) {
        if (mBackgroundOverlay == null) return;

        // Use transparent background - no dim layer
        mBackgroundOverlay.setAlpha(0f);

        // Apply blur effect (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Set blur behind flag on window for transparent blur
            if (mLayoutParams != null) {
                mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                mLayoutParams.setBlurBehindRadius((int) blurRadius);
            }
        }
    }

    private void applyNoEffect() {
        if (mBackgroundOverlay == null) return;

        // Clear any blur effect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mBackgroundOverlay.setRenderEffect(null);
            if (mLayoutParams != null) {
                mLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            }
        }

        // Fully transparent
        mBackgroundOverlay.setAlpha(0f);
    }

    private float getSizeScale(String size) {
        switch (size) {
            case "small":
                return 0.6f;
            case "large":
                return 1.2f;
            case "xlarge":
                return 1.5f;
            case "medium":
            default:
                return 1.0f;
        }
    }

    private void applyLottieAnimation(String style) {
        if (mLottieView == null) return;

        // Check if custom animation
        if ("custom".equals(style)) {
            File customFile = new File(mContext.getFilesDir(),
                    ChargingAnimationFragment.CUSTOM_LOTTIE_FILENAME);
            if (customFile.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(customFile);
                    byte[] data = new byte[(int) customFile.length()];
                    fis.read(data);
                    fis.close();
                    String jsonContent = new String(data, "UTF-8");
                    // Use file modification time as cache key to ensure new imports are loaded
                    String cacheKey = "custom_animation_" + customFile.lastModified();
                    mLottieView.setAnimationFromJson(jsonContent, cacheKey);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load custom animation", e);
                    // Fallback to default
                    mLottieView.setAnimation(R.raw.lottie_charging_status);
                }
            } else {
                // No custom file, fallback to default
                mLottieView.setAnimation(R.raw.lottie_charging_status);
            }
        } else {
            // Get the raw resource ID based on animation style
            int rawResId = getLottieResourceId(style);
            mLottieView.setAnimation(rawResId);
        }

        // Apply size scaling
        String size = mPrefs.getString(PREF_SIZE, "medium");
        float scale = getSizeScale(size);
        int baseSize = (int) (200 * mContext.getResources().getDisplayMetrics().density);
        int scaledSize = (int) (baseSize * scale);

        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) mLottieView.getLayoutParams();
        if (params == null) {
            params = new android.widget.FrameLayout.LayoutParams(scaledSize, scaledSize);
        } else {
            params.width = scaledSize;
            params.height = scaledSize;
        }
        mLottieView.setLayoutParams(params);

        // Start animation
        mLottieView.playAnimation();
    }

    private void applyLottieBatteryInfo() {
        if (mLottieBatteryInfo == null) return;

        boolean showText = mPrefs.getBoolean(PREF_SHOW_TEXT, true);
        boolean showBolt = mPrefs.getBoolean(PREF_SHOW_BOLT, true);

        // Show the container if either option is enabled
        if (showText || showBolt) {
            mLottieBatteryInfo.setVisibility(View.VISIBLE);

            // Show/hide bolt icon
            if (mLottieBoltIcon != null) {
                mLottieBoltIcon.setVisibility(showBolt ? View.VISIBLE : View.GONE);
            }

            // Show/hide percentage
            if (mLottiePercentageContainer != null) {
                mLottiePercentageContainer.setVisibility(showText ? View.VISIBLE : View.GONE);
            }

            // Set initial battery level
            if (mLottieBatteryText != null) {
                mLottieBatteryText.setText(String.valueOf(getBatteryLevel()));
            }
        } else {
            mLottieBatteryInfo.setVisibility(View.GONE);
        }
    }

    private int getLottieResourceId(String style) {
        switch (style) {
            case "charging_status":
                return R.raw.lottie_charging_status;
            case "energy_bolt":
                return R.raw.lottie_energy_bolt;
            case "fast_thunder":
                return R.raw.lottie_fast_thunder;
            case "loading":
                return R.raw.lottie_loading;
            case "renewable_energy":
                return R.raw.lottie_renewable_energy;
            default:
                return R.raw.lottie_charging_status;
        }
    }

    private int getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, filter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) {
                return (level * 100) / scale;
            }
        }
        return 100;
    }
}
