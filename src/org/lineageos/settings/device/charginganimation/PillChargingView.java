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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class PillChargingView extends View {

    private static final int PILL_WIDTH_DP = 220;
    private static final int PILL_HEIGHT_DP = 56;
    private static final int BOLT_CIRCLE_SIZE_DP = 40;
    private static final int BOLT_MARGIN_DP = 8;

    private Paint mPillBackgroundPaint;
    private Paint mFillPaint;
    private Paint mBoltCirclePaint;
    private Paint mBoltPaint;
    private Paint mTextPaint;
    private Paint mPercentPaint;

    private RectF mPillRect;
    private RectF mBoltCircleRect;
    private RectF mFillRect;
    private Path mBoltPath;

    private int mBatteryLevel = 0;
    private int mAnimatedLevel = 0;
    private float mScale = 1.0f;
    private float mDensity;
    private int mGradientColor = Color.parseColor("#00BFA5");

    private ValueAnimator mLevelAnimator;
    private ValueAnimator mPulseAnimator;
    private float mPulseAlpha = 1.0f;

    public PillChargingView(Context context) {
        super(context);
        init(context);
    }

    public PillChargingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PillChargingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mDensity = context.getResources().getDisplayMetrics().density;

        // Detect system theme
        boolean isDarkTheme = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        // Pill background (grey for better contrast)
        mPillBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPillBackgroundPaint.setColor(Color.parseColor("#404040"));
        mPillBackgroundPaint.setStyle(Paint.Style.FILL);

        // Battery fill gradient
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);

        // Bolt circle background - white for light theme, dark for dark theme
        mBoltCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBoltCirclePaint.setColor(isDarkTheme ? Color.parseColor("#2A2A2A") : Color.WHITE);
        mBoltCirclePaint.setStyle(Paint.Style.FILL);

        // Bolt icon - white for dark theme, dark for light theme
        mBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBoltPaint.setColor(isDarkTheme ? Color.WHITE : Color.parseColor("#1A1A1A"));
        mBoltPaint.setStyle(Paint.Style.FILL);

        // Percentage text
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        // Percent symbol
        mPercentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPercentPaint.setColor(Color.parseColor("#CCFFFFFF"));
        mPercentPaint.setTextAlign(Paint.Align.LEFT);
        mPercentPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        mPillRect = new RectF();
        mBoltCircleRect = new RectF();
        mFillRect = new RectF();
        mBoltPath = new Path();

        startPulseAnimation();
    }

    private void startPulseAnimation() {
        mPulseAnimator = ValueAnimator.ofFloat(0.7f, 1.0f);
        mPulseAnimator.setDuration(1500);
        mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mPulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mPulseAnimator.addUpdateListener(animation -> {
            mPulseAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        mPulseAnimator.start();
    }

    public void setBatteryLevel(int level) {
        if (mBatteryLevel == level) return;

        int oldLevel = mAnimatedLevel;
        mBatteryLevel = level;

        if (mLevelAnimator != null && mLevelAnimator.isRunning()) {
            mLevelAnimator.cancel();
        }

        mLevelAnimator = ValueAnimator.ofInt(oldLevel, level);
        mLevelAnimator.setDuration(500);
        mLevelAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mLevelAnimator.addUpdateListener(animation -> {
            mAnimatedLevel = (int) animation.getAnimatedValue();
            invalidate();
        });
        mLevelAnimator.start();
    }

    public void setScale(float scale) {
        mScale = scale;
        requestLayout();
        invalidate();
    }

    public void setGradientColor(int color) {
        mGradientColor = color;
        invalidate();
    }

    public void setGradientColor(String colorHex) {
        try {
            mGradientColor = Color.parseColor(colorHex);
            invalidate();
        } catch (Exception e) {
            // Keep default color
        }
    }

    private int getLighterColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, hsv[1] - 0.3f); // Reduce saturation
        hsv[2] = Math.min(1f, hsv[2] + 0.2f); // Increase brightness
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) (PILL_WIDTH_DP * mDensity * mScale);
        int height = (int) (PILL_HEIGHT_DP * mDensity * mScale);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDimensions();
    }

    private void updateDimensions() {
        int w = getWidth();
        int h = getHeight();

        float pillRadius = h / 2f;
        mPillRect.set(0, 0, w, h);

        float boltMargin = BOLT_MARGIN_DP * mDensity * mScale;
        float boltSize = BOLT_CIRCLE_SIZE_DP * mDensity * mScale;
        float boltTop = (h - boltSize) / 2f;
        mBoltCircleRect.set(boltMargin, boltTop, boltMargin + boltSize, boltTop + boltSize);

        // Text sizes
        mTextPaint.setTextSize(24 * mDensity * mScale);
        mPercentPaint.setTextSize(14 * mDensity * mScale);

        // Create bolt path
        createBoltPath();
    }

    private void createBoltPath() {
        mBoltPath.reset();

        float cx = mBoltCircleRect.centerX();
        float cy = mBoltCircleRect.centerY();
        float size = mBoltCircleRect.width() * 0.35f;

        // Lightning bolt shape
        mBoltPath.moveTo(cx + size * 0.1f, cy - size);
        mBoltPath.lineTo(cx - size * 0.4f, cy + size * 0.1f);
        mBoltPath.lineTo(cx, cy + size * 0.1f);
        mBoltPath.lineTo(cx - size * 0.1f, cy + size);
        mBoltPath.lineTo(cx + size * 0.4f, cy - size * 0.1f);
        mBoltPath.lineTo(cx, cy - size * 0.1f);
        mBoltPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float pillRadius = h / 2f;
        float margin = BOLT_MARGIN_DP * mDensity * mScale * 0.5f;

        // Draw pill background
        canvas.drawRoundRect(mPillRect, pillRadius, pillRadius, mPillBackgroundPaint);

        // Calculate fill area (starts from left edge, fills entire pill width)
        float fillStartX = margin;
        float fillEndX = w - margin;
        float fillWidth = fillEndX - fillStartX;
        float currentFillWidth = fillWidth * (mAnimatedLevel / 100f);

        // Draw battery fill with gradient (from absolute left)
        if (mAnimatedLevel > 0) {
            // Set fill rect - use same bounds as pill for proper curvature matching
            // Start from 0 to ensure left edge clips properly to pill shape
            mFillRect.set(0, 0, fillStartX + currentFillWidth, h);

            // Create gradient from base color to lighter variant
            int startColor = mGradientColor;
            int endColor = getLighterColor(mGradientColor);
            LinearGradient gradient = new LinearGradient(
                    fillStartX, 0, fillEndX, 0,
                    startColor, endColor,
                    Shader.TileMode.CLAMP);
            mFillPaint.setShader(gradient);
            mFillPaint.setAlpha((int) (255 * mPulseAlpha));

            // Clip to pill shape first to ensure fill never exceeds pill boundaries
            canvas.save();
            Path clipPath = new Path();
            clipPath.addRoundRect(mPillRect, pillRadius, pillRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);

            // Draw fill as a rectangle - the clip path handles the pill curvature
            // This creates a "water fill" effect with flat right edge
            canvas.drawRect(mFillRect, mFillPaint);
            canvas.restore();
        }

        // Draw bolt circle (on top of gradient)
        canvas.drawOval(mBoltCircleRect, mBoltCirclePaint);

        // Draw bolt icon
        canvas.drawPath(mBoltPath, mBoltPaint);

        // Draw percentage text (centered in the area after bolt circle)
        String percentText = String.valueOf(mAnimatedLevel);
        float textAreaStart = mBoltCircleRect.right;
        float textAreaEnd = w - margin;
        // Shift text slightly left (4dp) to appear more visually centered
        float textX = (textAreaStart + textAreaEnd) / 2f - (4 * mDensity * mScale);
        float textY = h / 2f - ((mTextPaint.descent() + mTextPaint.ascent()) / 2f);

        // Measure text width for positioning percent symbol
        float textWidth = mTextPaint.measureText(percentText);
        float totalWidth = textWidth + mPercentPaint.measureText("%") + 2 * mDensity * mScale;

        // Center the combined text
        float startX = textX - totalWidth / 2f;

        canvas.drawText(percentText, startX + textWidth / 2f, textY, mTextPaint);
        canvas.drawText("%", startX + textWidth + 2 * mDensity * mScale, textY, mPercentPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mLevelAnimator != null) {
            mLevelAnimator.cancel();
        }
        if (mPulseAnimator != null) {
            mPulseAnimator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mPulseAnimator != null && !mPulseAnimator.isRunning()) {
            mPulseAnimator.start();
        }
    }
}
