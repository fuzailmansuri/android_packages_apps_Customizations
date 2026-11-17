/*
 * Copyright (C) 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display.darkmode

import android.content.Context
import android.provider.Settings
import com.android.settings.R
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.SettingsSecureStore
import com.android.settingslib.metadata.SwitchPreference

class PureBlackThemePreference : SwitchPreference(
    key = Settings.Secure.BERRY_BLACK_THEME,
    purpose = R.string.berry_black_theme_title,
    title = R.string.berry_black_theme_title,
    summary = R.string.berry_black_theme_summary,
) {
    override fun storage(context: Context): KeyValueStore = SettingsSecureStore.get(context)
}
