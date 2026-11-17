/* Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.smartspace

import android.app.PendingIntent
import android.app.smartspace.SmartspaceTarget
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Launcher
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class SmartspaceActionTest {
    private val pm = mock<PackageManager>()
    private val context = mock<Context>().also {
        whenever(it.packageManager).thenReturn(pm)
    }
    private val target = SmartspaceTarget.Builder(
        "provider_card", ComponentName("provider", "provider.Service"), Process.myUserHandle()
    ).build()

    private fun resolvable(intent: Intent) {
        val result = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                exported = true
                packageName = "example.app"
            }
        }
        whenever(pm.resolveActivity(eq(intent), eq(0))).thenReturn(result)
    }

    private fun trampoline(inner: Intent) = Intent().setComponent(
        ComponentName("provider", "provider.SmartspaceTrampolineActivity")
    ).putExtra(Intent.EXTRA_INTENT, inner)

    @Test
    fun providerPendingIntentUsesLauncherAnimationHelper() {
        val launcher = mock<Launcher>()
        val view = mock<View>().also { whenever(it.context).thenReturn(launcher) }
        val pending = mock<PendingIntent>()
        SmartspaceCard.action(Intent(Intent.ACTION_VIEW), pending).onClick(view)
        verify(launcher).sendPendingIntentWithAnimation(eq(view), eq(pending), isNull())
        verify(launcher, never()).startActivitySafely(any(), any(), isNull())
    }

    @Test
    fun ordinaryIntentUsesLauncherSafeStartHelper() {
        val launcher = mock<Launcher>()
        val view = mock<View>().also { whenever(it.context).thenReturn(launcher) }
        SmartspaceCard.action(Intent(Intent.ACTION_VIEW), null).onClick(view)
        verify(launcher).startActivitySafely(eq(view), any(), isNull())
        verify(launcher, never()).sendPendingIntentWithAnimation(any(), any(), isNull())
    }

    @Test
    fun validWeatherDeepLinkPrecedesWeatherAppFallback() {
        val deepLink = Intent(Intent.ACTION_VIEW).setPackage("example.app")
        resolvable(deepLink)
        val weather = SmartspaceTarget.Builder(
            "weather", ComponentName("provider", "provider.Service"), Process.myUserHandle()
        ).setFeatureType(SmartspaceTarget.FEATURE_WEATHER).build()
        assertSame(deepLink, SmartspaceCard.sanitizeSmartspaceIntent(
            context, weather, SmartspaceEvent.WEATHER, deepLink
        ))
        verify(pm, never()).getLaunchIntentForPackage(any())
    }

    @Test
    fun nestedTrampolineRetainsProviderDestination() {
        val deepLink = Intent(Intent.ACTION_VIEW).setPackage("example.app")
        resolvable(deepLink)
        assertSame(deepLink, SmartspaceCard.sanitizeSmartspaceIntent(
            context, target, SmartspaceEvent.OTHER, trampoline(trampoline(deepLink))
        ))
    }

    @Test
    fun overlyNestedTrampolinesDoNotLaunch() {
        val deepLink = Intent(Intent.ACTION_VIEW).setPackage("example.app")
        resolvable(deepLink)
        var wrapped = deepLink
        repeat(5) { wrapped = trampoline(wrapped) }
        assertNull(SmartspaceCard.sanitizeSmartspaceIntent(
            context, target, SmartspaceEvent.OTHER, wrapped
        ))
    }

    @Test
    fun selfReferencingTrampolineDoesNotLoop() {
        val wrapped = Intent().setComponent(
            ComponentName("provider", "provider.SmartspaceTrampolineActivity")
        )
        wrapped.putExtra(Intent.EXTRA_INTENT, wrapped)
        assertNull(SmartspaceCard.sanitizeSmartspaceIntent(
            context, target, SmartspaceEvent.OTHER, wrapped
        ))
    }

    @Test
    fun reminderWithoutProviderActionDoesNotOpenAnUnrelatedApp() {
        assertNull(SmartspaceCard.sanitizeSmartspaceIntent(
            context, target, SmartspaceEvent.REMINDERS, null
        ))
        verify(pm, never()).getLaunchIntentForPackage(any())
        verify(pm, never()).resolveActivity(any(), any())
    }
}
