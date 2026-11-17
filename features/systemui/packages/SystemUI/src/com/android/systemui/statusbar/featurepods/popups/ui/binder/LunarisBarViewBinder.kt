/*
 * Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.featurepods.popups.ui.binder

import android.content.Context
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandEnabled
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.featurepods.popups.ui.compose.StatusBarDynamicIslandContainer
import com.android.systemui.statusbar.featurepods.popups.ui.view.DynamicIslandIconFader
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipsViewModelStore
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Adds the opt-in island to the existing status bar window and owns its entire view session. */
@SysUISingleton
class LunarisBarViewBinder @Inject constructor(
    private val models: Provider<StatusBarPopupChipsViewModelStore>,
    private val keyguard: KeyguardInteractor,
    private val userTracker: UserTracker,
    private val mediaHierarchyManager: MediaHierarchyManager,
    private val interruptionDecisions: Provider<com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProvider>,
) {
    private val boundViews = WeakHashMap<ViewGroup, Boolean>()
    private data class HostState(val userId: Int, val enabled: Boolean)

    fun bind(view: ViewGroup, lockscreen: Boolean) {
        if (view.context.displayId != Display.DEFAULT_DISPLAY || boundViews.put(view, true) != null) {
            return
        }
        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val keyguardState = combine(keyguard.isKeyguardShowing, keyguard.isKeyguardOccluded) {
                    showing, occluded -> showing to occluded
                }
                combine(
                    observeDynamicIslandEnabled(view.context),
                    observeUser(view.context),
                    keyguard.isDozing,
                    keyguardState,
                    observeDynamicIslandFeatureEnabled(
                        view.context, DynamicIslandFeatureSettings.LOCKSCREEN, false,
                    ),
                ) { enabled, user, dozing, state, showOnLockscreen ->
                    HostState(
                        user,
                        enabled && !dozing && !state.second &&
                            if (lockscreen) state.first && showOnLockscreen else !state.first,
                    )
                }.distinctUntilChanged().collectLatest { state ->
                    if (!state.enabled) return@collectLatest
                    models.get().withModel(view.context.displayId) { model ->
                        var visibleNotificationKeys = emptySet<String>()
                        val fader = DynamicIslandIconFader(view)
                        val originalClipChildren = view.clipChildren
                        val originalClipToPadding = view.clipToPadding
                        val host = ComposeView(view.context).apply {
                            clipChildren = false
                            clipToPadding = false
                            layoutParams = when (view) {
                                is RelativeLayout -> RelativeLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
                                else -> FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    Gravity.CENTER_VERTICAL,
                                )
                            }
                            setViewCompositionStrategy(ViewCompositionStrategy.Default)
                            setContent {
                                PlatformTheme {
                                    StatusBarDynamicIslandContainer(
                                        chips = if (lockscreen) model.lockscreenPopupChips
                                            else model.shownPopupChips,
                                        mediaTransitionChip = if (lockscreen) null
                                            else model.mediaTransitionChip,
                                        onMediaControlPopupVisibilityChanged = {
                                            mediaHierarchyManager.isMediaControlPopupShowing = it
                                        },
                                        onIslandBoundsChanged = fader::update,
                                        onIslandActiveChanged = { active ->
                                            visibility = if (active) View.VISIBLE else View.INVISIBLE
                                        },
                                        onIslandMotionChanged = fader::setMotionSettings,
                                        onVisibleNotificationKeysChanged = { visibleNotificationKeys = it },
                                    )
                                }
                            }
                        }
                        view.clipChildren = false
                        view.clipToPadding = false
                        view.addView(host)
                        fader.attach(host)
                        fader.setEnabled(true)
                        val filter = object : com.android.systemui.statusbar.notification.interruption.VisualInterruptionFilter(
                            setOf(com.android.systemui.statusbar.notification.interruption.VisualInterruptionType.PEEK),
                            "Notification is visible in Lunarisbar",
                        ) {
                            override fun shouldSuppress(entry: com.android.systemui.statusbar.notification.collection.NotificationEntry): Boolean =
                                host.isShown && view.isShown && entry.key in visibleNotificationKeys
                        }
                        val decisions = interruptionDecisions.get()
                        decisions.addFilter(filter)
                        try {
                            awaitCancellation()
                        } finally {
                            decisions.removeFilter(filter)
                            visibleNotificationKeys = emptySet()
                            fader.detach()
                            host.disposeComposition()
                            view.removeView(host)
                            view.clipChildren = originalClipChildren
                            view.clipToPadding = originalClipToPadding
                            mediaHierarchyManager.isMediaControlPopupShowing = false
                        }
                    }
                }
            }
        }
    }

    private fun observeUser(context: Context) = callbackFlow {
        val callback = object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                trySend(newUser)
            }
        }
        userTracker.addCallback(callback, context.mainExecutor)
        trySend(userTracker.userId)
        awaitClose { userTracker.removeCallback(callback) }
    }
}
