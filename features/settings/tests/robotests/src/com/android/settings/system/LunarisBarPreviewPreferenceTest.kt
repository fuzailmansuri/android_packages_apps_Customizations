/* Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.system

import android.content.Context
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceViewHolder
import androidx.test.core.app.ApplicationProvider
import com.android.internal.util.lunaris.LunarisBarSettings
import com.android.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LunarisBarPreviewPreferenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tapAndRefreshKeepPreviewLocal() {
        val original = Settings.System.getString(context.contentResolver, LunarisBarSettings.KEY)
        val preference = LunarisBarPreviewPreference(context)
        assertFalse(preference.isPersistent)
        assertEquals(Int.MIN_VALUE, preference.order)
        val holder = PreferenceViewHolder.createInstanceForTests(
            LayoutInflater.from(context).inflate(R.layout.lunaris_bar_preview, null))
        preference.onBindViewHolder(holder)
        val view = holder.findViewById(R.id.lunaris_bar_preview_view) as LunarisBarPreviewView
        assertTrue(view.performClick())
        assertEquals(context.getString(R.string.lunaris_bar_preview_expanded), view.stateDescription)
        preference.update(LunarisBarSettings().with("corner_radius", 12), 140, 140)
        preference.onBindViewHolder(holder)
        assertEquals(context.getString(R.string.lunaris_bar_preview_expanded), view.stateDescription)
        view.performClick()
        assertEquals(context.getString(R.string.lunaris_bar_preview_compact), view.stateDescription)
        assertEquals(original, Settings.System.getString(context.contentResolver, LunarisBarSettings.KEY))
    }

    @Test
    fun largerChipAndExpansionReserveMoreSpace() {
        val view = LunarisBarPreviewView(context)
        val width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.bind(LunarisBarSettings(), 100, 100, false)
        view.measure(width, height)
        val compactHeight = view.measuredHeight
        val larger = LunarisBarSettings().with("mini_scale", 200).with("offset_y", 100)
        view.bind(larger, 140, 140, false)
        view.measure(width, height)
        assertTrue(view.measuredHeight > compactHeight)
        val largerHeight = view.measuredHeight
        view.bind(larger, 140, 140, true)
        view.measure(width, height)
        assertTrue(view.measuredHeight > largerHeight)
    }
}
