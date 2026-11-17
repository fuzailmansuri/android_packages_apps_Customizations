/*
 * SPDX-FileCopyrightText: 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.system;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class CircularQsSettings extends DashboardFragment {

    @Override
    protected String getLogTag() {
        return "CircularQsSettings";
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.YASP;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.yaap_settings_circular_qs;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.yaap_settings_circular_qs);
}
