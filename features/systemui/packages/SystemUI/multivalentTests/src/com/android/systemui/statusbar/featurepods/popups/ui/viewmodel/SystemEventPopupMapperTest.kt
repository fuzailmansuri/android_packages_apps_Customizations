/* Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.statusbar.featurepods.popups.ui.viewmodel

import android.content.Intent
import android.os.BatteryManager
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.ActivityIntentHelper
import com.android.systemui.SysuiTestCase
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import com.android.systemui.statusbar.policy.KeyguardStateController
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class SystemEventPopupMapperTest : SysuiTestCase() {
    private fun details(current: Int) = SystemEventPopupMapper(
        mContext.createConfigurationContext(
            android.content.res.Configuration(mContext.resources.configuration).apply {
                setLocales(LocaleList(Locale.US))
            }
        ),
        mock(ActivityStarter::class.java),
        mock(ActivityIntentHelper::class.java),
        mock(NotificationLockscreenUserManager::class.java),
        mock(KeyguardStateController::class.java),
    ).toChargingDetails(Intent(Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        putExtra(BatteryManager.EXTRA_MAX_CHARGING_CURRENT, current)
        putExtra(BatteryManager.EXTRA_MAX_CHARGING_VOLTAGE, 9_000_000)
        putExtra(BatteryManager.EXTRA_TEMPERATURE, 365)
    }).associate { it.unit to it.value }

    @Test
    fun subAmpCurrentFormatsWithoutCrashingSystemUi() {
        assertEquals("900", details(900_000)["mA"])
        assertEquals("8.1", details(900_000)["W"])
    }

    @Test
    fun ampCurrentAndUnknownCurrentKeepCorrectUnits() {
        val charging = details(2_000_000)
        assertEquals("2.0", charging["A"])
        assertEquals("18.0", charging["W"])
        assertEquals("9.0", charging["V"])
        assertEquals("36.5", charging["°C"])
        assertEquals("9.0", details(-1)["V"])
    }
}
