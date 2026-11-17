/*
 * Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.display;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.provider.Settings;
import android.view.Display;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.text.NumberFormat;
import java.util.SortedSet;
import java.util.TreeSet;

/** Selects a user peak-rate limit; DisplayModeDirector still owns display policy. */
public class RefreshRatePreferenceController extends BasePreferenceController implements
        Preference.OnPreferenceChangeListener, LifecycleObserver, OnStart, OnStop {
    private static final String AUTO = "auto";
    private final DisplayManager mDisplayManager;
    private final Handler mHandler;
    private ListPreference mPreference;

    private final ContentObserver mSettingsObserver;
    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) { onDisplayChanged(displayId); }

                @Override
                public void onDisplayRemoved(int displayId) { onDisplayChanged(displayId); }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) updateState(mPreference);
                }
            };

    public RefreshRatePreferenceController(Context context, String key) {
        super(context, key);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mHandler = new Handler(context.getMainLooper());
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) { updateState(mPreference); }
        };
    }

    @VisibleForTesting
    static SortedSet<Float> supportedRates(Display.Mode activeMode, Display.Mode[] modes) {
        SortedSet<Float> rates = new TreeSet<>();
        for (Display.Mode mode : modes) {
            float rate = mode.getRefreshRate();
            if (mode.getPhysicalWidth() == activeMode.getPhysicalWidth()
                    && mode.getPhysicalHeight() == activeMode.getPhysicalHeight()
                    && Float.isFinite(rate) && rate > 0f) {
                rates.add(rate);
            }
        }
        return rates;
    }

    private SortedSet<Float> supportedRates() {
        Display display = mDisplayManager == null ? null
                : mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return display == null ? new TreeSet<>()
                : supportedRates(display.getMode(), display.getSupportedModes());
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_smooth_display)
                && supportedRates().size() > 1 ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference == null) return;
        mPreference.setOnPreferenceChangeListener(this);
        updateState(mPreference);
    }

    @Override
    public void updateState(Preference preference) {
        if (!(preference instanceof ListPreference)) return;
        ListPreference list = (ListPreference) preference;
        SortedSet<Float> rates = supportedRates();
        list.setVisible(mContext.getResources().getBoolean(R.bool.config_show_smooth_display)
                && rates.size() > 1);
        CharSequence[] entries = new CharSequence[rates.size() + 1];
        CharSequence[] values = new CharSequence[entries.length];
        entries[0] = mContext.getString(R.string.refresh_rate_selector_auto);
        values[0] = AUTO;
        NumberFormat format = NumberFormat.getNumberInstance();
        int index = 1;
        for (float rate : rates) {
            entries[index] = mContext.getString(R.string.refresh_rate_selector_hz,
                    format.format(rate));
            values[index++] = Float.toString(rate);
        }
        list.setEntries(entries);
        list.setEntryValues(values);
        String raw = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE);
        float peak = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, Float.NaN);
        // The old Smooth display switch stored infinity for the highest available rate.
        if (peak == Float.POSITIVE_INFINITY && !rates.isEmpty()) peak = rates.last();
        String selected = raw == null || peak == 0f || Float.isNaN(peak) ? AUTO
                : rates.contains(peak) ? Float.toString(peak) : null;
        list.setValue(selected);
        list.setSummary(selected == null
                ? mContext.getString(R.string.refresh_rate_selector_current, format.format(peak))
                : list.getEntry());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (!(value instanceof String)) return false;
        if (AUTO.equals(value)) {
            return Settings.System.putString(mContext.getContentResolver(),
                    Settings.System.PEAK_REFRESH_RATE, null);
        }
        try {
            float rate = Float.parseFloat((String) value);
            // Recheck the active resolution in case it changed while the dialog was open.
            return supportedRates().contains(rate) && Settings.System.putFloat(
                    mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, rate);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onStart() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.PEAK_REFRESH_RATE), false,
                mSettingsObserver);
        if (mDisplayManager != null) {
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
        }
        updateState(mPreference);
    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        if (mDisplayManager != null) mDisplayManager.unregisterDisplayListener(mDisplayListener);
    }
}
