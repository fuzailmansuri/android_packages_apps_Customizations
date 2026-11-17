/*
 * Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.compose

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalCircularQsTileSize
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalUseCircularQsTileShapes
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository

/** Shares the current user's tile appearance across expanded, compact and edit grids. */
@Composable
internal fun CircularQsTileSettings(
    settings: SystemSettingsRepository,
    content: @Composable () -> Unit,
) {
    val enabled by
        remember(settings) { settings.boolSetting(Settings.System.QS_TILES_CIRCULAR, false) }
            .collectAsStateWithLifecycle(false)
    val size by
        remember(settings) { settings.intSetting(Settings.System.QS_TILES_CIRCULAR_SIZE, 72) }
            .collectAsStateWithLifecycle(72)

    CompositionLocalProvider(
        LocalUseCircularQsTileShapes provides enabled,
        LocalCircularQsTileSize provides size.coerceIn(72, 80),
        content = content,
    )
}
