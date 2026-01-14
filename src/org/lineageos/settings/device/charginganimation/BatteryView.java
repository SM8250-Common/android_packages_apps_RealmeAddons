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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class BatteryView extends View {

    // Base dimensions in dp
    private static final float CIRCLE_SIZE_DP = 180f;
    private static final float STROKE_WIDTH_DP = 4f;

    private float mCircleSize;
    private float mStrokeWidth;

    private int mBatteryLevel = 0;
    private int mTargetLevel = 0;
    private float mScale = 1.0f;
    private boolean mShowText = true;
    private boolean mShowBolt = true;

    private ValueAnimator mLevelAnimator;
    private ValueAnimator mWaveAnimator;
    private ValueAnimator mPulseAnimator;

    private float mWaveOffset = 0f;
    private float mPulseScale = 1f;

    private final Paint mCirclePaint;
    private final Paint mFillPaint;
    private final Paint mWavePaint;
    private final Paint mGlowPaint;
    private final Paint mTextPaint;
    private final Paint mPercentPaint;
    private final Paint mBoltPaint;
    private final Paint mRingPaint;

    private final Path mWavePath;
    private final Path mBoltPath;

    public BatteryView(Context context) {
        this(context, null);
    }

    public BatteryView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mCircleSize = dpToPx(CIRCLE_SIZE_DP);
        mStrokeWidth = dpToPx(STROKE_WIDTH_DP);

        mWavePath = new Path();
        mBoltPath = new Path();

        // Outer ring paint
        mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStrokeWidth(mStrokeWidth);
        mRingPaint.setColor(0x40FFFFFF);

        // Circle background
        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setStyle(Paint.Style.FILL);
        mCirclePaint.setColor(0x15FFFFFF);

        // Fill paint for the liquid
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);

        // Wave paint
        mWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaint.setStyle(Paint.Style.FILL);

        // Glow effect
        mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGlowPaint.setStyle(Paint.Style.FILL);

        // Main percentage text
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(dpToPx(56f));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setFakeBoldText(true);
        mTextPaint.setShadowLayer(dpToPx(4f), 0, 0, 0x80000000);

        // Percent symbol paint
        mPercentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPercentPaint.setStyle(Paint.Style.FILL);
        mPercentPaint.setColor(0xCCFFFFFF);
        mPercentPaint.setTextSize(dpToPx(20f));
        mPercentPaint.setTextAlign(Paint.Align.LEFT);

        // Bolt icon paint
        mBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBoltPaint.setStyle(Paint.Style.FILL);
        mBoltPaint.setColor(0xFFFFFFFF);
        mBoltPaint.setShadowLayer(dpToPx(6f), 0, 0, 0x60000000);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
        startAnimations();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    public void setScale(float scale) {
        mScale = scale;
        requestLayout();
        invalidate();
    }

    public void setShowText(boolean show) {
        mShowText = show;
        invalidate();
    }

    public void setShowBolt(boolean show) {
        mShowBolt = show;
        invalidate();
    }

    public void setBatteryLevel(int level) {
        mTargetLevel = Math.max(0, Math.min(100, level));
        if (mBatteryLevel != mTargetLevel) {
            animateLevelChange();
        }
    }

    private void startAnimations() {
        // Wave animation
        mWaveAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        mWaveAnimator.setDuration(2000);
        mWaveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mWaveAnimator.setInterpolator(new LinearInterpolator());
        mWaveAnimator.addUpdateListener(animation -> {
            mWaveOffset = (float) animation.getAnimatedValue();
            invalidate();
        });
        mWaveAnimator.start();

        // Pulse animation for glow
        mPulseAnimator = ValueAnimator.ofFloat(0.95f, 1.05f);
        mPulseAnimator.setDuration(1500);
        mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mPulseAnimator.addUpdateListener(animation -> {
            mPulseScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        mPulseAnimator.start();
    }

    private void animateLevelChange() {
        if (mLevelAnimator != null && mLevelAnimator.isRunning()) {
            mLevelAnimator.cancel();
        }

        mLevelAnimator = ValueAnimator.ofInt(mBatteryLevel, mTargetLevel);
        mLevelAnimator.setDuration(800);
        mLevelAnimator.addUpdateListener(animation -> {
            mBatteryLevel = (int) animation.getAnimatedValue();
            updateColors();
            invalidate();
        });
        mLevelAnimator.start();
    }

    private void updateColors() {
        int[] colors = getGradientColors();
        // Update fill and wave shaders will be done in onDraw
    }

    private int[] getGradientColors() {
        if (mBatteryLevel <= 20) {
            // Red gradient for low battery
            return new int[]{0xFFFF6B6B, 0xFFEE5A5A};
        } else if (mBatteryLevel <= 50) {
            // Orange/Yellow gradient
            return new int[]{0xFFFFB347, 0xFFFF8C00};
        } else if (mBatteryLevel <= 80) {
            // Cyan/Blue gradient
            return new int[]{0xFF00D4FF, 0xFF0099CC};
        } else {
            // Green gradient for high battery
            return new int[]{0xFF00E676, 0xFF00C853};
        }
    }

    private int getPrimaryColor() {
        if (mBatteryLevel <= 20) return 0xFFFF6B6B;
        if (mBatteryLevel <= 50) return 0xFFFFB347;
        if (mBatteryLevel <= 80) return 0xFF00D4FF;
        return 0xFF00E676;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (mCircleSize * mScale + dpToPx(60f));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = mCircleSize * mScale;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = size / 2f;

        // Draw outer glow
        drawGlow(canvas, centerX, centerY, radius);

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, mCirclePaint);

        // Draw outer ring
        float ringRadius = radius + mStrokeWidth * mScale;
        mRingPaint.setStrokeWidth(mStrokeWidth * mScale);
        canvas.drawCircle(centerX, centerY, ringRadius, mRingPaint);

        // Draw liquid fill with waves
        drawLiquidFill(canvas, centerX, centerY, radius);

        // Draw text
        if (mShowText) {
            drawText(canvas, centerX, centerY);
        }

        // Draw bolt icon
        if (mShowBolt) {
            drawBolt(canvas, centerX, centerY - radius * 0.35f);
        }
    }

    private void drawGlow(Canvas canvas, float cx, float cy, float radius) {
        int primaryColor = getPrimaryColor();
        float glowRadius = radius * 1.3f * mPulseScale;

        int alpha = (int) (40 + (mBatteryLevel / 100f) * 30);
        int glowColor = Color.argb(alpha, Color.red(primaryColor),
                Color.green(primaryColor), Color.blue(primaryColor));

        RadialGradient gradient = new RadialGradient(cx, cy, glowRadius,
                new int[]{glowColor, Color.TRANSPARENT},
                new float[]{0.5f, 1f}, Shader.TileMode.CLAMP);
        mGlowPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, glowRadius, mGlowPaint);
    }

    private void drawLiquidFill(Canvas canvas, float cx, float cy, float radius) {
        if (mBatteryLevel <= 0) return;

        canvas.save();

        // Clip to circle
        Path clipPath = new Path();
        clipPath.addCircle(cx, cy, radius - mStrokeWidth * mScale * 0.5f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // Calculate fill height
        float fillHeight = (mBatteryLevel / 100f) * (radius * 2);
        float fillTop = cy + radius - fillHeight;

        int[] colors = getGradientColors();

        // Create gradient for fill
        LinearGradient fillGradient = new LinearGradient(
                cx, cy + radius, cx, fillTop,
                colors[0], colors[1], Shader.TileMode.CLAMP);
        mFillPaint.setShader(fillGradient);

        // Draw main fill
        canvas.drawRect(cx - radius, fillTop, cx + radius, cy + radius, mFillPaint);

        // Draw waves on top
        drawWaves(canvas, cx, cy, radius, fillTop, colors);

        canvas.restore();
    }

    private void drawWaves(Canvas canvas, float cx, float cy, float radius, float fillTop, int[] colors) {
        // Primary wave
        mWavePath.reset();
        float waveHeight = dpToPx(8f) * mScale;
        float waveLength = radius * 0.8f;

        mWavePath.moveTo(cx - radius, fillTop + waveHeight);

        for (float x = -radius; x <= radius; x += 5) {
            float y = fillTop + (float) Math.sin((x / waveLength) * Math.PI * 2 + mWaveOffset) * waveHeight;
            mWavePath.lineTo(cx + x, y);
        }

        mWavePath.lineTo(cx + radius, cy + radius);
        mWavePath.lineTo(cx - radius, cy + radius);
        mWavePath.close();

        // Wave gradient with slight transparency
        LinearGradient waveGradient = new LinearGradient(
                cx, fillTop, cx, cy + radius,
                Color.argb(200, Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0])),
                Color.argb(230, Color.red(colors[1]), Color.green(colors[1]), Color.blue(colors[1])),
                Shader.TileMode.CLAMP);
        mWavePaint.setShader(waveGradient);
        canvas.drawPath(mWavePath, mWavePaint);

        // Secondary wave (offset)
        mWavePath.reset();
        float wave2Height = dpToPx(5f) * mScale;

        mWavePath.moveTo(cx - radius, fillTop + wave2Height);

        for (float x = -radius; x <= radius; x += 5) {
            float y = fillTop + (float) Math.sin((x / waveLength) * Math.PI * 2 + mWaveOffset + Math.PI) * wave2Height;
            mWavePath.lineTo(cx + x, y);
        }

        mWavePath.lineTo(cx + radius, cy + radius);
        mWavePath.lineTo(cx - radius, cy + radius);
        mWavePath.close();

        // Slightly more transparent second wave
        mWavePaint.setShader(new LinearGradient(
                cx, fillTop, cx, cy + radius,
                Color.argb(150, Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0])),
                Color.argb(180, Color.red(colors[1]), Color.green(colors[1]), Color.blue(colors[1])),
                Shader.TileMode.CLAMP));
        canvas.drawPath(mWavePath, mWavePaint);
    }

    private void drawText(Canvas canvas, float cx, float cy) {
        String levelStr = String.valueOf(mBatteryLevel);

        mTextPaint.setTextSize(dpToPx(56f) * mScale);
        mPercentPaint.setTextSize(dpToPx(20f) * mScale);

        // Measure text width for positioning
        float levelWidth = mTextPaint.measureText(levelStr);
        float percentWidth = mPercentPaint.measureText("%");
        float totalWidth = levelWidth + percentWidth + dpToPx(2f) * mScale;

        float startX = cx - totalWidth / 2f;
        float textY = cy + dpToPx(20f) * mScale;

        // Draw level number
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(levelStr, startX, textY, mTextPaint);

        // Draw percent symbol
        float percentX = startX + levelWidth + dpToPx(2f) * mScale;
        float percentY = textY - dpToPx(8f) * mScale;
        canvas.drawText("%", percentX, percentY, mPercentPaint);
    }

    private void drawBolt(Canvas canvas, float cx, float cy) {
        float boltScale = mScale * 0.7f;
        float boltWidth = dpToPx(20f) * boltScale;
        float boltHeight = dpToPx(36f) * boltScale;

        mBoltPath.reset();

        // Lightning bolt shape
        mBoltPath.moveTo(cx + boltWidth * 0.1f, cy - boltHeight * 0.5f);
        mBoltPath.lineTo(cx - boltWidth * 0.35f, cy + boltHeight * 0.05f);
        mBoltPath.lineTo(cx - boltWidth * 0.05f, cy + boltHeight * 0.05f);
        mBoltPath.lineTo(cx - boltWidth * 0.15f, cy + boltHeight * 0.5f);
        mBoltPath.lineTo(cx + boltWidth * 0.35f, cy - boltHeight * 0.05f);
        mBoltPath.lineTo(cx + boltWidth * 0.05f, cy - boltHeight * 0.05f);
        mBoltPath.close();

        canvas.drawPath(mBoltPath, mBoltPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mLevelAnimator != null && mLevelAnimator.isRunning()) {
            mLevelAnimator.cancel();
        }
        if (mWaveAnimator != null && mWaveAnimator.isRunning()) {
            mWaveAnimator.cancel();
        }
        if (mPulseAnimator != null && mPulseAnimator.isRunning()) {
            mPulseAnimator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Restart animations when view is attached
        if (mWaveAnimator != null && !mWaveAnimator.isRunning()) {
            mWaveAnimator.start();
        }
        if (mPulseAnimator != null && !mPulseAnimator.isRunning()) {
            mPulseAnimator.start();
        }
    }
}
