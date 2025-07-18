/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.intelligence.search.query;

import static com.android.settings.intelligence.search.sitemap.HighlightableMenu.MENU_KEY_APPS;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import com.android.settings.intelligence.R;
import com.android.settings.intelligence.nano.SettingsIntelligenceLogProto;
import com.android.settings.intelligence.search.AppSearchResult;
import com.android.settings.intelligence.search.ResultPayload;
import com.android.settings.intelligence.search.SearchResult;
import com.android.settings.intelligence.search.indexing.DatabaseIndexingUtils;
import com.android.settings.intelligence.search.sitemap.HighlightableMenu;
import com.android.settings.intelligence.search.sitemap.SiteMapManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Search loader for installed apps.
 */
public class InstalledAppResultTask extends SearchQueryTask.QueryWorker {

    private static final int QUERY_WORKER_ID =
            SettingsIntelligenceLogProto.SettingsIntelligenceEvent.SEARCH_QUERY_INSTALLED_APPS;
    private static final String INTENT_SCHEME = "package";

    private final PackageManager mPackageManager;
    private List<String> mBreadcrumb;

    public static SearchQueryTask newTask(Context context, SiteMapManager siteMapManager,
            String query) {
        return new SearchQueryTask(new InstalledAppResultTask(context, siteMapManager, query));
    }

    public InstalledAppResultTask(Context context, SiteMapManager siteMapManager,
            String query) {
        super(context, siteMapManager, query);
        mPackageManager = context.getPackageManager();
    }

    @Override
    protected int getQueryWorkerId() {
        return QUERY_WORKER_ID;
    }

    @Override
    protected List<? extends SearchResult> query() {
        final List<AppSearchResult> results = new ArrayList<>();

        List<ApplicationInfo> appsInfo = mPackageManager.getInstalledApplications(
                PackageManager.MATCH_DISABLED_COMPONENTS
                        | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_INSTANT);

        for (ApplicationInfo info : appsInfo) {
            if (!info.enabled
                    && mPackageManager.getApplicationEnabledSetting(info.packageName)
                    != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                // Disabled by something other than user, skip.
                continue;
            }
            try {
                ModuleInfo moduleInfo = mPackageManager.getModuleInfo(info.packageName, 0);
                if(moduleInfo.isHidden()) {
                    // The app is hidden, skip
                    continue;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // this should not happen here
                continue;
            }

            final CharSequence label = info.loadLabel(mPackageManager);
            final int wordDiff = SearchQueryUtils.getWordDifference(label.toString(), mQuery);
            if (wordDiff == SearchQueryUtils.NAME_NO_MATCH) {
                continue;
            }

            final Intent targetIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts(INTENT_SCHEME, info.packageName, null /* fragment */))
                    .putExtra(DatabaseIndexingUtils.EXTRA_SOURCE_METRICS_CATEGORY,
                            DatabaseIndexingUtils.DASHBOARD_SEARCH_RESULTS);
            final Intent intent = HighlightableMenu.isFeatureEnabled(mContext)
                    ? DatabaseIndexingUtils.buildSearchTrampolineIntent(targetIntent, MENU_KEY_APPS)
                    : targetIntent;

            final AppSearchResult.Builder builder = new AppSearchResult.Builder();
            builder.setAppInfo(info)
                    .setDataKey(info.packageName)
                    .setTitle(info.loadLabel(mPackageManager))
                    .setRank(getRank(wordDiff))
                    .addBreadcrumbs(getBreadCrumb())
                    .setPayload(new ResultPayload(intent));
            results.add(builder.build());
        }

        Collections.sort(results);
        return results;
    }

    private List<String> getBreadCrumb() {
        if (mBreadcrumb == null || mBreadcrumb.isEmpty()) {
            mBreadcrumb = mSiteMapManager.buildBreadCrumb(
                    mContext, "com.android.settings.applications.ManageApplications",
                    mContext.getString(R.string.applications_settings));
        }
        return mBreadcrumb;
    }

    /**
     * A temporary ranking scheme for installed apps.
     *
     * @param wordDiff difference between query length and app name length.
     * @return the ranking.
     */
    private int getRank(int wordDiff) {
        if (wordDiff < 6) {
            return 2;
        }
        return 3;
    }
}

