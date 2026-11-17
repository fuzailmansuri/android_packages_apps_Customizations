/* Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.system

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.test.core.app.ApplicationProvider
import com.android.settings.R
import com.google.android.material.slider.Slider
import com.yasp.settings.preferences.LunarisBarSeekBarPreference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LunarisBarSeekBarPreferenceTest {
    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(), R.style.Theme_Settings)

    private fun preference(key: String, min: Int, max: Int, value: Int) =
        requireNotNull(PreferenceManager(context).inflateFromResource(
            context, R.xml.lunaris_bar_preference_templates, null)
            .findPreference<LunarisBarSeekBarPreference>("slider")).apply {
            this.key = key
            setMax(max)
            setMin(min)
            setValue(value)
        }

    @Test
    fun recycledRowOnlyChangesItsCurrentPreference() {
        val width = preference("status_bar_dynamic_island_width_scale", 70, 140, 70)
        val offset = preference("offset_x", -200, 200, 0)
        val holder = PreferenceViewHolder.createInstanceForTests(
            LayoutInflater.from(context).inflate(R.layout.preference_custom_seekbar, null))
        val slider = holder.findViewById(R.id.slider) as Slider
        var widthChanges = 0
        var offsetChanges = 0
        width.setOnPreferenceChangeListener { _, _ -> widthChanges++; true }
        offset.setOnPreferenceChangeListener { _, _ -> offsetChanges++; true }
        width.onBindViewHolder(holder)
        offset.onBindViewHolder(holder)

        width.setMax(140)
        width.setMin(70)
        width.setValue(100)
        assertEquals(-200f, slider.valueFrom, 0f)
        assertEquals(200f, slider.valueTo, 0f)
        assertEquals(0f, slider.value, 0f)
        assertEquals(0, widthChanges)
        assertEquals(0, offsetChanges)

        slider.value = 12f
        assertEquals(100, width.value)
        assertEquals(12, offset.value)
        assertEquals(0, widthChanges)
        assertEquals(1, offsetChanges)
    }
}
