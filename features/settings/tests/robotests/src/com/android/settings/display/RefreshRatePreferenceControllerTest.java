/*
 * Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.TreeSet;

@RunWith(RobolectricTestRunner.class)
public class RefreshRatePreferenceControllerTest {
    private static final float FRACTIONAL_RATE = 59.940002f;
    private final Display.Mode mActive = new Display.Mode(1, 1000, 2000, FRACTIONAL_RATE);
    private Context mContext;
    private Display mDisplay;
    private RefreshRatePreferenceController mController;
    private ListPreference mPreference;

    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        DisplayManager manager = mock(DisplayManager.class);
        mDisplay = mock(Display.class);
        doReturn(manager).when(mContext).getSystemService(DisplayManager.class);
        when(manager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);
        when(mDisplay.getMode()).thenReturn(mActive);
        when(mDisplay.getSupportedModes()).thenReturn(new Display.Mode[] {
                mActive, new Display.Mode(2, 1000, 2000, 90f),
                new Display.Mode(3, 2000, 4000, 120f)
        });
        mController = new RefreshRatePreferenceController(mContext, Settings.System.PEAK_REFRESH_RATE);
        mPreference = new ListPreference(mContext);
        mPreference.setPersistent(false);
    }

    @Test
    public void supportedRatesFiltersResolutionDuplicatesAndInvalidRates() {
        Display.Mode[] modes = {
                new Display.Mode(1, 1000, 2000, 90f), mActive,
                new Display.Mode(3, 1000, 2000, 90f),
                new Display.Mode(4, 2000, 4000, 120f),
                new Display.Mode(5, 1000, 2000, Float.NaN),
                new Display.Mode(6, 1000, 2000, Float.POSITIVE_INFINITY),
                new Display.Mode(7, 1000, 2000, 0f)
        };
        assertEquals(new TreeSet<>(Arrays.asList(FRACTIONAL_RATE, 90f)),
                RefreshRatePreferenceController.supportedRates(mActive, modes));
    }

    @Test
    public void selectionPreservesExactFractionalRateAndMinimum() {
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, 30f);
        assertTrue(mController.onPreferenceChange(mPreference, Float.toString(FRACTIONAL_RATE)));
        assertEquals(FRACTIONAL_RATE, Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, 0f), 0f);
        assertEquals(30f, Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, 0f), 0f);
    }

    @Test
    public void autoRemovesPeakOverrideWithoutChangingMinimum() {
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, 90f);
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, 30f);
        assertTrue(mController.onPreferenceChange(mPreference, "auto"));
        assertNull(Settings.System.getString(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE));
        assertEquals(30f, Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, 0f), 0f);
    }

    @Test
    public void invalidAndOtherResolutionRatesAreRejected() {
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, 90f);
        for (String value : Arrays.asList("120.0", "60.0", "NaN", "Infinity", "invalid")) {
            assertFalse(mController.onPreferenceChange(mPreference, value));
        }
        assertEquals(90f, Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, 0f), 0f);
    }

    @Test
    public void refreshDoesNotRewriteExistingLimit() {
        Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, 72f);
        mController.updateState(mPreference);
        assertNull(mPreference.getValue());
        assertEquals(72f, Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, 0f), 0f);
    }

    @Test
    public void selectionRechecksResolutionAfterDialogWasOpened() {
        when(mDisplay.getMode()).thenReturn(new Display.Mode(3, 2000, 4000, 120f));
        assertFalse(mController.onPreferenceChange(mPreference, "90.0"));
        assertTrue(mController.onPreferenceChange(mPreference, "120.0"));
    }
}
