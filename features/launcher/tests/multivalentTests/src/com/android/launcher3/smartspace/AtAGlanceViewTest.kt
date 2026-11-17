/* Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.smartspace

import android.view.View
import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.integration.util.LauncherActivityScenarioRule
import com.android.launcher3.testutil.rule.TestRules.overrideApplicationInActivity
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SandboxApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit

@LargeTest
@RunWith(LauncherMultivalentJUnit::class)
class AtAGlanceViewTest {
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val app = SandboxApplication().withModelDependency()
    @get:Rule val appOverride = overrideApplicationInActivity(app, mockito)
    @get:Rule val launcherActivity = LauncherActivityScenarioRule<Launcher>()

    @Test
    fun headerUsesTopInsetWithoutReservingWorkspaceCells() {
        launcherActivity.executeOnLauncher { launcher ->
            LauncherPrefs.get(launcher).put(LauncherPrefs.SHOW_SMARTSPACE, false)
            val originalPadding = launcher.workspace.paddingTop
            val screen = launcher.workspace.getScreenWithId(WorkspaceLayoutManager.FIRST_SCREEN_ID)
            val columns = screen.countX
            val rows = screen.countY
            LauncherPrefs.get(launcher).put(LauncherPrefs.SHOW_SMARTSPACE, true)
            val header = launcher.findViewById<AtAGlanceView>(R.id.launcher_smartspace)
            assertNotNull(header)
            assertSame(launcher.dragLayer, header.parent)
            assertEquals(null, header.tag)
            val insetTop = launcher.dragLayer.insets.top
            val expectedTop = insetTop + Math.round(16 * launcher.resources.displayMetrics.density)
            val params = header.layoutParams as android.view.ViewGroup.MarginLayoutParams
            assertEquals(expectedTop, params.topMargin)
            assertTrue(launcher.workspace.paddingTop >= params.topMargin + params.height)
            assertEquals(columns, screen.countX)
            assertEquals(rows, screen.countY)
            LauncherPrefs.get(launcher).put(LauncherPrefs.SHOW_SMARTSPACE, false)
            assertEquals(originalPadding, launcher.workspace.paddingTop)
        }
    }

    @Test
    fun headerRemainsVisibleWhileReturningHome() {
        launcherActivity.executeOnLauncher { launcher ->
            LauncherPrefs.get(launcher).put(LauncherPrefs.SHOW_SMARTSPACE, true)
            val header = launcher.findViewById<AtAGlanceView>(R.id.launcher_smartspace)
            header.onStateTransitionComplete(LauncherState.NORMAL)
            header.onStateTransitionStart(LauncherState.BACKGROUND_APP)
            assertEquals(View.VISIBLE, header.visibility)
            header.onStateTransitionComplete(LauncherState.BACKGROUND_APP)
            assertEquals(View.INVISIBLE, header.visibility)
            header.onStateTransitionStart(LauncherState.NORMAL)
            assertEquals(View.VISIBLE, header.visibility)
        }
    }

    @Test
    fun headerDisappearsInAllAppsAndWhenDisabled() {
        launcherActivity.executeOnLauncher { launcher ->
            LauncherPrefs.get(launcher).put(LauncherPrefs.SHOW_SMARTSPACE, true)
            launcher.stateManager.goToState(LauncherState.ALL_APPS, false)
            val header = launcher.findViewById<AtAGlanceView>(R.id.launcher_smartspace)
            assertEquals(View.INVISIBLE, header.visibility)
            launcher.stateManager.goToState(LauncherState.NORMAL, false)
            LauncherPrefs.get(launcher).put(LauncherPrefs.SHOW_SMARTSPACE, false)
            assertEquals(View.INVISIBLE, header.visibility)
        }
    }
}
