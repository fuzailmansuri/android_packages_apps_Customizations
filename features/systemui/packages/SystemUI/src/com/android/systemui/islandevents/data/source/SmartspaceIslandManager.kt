/*
 * Copyright (C) 2025-2026 AxionOS
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.islandevents.data.source

import android.app.smartspace.SmartspaceTarget
import android.os.Parcelable
import com.android.systemui.islandevents.model.IslandEvent
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceTargetListener
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Adapts the shared event source to YAAP's existing platform Smartspace provider. */
class SmartspaceIslandManager @Inject constructor(
    private val smartspaceController: LockscreenSmartspaceController,
    private val userTracker: UserTracker,
) {
    private val mutableNowPlaying = MutableStateFlow<IslandEvent.NowPlaying?>(null)
    val nowPlayingEvent = mutableNowPlaying.asStateFlow()
    private var listening = false

    private val listener = object : SmartspaceTargetListener {
        override fun onSmartspaceTargetsUpdated(targets: List<Parcelable>) {
            if (!listening) return
            val now = System.currentTimeMillis()
            val target = targets.filterIsInstance<SmartspaceTarget>().firstOrNull {
                it.userHandle == userTracker.userHandle &&
                    it.creationTimeMillis <= now &&
                    (it.expiryTimeMillis <= 0 || it.expiryTimeMillis > now) &&
                    it.featureType == SmartspaceTarget.FEATURE_MEDIA
            }
            val title = target?.headerAction?.title?.toString()?.takeIf { it.isNotBlank() }
            mutableNowPlaying.value = if (title == null) null else IslandEvent.NowPlaying(
                songTitle = title,
                artist = target.headerAction?.subtitle?.toString().orEmpty(),
                key = target.smartspaceTargetId,
            )
        }
    }

    fun startListening() {
        if (listening) return
        listening = true
        smartspaceController.addListener(listener)
        smartspaceController.requestSmartspaceUpdate()
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        smartspaceController.removeListener(listener)
        mutableNowPlaying.value = null
    }
}
