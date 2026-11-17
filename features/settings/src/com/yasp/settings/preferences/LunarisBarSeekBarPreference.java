/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.yasp.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.PreferenceViewHolder;
import com.android.settings.R;
import com.google.android.material.slider.Slider;

/** Uses the same seek bar and reset controls as the other settings pages. */
public class LunarisBarSeekBarPreference extends CustomSeekBarPreference {
    public LunarisBarSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        // A recycled row must stop dispatching changes to its previous preference.
        Slider slider = (Slider) holder.findViewById(R.id.slider);
        slider.clearOnChangeListeners();
        slider.clearOnSliderTouchListeners();
        super.onBindViewHolder(holder);
    }

    @Override
    public void setMax(int max) {
        mMaxValue = max;
        notifyChanged();
    }

    @Override
    public void setMin(int min) {
        mMinValue = min;
        notifyChanged();
    }

    @Override
    public void setValue(int value) {
        // mSlider can belong to another preference after RecyclerView reuses the row.
        mValue = getLimitedValue(value);
        notifyChanged();
    }

    @Override
    protected String getTextValue(int value) {
        String key = getKey();
        if (key == null) return super.getTextValue(value);
        if (key.equals("animation_speed")) return (value / 100f) + "×";
        if (key.equals("outline_width")) return (value / 10f) + " dp";
        if (key.contains("scale") || key.contains("opacity")) return value + "%";
        if (key.endsWith("delay") || key.equals("hide_after")) return value + " s";
        if (key.equals("minutes")) return getContext().getString(
                com.android.settings.R.string.lunaris_bar_minutes_value, value);
        if (key.equals("columns")) return Integer.toString(value);
        return value + " dp";
    }
}
