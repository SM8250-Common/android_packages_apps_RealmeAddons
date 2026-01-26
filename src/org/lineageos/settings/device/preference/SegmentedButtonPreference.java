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

package org.lineageos.settings.device.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.settings.device.R;

public class SegmentedButtonPreference extends Preference {

    private static final int[] VALUES = {30, 40, 50, 60};
    private static final int[] BUTTON_IDS = {
            R.id.button_30, R.id.button_40, R.id.button_50, R.id.button_60
    };

    private int mValue = 30;
    private RadioGroup mToggleGroup;
    private OnValueChangedListener mListener;

    public interface OnValueChangedListener {
        void onValueChanged(int newValue);
    }

    public SegmentedButtonPreference(Context context) {
        this(context, null);
    }

    public SegmentedButtonPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentedButtonPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_segmented_button);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);

        TextView titleView = (TextView) holder.findViewById(R.id.title);
        TextView summaryView = (TextView) holder.findViewById(R.id.summary);
        mToggleGroup = (RadioGroup) holder.findViewById(R.id.toggle_group);

        if (titleView != null) {
            titleView.setText(getTitle());
        }

        if (summaryView != null) {
            CharSequence summary = getSummary();
            if (summary != null) {
                summaryView.setText(summary);
                summaryView.setVisibility(android.view.View.VISIBLE);
            } else {
                summaryView.setVisibility(android.view.View.GONE);
            }
        }

        if (mToggleGroup != null) {
            // Remove listener before setting checked to avoid triggering callback
            mToggleGroup.setOnCheckedChangeListener(null);

            // Set the current selection
            updateButtonSelection();

            // Add listener for changes
            mToggleGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int newValue = getValueForButtonId(checkedId);
                if (newValue != mValue) {
                    mValue = newValue;
                    if (mListener != null) {
                        mListener.onValueChanged(mValue);
                    }
                }
            });

            // Update enabled state for all buttons
            boolean enabled = isEnabled();
            for (int buttonId : BUTTON_IDS) {
                RadioButton button = mToggleGroup.findViewById(buttonId);
                if (button != null) {
                    button.setEnabled(enabled);
                }
            }
        }
    }

    private void updateButtonSelection() {
        if (mToggleGroup == null) return;

        int buttonId = getButtonIdForValue(mValue);
        mToggleGroup.check(buttonId);
    }

    private int getButtonIdForValue(int value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i] == value) {
                return BUTTON_IDS[i];
            }
        }
        return BUTTON_IDS[0];
    }

    private int getValueForButtonId(int buttonId) {
        for (int i = 0; i < BUTTON_IDS.length; i++) {
            if (BUTTON_IDS[i] == buttonId) {
                return VALUES[i];
            }
        }
        return VALUES[0];
    }

    public void setValue(int value) {
        // Snap to nearest valid value
        int snappedValue = VALUES[0];
        int minDiff = Math.abs(value - VALUES[0]);
        for (int v : VALUES) {
            int diff = Math.abs(value - v);
            if (diff < minDiff) {
                minDiff = diff;
                snappedValue = v;
            }
        }
        mValue = snappedValue;
        updateButtonSelection();
    }

    public int getValue() {
        return mValue;
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        mListener = listener;
    }
}
