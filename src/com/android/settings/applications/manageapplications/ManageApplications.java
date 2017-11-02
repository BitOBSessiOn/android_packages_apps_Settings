/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.settings.applications.manageapplications;

import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_BLOCKED;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_DISABLED;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_ENABLED;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_INSTANT;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_PERSONAL;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_POWER_WHITELIST;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_POWER_WHITELIST_ALL;
import static com.android.settings.applications.manageapplications.AppFilterRegistry
        .FILTER_APPS_WORK;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceFrameLayout;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.Settings;
import com.android.settings.Settings.GamesStorageActivity;
import com.android.settings.Settings.HighPowerApplicationsActivity;
import com.android.settings.Settings.ManageExternalSourcesActivity;
import com.android.settings.Settings.MoviesStorageActivity;
import com.android.settings.Settings.NotificationAppListActivity;
import com.android.settings.Settings.OverlaySettingsActivity;
import com.android.settings.Settings.StorageUseActivity;
import com.android.settings.Settings.UsageAccessSettingsActivity;
import com.android.settings.Settings.WriteSettingsActivity;
import com.android.settings.SettingsActivity;
import com.android.settings.applications.AppInfoBase;
import com.android.settings.applications.AppStateAppOpsBridge.PermissionState;
import com.android.settings.applications.AppStateBaseBridge;
import com.android.settings.applications.AppStateInstallAppsBridge;
import com.android.settings.applications.AppStateNotificationBridge;
import com.android.settings.applications.AppStateOverlayBridge;
import com.android.settings.applications.AppStatePowerBridge;
import com.android.settings.applications.AppStateUsageBridge;
import com.android.settings.applications.AppStateUsageBridge.UsageState;
import com.android.settings.applications.AppStateWriteSettingsBridge;
import com.android.settings.applications.AppStorageSettings;
import com.android.settings.applications.DefaultAppSettings;
import com.android.settings.applications.DrawOverlayDetails;
import com.android.settings.applications.ExternalSourcesDetails;
import com.android.settings.applications.InstalledAppCounter;
import com.android.settings.applications.InstalledAppDetails;
import com.android.settings.applications.NotificationApps;
import com.android.settings.applications.UsageAccessDetails;
import com.android.settings.applications.WriteSettingsDetails;
import com.android.settings.core.InstrumentedPreferenceFragment;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.fuelgauge.HighPowerDetail;
import com.android.settings.fuelgauge.PowerWhitelistBackend;
import com.android.settings.notification.AppNotificationSettings;
import com.android.settings.notification.ConfigureNotificationSettings;
import com.android.settings.notification.NotificationBackend;
import com.android.settings.notification.NotificationBackend.AppRow;
import com.android.settings.widget.LoadingViewController;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.AppFilter;
import com.android.settingslib.applications.ApplicationsState.CompoundFilter;
import com.android.settingslib.applications.ApplicationsState.VolumeFilter;
import com.android.settingslib.applications.StorageStatsSource;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.wrapper.PackageManagerWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

/**
 * Activity to pick an application that will be used to display installation information and
 * options to uninstall/delete user data for system applications. This activity
 * can be launched through Settings or via the ACTION_MANAGE_PACKAGE_STORAGE
 * intent.
 */
public class ManageApplications extends InstrumentedPreferenceFragment
        implements View.OnClickListener, OnItemSelectedListener {

    static final String TAG = "ManageApplications";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // Intent extras.
    public static final String EXTRA_CLASSNAME = "classname";
    // Used for storage only.
    public static final String EXTRA_VOLUME_UUID = "volumeUuid";
    public static final String EXTRA_VOLUME_NAME = "volumeName";
    public static final String EXTRA_STORAGE_TYPE = "storageType";
    public static final String EXTRA_WORK_ONLY = "workProfileOnly";
    public static final String EXTRA_WORK_ID = "workId";

    private static final String EXTRA_SORT_ORDER = "sortOrder";
    private static final String EXTRA_SHOW_SYSTEM = "showSystem";
    private static final String EXTRA_HAS_ENTRIES = "hasEntries";
    private static final String EXTRA_HAS_BRIDGE = "hasBridge";

    // attributes used as keys when passing values to InstalledAppDetails activity
    public static final String APP_CHG = "chg";

    // constant value that can be used to check return code from sub activity.
    private static final int INSTALLED_APP_DETAILS = 1;
    private static final int ADVANCED_SETTINGS = 2;

    public static final int SIZE_TOTAL = 0;
    public static final int SIZE_INTERNAL = 1;
    public static final int SIZE_EXTERNAL = 2;

    // Storage types. Used to determine what the extra item in the list of preferences is.
    public static final int STORAGE_TYPE_DEFAULT = 0; // Show all apps that are not categorized.
    public static final int STORAGE_TYPE_MUSIC = 1;
    public static final int STORAGE_TYPE_LEGACY = 2; // Show apps even if they can be categorized.
    public static final int STORAGE_TYPE_PHOTOS_VIDEOS = 3;

    private static final int NO_USER_SPECIFIED = -1;

    // sort order
    private int mSortOrder = R.id.sort_order_alpha;

    // whether showing system apps.
    private boolean mShowSystem;

    private ApplicationsState mApplicationsState;

    public int mListType;
    private AppFilterItem mFilter;
    private ApplicationsAdapter mApplications;

    private View mLoadingContainer;

    private View mListContainer;
    private RecyclerView mRecyclerView;

    // Size resource used for packages whose size computation failed for some reason
    CharSequence mInvalidSizeStr;

    private String mCurrentPkgName;
    private int mCurrentUid;

    private Menu mOptionsMenu;

    public static final int LIST_TYPE_MAIN = 0;
    public static final int LIST_TYPE_NOTIFICATION = 1;
    public static final int LIST_TYPE_STORAGE = 3;
    public static final int LIST_TYPE_USAGE_ACCESS = 4;
    public static final int LIST_TYPE_HIGH_POWER = 5;
    public static final int LIST_TYPE_OVERLAY = 6;
    public static final int LIST_TYPE_WRITE_SETTINGS = 7;
    public static final int LIST_TYPE_MANAGE_SOURCES = 8;
    public static final int LIST_TYPE_GAMES = 9;
    public static final int LIST_TYPE_MOVIES = 10;
    public static final int LIST_TYPE_PHOTOGRAPHY = 11;

    // List types that should show instant apps.
    public static final Set<Integer> LIST_TYPES_WITH_INSTANT = new ArraySet<>(Arrays.asList(
            LIST_TYPE_MAIN,
            LIST_TYPE_STORAGE));

    private View mRootView;
    private View mSpinnerHeader;
    private Spinner mFilterSpinner;
    private FilterSpinnerAdapter mFilterAdapter;
    private NotificationBackend mNotifBackend;
    private ResetAppsHelper mResetAppsHelper;
    private String mVolumeUuid;
    private int mStorageType;
    private boolean mIsWorkOnly;
    private int mWorkUserId;
    private View mEmptyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final Activity activity = getActivity();
        mApplicationsState = ApplicationsState.getInstance(activity.getApplication());

        Intent intent = activity.getIntent();
        Bundle args = getArguments();
        int screenTitle = intent.getIntExtra(
                SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE_RESID, R.string.notifications_label);
        String className = args != null ? args.getString(EXTRA_CLASSNAME) : null;
        if (className == null) {
            className = intent.getComponent().getClassName();
        }
        if (className.equals(NotificationAppListActivity.class.getName())
                || this instanceof NotificationApps) {
            mListType = LIST_TYPE_NOTIFICATION;
            mNotifBackend = new NotificationBackend();
            screenTitle = R.string.app_notifications_title;
        } else if (className.equals(StorageUseActivity.class.getName())) {
            if (args != null && args.containsKey(EXTRA_VOLUME_UUID)) {
                mVolumeUuid = args.getString(EXTRA_VOLUME_UUID);
                mStorageType = args.getInt(EXTRA_STORAGE_TYPE, STORAGE_TYPE_DEFAULT);
                mListType = LIST_TYPE_STORAGE;
            } else {
                // No volume selected, display a normal list, sorted by size.
                mListType = LIST_TYPE_MAIN;
            }
            mSortOrder = R.id.sort_order_size;
        } else if (className.equals(UsageAccessSettingsActivity.class.getName())) {
            mListType = LIST_TYPE_USAGE_ACCESS;
            screenTitle = R.string.usage_access;
        } else if (className.equals(HighPowerApplicationsActivity.class.getName())) {
            mListType = LIST_TYPE_HIGH_POWER;
            // Default to showing system.
            mShowSystem = true;
            screenTitle = R.string.high_power_apps;
        } else if (className.equals(OverlaySettingsActivity.class.getName())) {
            mListType = LIST_TYPE_OVERLAY;
            screenTitle = R.string.system_alert_window_settings;
        } else if (className.equals(WriteSettingsActivity.class.getName())) {
            mListType = LIST_TYPE_WRITE_SETTINGS;
            screenTitle = R.string.write_settings;
        } else if (className.equals(ManageExternalSourcesActivity.class.getName())) {
            mListType = LIST_TYPE_MANAGE_SOURCES;
            screenTitle = R.string.install_other_apps;
        } else if (className.equals(GamesStorageActivity.class.getName())) {
            mListType = LIST_TYPE_GAMES;
            mSortOrder = R.id.sort_order_size;
        } else if (className.equals(MoviesStorageActivity.class.getName())) {
            mListType = LIST_TYPE_MOVIES;
            mSortOrder = R.id.sort_order_size;
        } else if (className.equals(Settings.PhotosStorageActivity.class.getName())) {
            mListType = LIST_TYPE_PHOTOGRAPHY;
            mSortOrder = R.id.sort_order_size;
            mStorageType = args.getInt(EXTRA_STORAGE_TYPE, STORAGE_TYPE_DEFAULT);
        } else {
            mListType = LIST_TYPE_MAIN;
        }
        final AppFilterRegistry appFilterRegistry = AppFilterRegistry.getInstance();
        mFilter = appFilterRegistry.get(appFilterRegistry.getDefaultFilterType(mListType));
        mIsWorkOnly = args != null ? args.getBoolean(EXTRA_WORK_ONLY) : false;
        mWorkUserId = args != null ? args.getInt(EXTRA_WORK_ID) : NO_USER_SPECIFIED;

        if (savedInstanceState != null) {
            mSortOrder = savedInstanceState.getInt(EXTRA_SORT_ORDER, mSortOrder);
            mShowSystem = savedInstanceState.getBoolean(EXTRA_SHOW_SYSTEM, mShowSystem);
        }

        mInvalidSizeStr = activity.getText(R.string.invalid_size_value);

        mResetAppsHelper = new ResetAppsHelper(activity);

        if (usePreferenceScreenTitle() && screenTitle > 0) {
            activity.setTitle(screenTitle);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.manage_applications_apps, null);
        mLoadingContainer = mRootView.findViewById(R.id.loading_container);
        mListContainer = mRootView.findViewById(R.id.list_container);
        if (mListContainer != null) {
            // Create adapter and list view here
            mEmptyView = mListContainer.findViewById(android.R.id.empty);
            mApplications = new ApplicationsAdapter(mApplicationsState, this, mFilter,
                    savedInstanceState);
            if (savedInstanceState != null) {
                mApplications.mHasReceivedLoadEntries =
                        savedInstanceState.getBoolean(EXTRA_HAS_ENTRIES, false);
                mApplications.mHasReceivedBridgeCallback =
                        savedInstanceState.getBoolean(EXTRA_HAS_BRIDGE, false);
            }
            int userId = mIsWorkOnly ? mWorkUserId : UserHandle.getUserId(mCurrentUid);
            if (mStorageType == STORAGE_TYPE_MUSIC) {
                Context context = getContext();
                mApplications.setExtraViewController(
                        new MusicViewHolderController(
                                context,
                                new StorageStatsSource(context),
                                mVolumeUuid,
                                UserHandle.of(userId)));
            } else if (mStorageType == STORAGE_TYPE_PHOTOS_VIDEOS) {
                Context context = getContext();
                mApplications.setExtraViewController(
                        new PhotosViewHolderController(
                                context,
                                new StorageStatsSource(context),
                                mVolumeUuid,
                                UserHandle.of(userId)));
            }
            mRecyclerView = mListContainer.findViewById(R.id.apps_list);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(
                    getContext(), RecyclerView.VERTICAL, false /* reverseLayout */));
            mRecyclerView.setAdapter(mApplications);
        }

        // We have to do this now because PreferenceFrameLayout looks at it
        // only when the view is added.
        if (container instanceof PreferenceFrameLayout) {
            ((PreferenceFrameLayout.LayoutParams) mRootView.getLayoutParams()).removeBorders = true;
        }

        createHeader();

        mResetAppsHelper.onRestoreInstanceState(savedInstanceState);

        return mRootView;
    }

    @VisibleForTesting
    void createHeader() {
        final Activity activity = getActivity();
        final FrameLayout pinnedHeader = mRootView.findViewById(R.id.pinned_header);
        mSpinnerHeader = activity.getLayoutInflater()
                .inflate(R.layout.apps_filter_spinner, pinnedHeader, false);
        mFilterSpinner = mSpinnerHeader.findViewById(R.id.filter_spinner);
        mFilterAdapter = new FilterSpinnerAdapter(this);
        mFilterSpinner.setAdapter(mFilterAdapter);
        mFilterSpinner.setOnItemSelectedListener(this);
        pinnedHeader.addView(mSpinnerHeader, 0);

        final AppFilterRegistry appFilterRegistry = AppFilterRegistry.getInstance();
        mFilterAdapter.enableFilter(appFilterRegistry.getDefaultFilterType(mListType));
        if (mListType == LIST_TYPE_MAIN) {
            if (UserManager.get(getActivity()).getUserProfiles().size() > 1) {
                mFilterAdapter.enableFilter(FILTER_APPS_PERSONAL);
                mFilterAdapter.enableFilter(FILTER_APPS_WORK);
            }
        }
        if (mListType == LIST_TYPE_NOTIFICATION) {
            mFilterAdapter.enableFilter(FILTER_APPS_BLOCKED);
        }
        if (mListType == LIST_TYPE_HIGH_POWER) {
            mFilterAdapter.enableFilter(FILTER_APPS_POWER_WHITELIST_ALL);
        }

        AppFilter compositeFilter = getCompositeFilter(mListType, mStorageType, mVolumeUuid);
        if (mIsWorkOnly) {
            final AppFilter workFilter = appFilterRegistry.get(FILTER_APPS_WORK).getFilter();
            compositeFilter = new CompoundFilter(compositeFilter, workFilter);
        }
        if (compositeFilter != null) {
            mApplications.setCompositeFilter(compositeFilter);
        }
    }

    @VisibleForTesting
    @Nullable
    static AppFilter getCompositeFilter(int listType, int storageType, String volumeUuid) {
        AppFilter filter = new VolumeFilter(volumeUuid);
        if (listType == LIST_TYPE_STORAGE) {
            if (storageType == STORAGE_TYPE_MUSIC) {
                filter = new CompoundFilter(ApplicationsState.FILTER_AUDIO, filter);
            } else if (storageType == STORAGE_TYPE_DEFAULT) {
                filter = new CompoundFilter(ApplicationsState.FILTER_OTHER_APPS, filter);
            }
            return filter;
        }
        if (listType == LIST_TYPE_GAMES) {
            return new CompoundFilter(ApplicationsState.FILTER_GAMES, filter);
        } else if (listType == LIST_TYPE_MOVIES) {
            return new CompoundFilter(ApplicationsState.FILTER_MOVIES, filter);
        } else if (listType == LIST_TYPE_PHOTOGRAPHY) {
            return new CompoundFilter(ApplicationsState.FILTER_PHOTOS, filter);
        }

        return null;
    }

    @Override
    public int getMetricsCategory() {
        switch (mListType) {
            case LIST_TYPE_MAIN:
                return MetricsEvent.MANAGE_APPLICATIONS;
            case LIST_TYPE_NOTIFICATION:
                return MetricsEvent.MANAGE_APPLICATIONS_NOTIFICATIONS;
            case LIST_TYPE_STORAGE:
                if (mStorageType == STORAGE_TYPE_MUSIC) {
                    return MetricsEvent.APPLICATIONS_STORAGE_MUSIC;
                }
                return MetricsEvent.APPLICATIONS_STORAGE_APPS;
            case LIST_TYPE_GAMES:
                return MetricsEvent.APPLICATIONS_STORAGE_GAMES;
            case LIST_TYPE_MOVIES:
                return MetricsEvent.APPLICATIONS_STORAGE_MOVIES;
            case LIST_TYPE_PHOTOGRAPHY:
                return MetricsEvent.APPLICATIONS_STORAGE_PHOTOS;
            case LIST_TYPE_USAGE_ACCESS:
                return MetricsEvent.USAGE_ACCESS;
            case LIST_TYPE_HIGH_POWER:
                return MetricsEvent.APPLICATIONS_HIGH_POWER_APPS;
            case LIST_TYPE_OVERLAY:
                return MetricsEvent.SYSTEM_ALERT_WINDOW_APPS;
            case LIST_TYPE_WRITE_SETTINGS:
                return MetricsEvent.SYSTEM_ALERT_WINDOW_APPS;
            case LIST_TYPE_MANAGE_SOURCES:
                return MetricsEvent.MANAGE_EXTERNAL_SOURCES;
            default:
                return MetricsEvent.VIEW_UNKNOWN;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        updateView();
        if (mApplications != null) {
            mApplications.resume(mSortOrder);
            mApplications.updateLoading();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mResetAppsHelper.onSaveInstanceState(outState);
        outState.putInt(EXTRA_SORT_ORDER, mSortOrder);
        outState.putBoolean(EXTRA_SHOW_SYSTEM, mShowSystem);
        outState.putBoolean(EXTRA_HAS_ENTRIES, mApplications.mHasReceivedLoadEntries);
        outState.putBoolean(EXTRA_HAS_BRIDGE, mApplications.mHasReceivedBridgeCallback);
        if (mApplications != null) {
            mApplications.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mApplications != null) {
            mApplications.pause();
        }
        mResetAppsHelper.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mApplications != null) {
            mApplications.release();
        }
        mRootView = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INSTALLED_APP_DETAILS && mCurrentPkgName != null) {
            if (mListType == LIST_TYPE_NOTIFICATION) {
                mApplications.mExtraInfoBridge.forceUpdate(mCurrentPkgName, mCurrentUid);
            } else if (mListType == LIST_TYPE_HIGH_POWER || mListType == LIST_TYPE_OVERLAY
                    || mListType == LIST_TYPE_WRITE_SETTINGS) {
                mApplications.mExtraInfoBridge.forceUpdate(mCurrentPkgName, mCurrentUid);
            } else {
                mApplicationsState.requestSize(mCurrentPkgName, UserHandle.getUserId(mCurrentUid));
            }
        }
    }

    // utility method used to start sub activity
    private void startApplicationDetailsActivity() {
        switch (mListType) {
            case LIST_TYPE_NOTIFICATION:
                startAppInfoFragment(AppNotificationSettings.class, R.string.notifications_title);
                break;
            case LIST_TYPE_USAGE_ACCESS:
                startAppInfoFragment(UsageAccessDetails.class, R.string.usage_access);
                break;
            case LIST_TYPE_STORAGE:
                startAppInfoFragment(AppStorageSettings.class, R.string.storage_settings);
                break;
            case LIST_TYPE_HIGH_POWER:
                HighPowerDetail.show(this, mCurrentPkgName, INSTALLED_APP_DETAILS);
                break;
            case LIST_TYPE_OVERLAY:
                startAppInfoFragment(DrawOverlayDetails.class, R.string.overlay_settings);
                break;
            case LIST_TYPE_WRITE_SETTINGS:
                startAppInfoFragment(WriteSettingsDetails.class, R.string.write_system_settings);
                break;
            case LIST_TYPE_MANAGE_SOURCES:
                startAppInfoFragment(ExternalSourcesDetails.class, R.string.install_other_apps);
                break;
            case LIST_TYPE_GAMES:
                startAppInfoFragment(AppStorageSettings.class, R.string.game_storage_settings);
                break;
            case LIST_TYPE_MOVIES:
                startAppInfoFragment(AppStorageSettings.class, R.string.storage_movies_tv);
                break;
            case LIST_TYPE_PHOTOGRAPHY:
                startAppInfoFragment(AppStorageSettings.class, R.string.storage_photos_videos);
                break;
            // TODO: Figure out if there is a way where we can spin up the profile's settings
            // process ahead of time, to avoid a long load of data when user clicks on a managed
            // app. Maybe when they load the list of apps that contains managed profile apps.
            default:
                startAppInfoFragment(InstalledAppDetails.class, R.string.application_info_label);
                break;
        }
    }

    private void startAppInfoFragment(Class<?> fragment, int titleRes) {
        AppInfoBase.startAppInfoFragment(fragment, titleRes, mCurrentPkgName, mCurrentUid, this,
                INSTALLED_APP_DETAILS, getMetricsCategory());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        HelpUtils.prepareHelpMenuItem(activity, menu, getHelpResource(), getClass().getName());
        mOptionsMenu = menu;
        inflater.inflate(R.menu.manage_apps, menu);
        updateOptionsMenu();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        updateOptionsMenu();
    }

    @Override
    public void onDestroyOptionsMenu() {
        mOptionsMenu = null;
    }

    @StringRes
    int getHelpResource() {
        if (mListType == LIST_TYPE_MAIN) {
            return R.string.help_uri_apps;
        } else if (mListType == LIST_TYPE_USAGE_ACCESS) {
            return R.string.help_url_usage_access;
        } else {
            return R.string.help_uri_notifications;
        }
    }

    void updateOptionsMenu() {
        if (mOptionsMenu == null) {
            return;
        }
        mOptionsMenu.findItem(R.id.advanced).setVisible(false);

        mOptionsMenu.findItem(R.id.sort_order_alpha).setVisible(mListType == LIST_TYPE_STORAGE
                && mSortOrder != R.id.sort_order_alpha);
        mOptionsMenu.findItem(R.id.sort_order_size).setVisible(mListType == LIST_TYPE_STORAGE
                && mSortOrder != R.id.sort_order_size);

        mOptionsMenu.findItem(R.id.show_system).setVisible(!mShowSystem
                && mListType != LIST_TYPE_HIGH_POWER);
        mOptionsMenu.findItem(R.id.hide_system).setVisible(mShowSystem
                && mListType != LIST_TYPE_HIGH_POWER);

        mOptionsMenu.findItem(R.id.reset_app_preferences).setVisible(mListType == LIST_TYPE_MAIN);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuId = item.getItemId();
        switch (item.getItemId()) {
            case R.id.sort_order_alpha:
            case R.id.sort_order_size:
                mSortOrder = menuId;
                if (mApplications != null) {
                    mApplications.rebuild(mSortOrder);
                }
                break;
            case R.id.show_system:
            case R.id.hide_system:
                mShowSystem = !mShowSystem;
                mApplications.rebuild();
                break;
            case R.id.reset_app_preferences:
                mResetAppsHelper.buildResetDialog();
                return true;
            case R.id.advanced:
                if (mListType == LIST_TYPE_NOTIFICATION) {
                    ((SettingsActivity) getActivity()).startPreferencePanel(this,
                            ConfigureNotificationSettings.class.getName(), null,
                            R.string.configure_notification_settings, null, this,
                            ADVANCED_SETTINGS);
                } else {
                    ((SettingsActivity) getActivity()).startPreferencePanel(this,
                            DefaultAppSettings.class.getName(), null, R.string.configure_apps,
                            null, this, ADVANCED_SETTINGS);
                }
                return true;
            default:
                // Handle the home button
                return false;
        }
        updateOptionsMenu();
        return true;
    }

    @Override
    public void onClick(View view) {
        if (mApplications == null) {
            return;
        }
        final int position = mRecyclerView.getChildAdapterPosition(view);

        if (position == RecyclerView.NO_POSITION) {
            Log.w(TAG, "Cannot find position for child, skipping onClick handling");
            return;
        }
        if (mApplications.getApplicationCount() > position) {
            ApplicationsState.AppEntry entry = mApplications.getAppEntry(position);
            mCurrentPkgName = entry.info.packageName;
            mCurrentUid = entry.info.uid;
            startApplicationDetailsActivity();
        } else {
            mApplications.mExtraViewController.onClick(this);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mFilter = mFilterAdapter.getFilter(position);
        mApplications.setFilter(mFilter);
        if (DEBUG) Log.d(TAG, "Selecting filter " + mFilter);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void updateView() {
        updateOptionsMenu();
        final Activity host = getActivity();
        if (host != null) {
            host.invalidateOptionsMenu();
        }
    }

    public void setHasDisabled(boolean hasDisabledApps) {
        if (mListType != LIST_TYPE_MAIN) {
            return;
        }
        mFilterAdapter.setFilterEnabled(FILTER_APPS_ENABLED, hasDisabledApps);
        mFilterAdapter.setFilterEnabled(FILTER_APPS_DISABLED, hasDisabledApps);
    }

    public void setHasInstant(boolean haveInstantApps) {
        if (LIST_TYPES_WITH_INSTANT.contains(mListType)) {
            mFilterAdapter.setFilterEnabled(FILTER_APPS_INSTANT, haveInstantApps);
        }
    }

    static class FilterSpinnerAdapter extends ArrayAdapter<CharSequence> {

        private final ManageApplications mManageApplications;
        private final Context mContext;

        // Use ArrayAdapter for view logic, but have our own list for managing
        // the options available.
        private final ArrayList<AppFilterItem> mFilterOptions = new ArrayList<>();

        public FilterSpinnerAdapter(ManageApplications manageApplications) {
            super(manageApplications.getContext(), R.layout.filter_spinner_item);
            mContext = manageApplications.getContext();
            mManageApplications = manageApplications;
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        public AppFilterItem getFilter(int position) {
            return mFilterOptions.get(position);
        }

        public void setFilterEnabled(@AppFilterRegistry.FilterType int filter, boolean enabled) {
            if (enabled) {
                enableFilter(filter);
            } else {
                disableFilter(filter);
            }
        }

        public void enableFilter(@AppFilterRegistry.FilterType int filterType) {
            final AppFilterItem filter = AppFilterRegistry.getInstance().get(filterType);
            if (mFilterOptions.contains(filter)) {
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "Enabling filter " + filter);
            }
            mFilterOptions.add(filter);
            Collections.sort(mFilterOptions);
            mManageApplications.mSpinnerHeader.setVisibility(
                    mFilterOptions.size() > 1 ? View.VISIBLE : View.GONE);
            notifyDataSetChanged();
            if (mFilterOptions.size() == 1) {
                if (DEBUG) {
                    Log.d(TAG, "Auto selecting filter " + filter);
                }
                mManageApplications.mFilterSpinner.setSelection(0);
                mManageApplications.onItemSelected(null, null, 0, 0);
            }
        }

        public void disableFilter(@AppFilterRegistry.FilterType int filterType) {
            final AppFilterItem filter = AppFilterRegistry.getInstance().get(filterType);
            if (!mFilterOptions.remove(filter)) {
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "Disabling filter " + filter);
            }
            Collections.sort(mFilterOptions);
            mManageApplications.mSpinnerHeader.setVisibility(
                    mFilterOptions.size() > 1 ? View.VISIBLE : View.GONE);
            notifyDataSetChanged();
            if (mManageApplications.mFilter == filter) {
                if (mFilterOptions.size() > 0) {
                    if (DEBUG) {
                        Log.d(TAG, "Auto selecting filter " + mFilterOptions.get(0));
                    }
                    mManageApplications.mFilterSpinner.setSelection(0);
                    mManageApplications.onItemSelected(null, null, 0, 0);
                }
            }
        }

        @Override
        public int getCount() {
            return mFilterOptions.size();
        }

        @Override
        public CharSequence getItem(int position) {
            return mContext.getText(mFilterOptions.get(position).getTitle());
        }
    }

    static class ApplicationsAdapter extends RecyclerView.Adapter<ApplicationViewHolder>
            implements ApplicationsState.Callbacks, AppStateBaseBridge.Callback {

        private static final String STATE_LAST_SCROLL_INDEX = "state_last_scroll_index";
        private static final int VIEW_TYPE_APP = 0;
        private static final int VIEW_TYPE_EXTRA_VIEW = 1;

        private final ApplicationsState mState;
        private final ApplicationsState.Session mSession;
        private final ManageApplications mManageApplications;
        private final Context mContext;
        private final AppStateBaseBridge mExtraInfoBridge;
        private final LoadingViewController mLoadingViewController;

        private AppFilterItem mAppFilter;
        private ArrayList<ApplicationsState.AppEntry> mEntries;
        private boolean mResumed;
        private int mLastSortMode = -1;
        private int mWhichSize = SIZE_TOTAL;
        private AppFilter mCompositeFilter;
        private boolean mHasReceivedLoadEntries;
        private boolean mHasReceivedBridgeCallback;
        private FileViewHolderController mExtraViewController;

        // These two variables are used to remember and restore the last scroll position when this
        // fragment is paused. We need this special handling because app entries are added gradually
        // when we rebuild the list after the user made some changes, like uninstalling an app.
        private int mLastIndex = -1;

        public ApplicationsAdapter(ApplicationsState state, ManageApplications manageApplications,
                AppFilterItem appFilter, Bundle savedInstanceState) {
            setHasStableIds(true);
            mState = state;
            mSession = state.newSession(this);
            mManageApplications = manageApplications;
            mLoadingViewController = new LoadingViewController(
                    mManageApplications.mLoadingContainer,
                    mManageApplications.mListContainer
            );
            mContext = manageApplications.getActivity();
            mAppFilter = appFilter;
            if (mManageApplications.mListType == LIST_TYPE_NOTIFICATION) {
                mExtraInfoBridge = new AppStateNotificationBridge(mContext, mState, this,
                        manageApplications.mNotifBackend);
            } else if (mManageApplications.mListType == LIST_TYPE_USAGE_ACCESS) {
                mExtraInfoBridge = new AppStateUsageBridge(mContext, mState, this);
            } else if (mManageApplications.mListType == LIST_TYPE_HIGH_POWER) {
                mExtraInfoBridge = new AppStatePowerBridge(mState, this);
            } else if (mManageApplications.mListType == LIST_TYPE_OVERLAY) {
                mExtraInfoBridge = new AppStateOverlayBridge(mContext, mState, this);
            } else if (mManageApplications.mListType == LIST_TYPE_WRITE_SETTINGS) {
                mExtraInfoBridge = new AppStateWriteSettingsBridge(mContext, mState, this);
            } else if (mManageApplications.mListType == LIST_TYPE_MANAGE_SOURCES) {
                mExtraInfoBridge = new AppStateInstallAppsBridge(mContext, mState, this);
            } else {
                mExtraInfoBridge = null;
            }
            if (savedInstanceState != null) {
                mLastIndex = savedInstanceState.getInt(STATE_LAST_SCROLL_INDEX);
            }
        }

        public void setCompositeFilter(AppFilter compositeFilter) {
            mCompositeFilter = compositeFilter;
            rebuild();
        }

        public void setFilter(AppFilterItem appFilter) {
            mAppFilter = appFilter;
            rebuild();
        }

        public void setExtraViewController(FileViewHolderController extraViewController) {
            mExtraViewController = extraViewController;
            // Start to query extra view's stats on background, and once done post result to main
            // thread.
            ThreadUtils.postOnBackgroundThread(() -> {
                mExtraViewController.queryStats();
                ThreadUtils.postOnMainThread(() -> {
                    onExtraViewCompleted();
                });
            });
        }

        public void resume(int sort) {
            if (DEBUG) Log.i(TAG, "Resume!  mResumed=" + mResumed);
            if (!mResumed) {
                mResumed = true;
                mSession.onResume();
                mLastSortMode = sort;
                if (mExtraInfoBridge != null) {
                    mExtraInfoBridge.resume();
                }
                rebuild();
            } else {
                rebuild(sort);
            }
        }

        public void pause() {
            if (mResumed) {
                mResumed = false;
                mSession.onPause();
                if (mExtraInfoBridge != null) {
                    mExtraInfoBridge.pause();
                }
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            // Record the current scroll position before pausing.
            final LinearLayoutManager layoutManager =
                    (LinearLayoutManager) mManageApplications.mRecyclerView.getLayoutManager();
            outState.putInt(STATE_LAST_SCROLL_INDEX, layoutManager.findFirstVisibleItemPosition());
        }

        public void release() {
            mSession.onDestroy();
            if (mExtraInfoBridge != null) {
                mExtraInfoBridge.release();
            }
        }

        public void rebuild(int sort) {
            if (sort == mLastSortMode) {
                return;
            }
            mLastSortMode = sort;
            rebuild();
        }

        @Override
        public ApplicationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view = ApplicationViewHolder.newView(parent);
            return new ApplicationViewHolder(view,
                    shouldUseStableItemHeight(mManageApplications.mListType));
        }

        @Override
        public int getItemViewType(int position) {
            boolean isLastItem = (getItemCount() - 1) == position;
            return hasExtraView() && isLastItem
                    ? VIEW_TYPE_EXTRA_VIEW
                    : VIEW_TYPE_APP;
        }

        public void rebuild() {
            if (!mHasReceivedLoadEntries
                    || (mExtraInfoBridge != null && !mHasReceivedBridgeCallback)) {
                // Don't rebuild the list until all the app entries are loaded.
                return;
            }
            ApplicationsState.AppFilter filterObj;
            Comparator<AppEntry> comparatorObj;
            boolean emulated = Environment.isExternalStorageEmulated();
            if (emulated) {
                mWhichSize = SIZE_TOTAL;
            } else {
                mWhichSize = SIZE_INTERNAL;
            }
            filterObj = mAppFilter.getFilter();
            if (mCompositeFilter != null) {
                filterObj = new CompoundFilter(filterObj, mCompositeFilter);
            }
            if (!mManageApplications.mShowSystem) {
                if (LIST_TYPES_WITH_INSTANT.contains(mManageApplications.mListType)) {
                    filterObj = new CompoundFilter(filterObj,
                            ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER_AND_INSTANT);
                } else {
                    filterObj = new CompoundFilter(filterObj,
                            ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER);
                }
            }
            switch (mLastSortMode) {
                case R.id.sort_order_size:
                    switch (mWhichSize) {
                        case SIZE_INTERNAL:
                            comparatorObj = ApplicationsState.INTERNAL_SIZE_COMPARATOR;
                            break;
                        case SIZE_EXTERNAL:
                            comparatorObj = ApplicationsState.EXTERNAL_SIZE_COMPARATOR;
                            break;
                        default:
                            comparatorObj = ApplicationsState.SIZE_COMPARATOR;
                            break;
                    }
                    break;
                default:
                    comparatorObj = ApplicationsState.ALPHA_COMPARATOR;
                    break;
            }

            filterObj = new CompoundFilter(filterObj, ApplicationsState.FILTER_NOT_HIDE);
            AppFilter finalFilterObj = filterObj;
            ThreadUtils.postOnBackgroundThread(() -> {
                final ArrayList<AppEntry> entries = mSession.rebuild(finalFilterObj,
                        comparatorObj, false);
                if (entries != null) {
                    ThreadUtils.postOnMainThread(() -> onRebuildComplete(entries));
                }
            });
        }

        @VisibleForTesting
        static boolean shouldUseStableItemHeight(int listType) {
            switch (listType) {
                case LIST_TYPE_NOTIFICATION:
                    // Most entries in notification type has no summary. Don't use stable height
                    // so height is short for most entries.
                    return false;
                default:
                    // Other types have non-empty summary, so keep the height as we expect summary
                    // to fill in.
                    return true;
            }
        }

        private static boolean packageNameEquals(PackageItemInfo info1, PackageItemInfo info2) {
            if (info1 == null || info2 == null) {
                return false;
            }
            if (info1.packageName == null || info2.packageName == null) {
                return false;
            }
            return info1.packageName.equals(info2.packageName);
        }

        private ArrayList<ApplicationsState.AppEntry> removeDuplicateIgnoringUser(
                ArrayList<ApplicationsState.AppEntry> entries) {
            int size = entries.size();
            // returnList will not have more entries than entries
            ArrayList<ApplicationsState.AppEntry> returnEntries = new ArrayList<>(size);

            // assume appinfo of same package but different users are grouped together
            PackageItemInfo lastInfo = null;
            for (int i = 0; i < size; i++) {
                AppEntry appEntry = entries.get(i);
                PackageItemInfo info = appEntry.info;
                if (!packageNameEquals(lastInfo, appEntry.info)) {
                    returnEntries.add(appEntry);
                }
                lastInfo = info;
            }
            returnEntries.trimToSize();
            return returnEntries;
        }

        @Override
        public void onRebuildComplete(ArrayList<AppEntry> entries) {
            final int filterType = mAppFilter.getFilterType();
            if (filterType == FILTER_APPS_POWER_WHITELIST ||
                    filterType == FILTER_APPS_POWER_WHITELIST_ALL) {
                entries = removeDuplicateIgnoringUser(entries);
            }
            mEntries = entries;
            notifyDataSetChanged();
            if (getItemCount() == 0) {
                mManageApplications.mRecyclerView.setVisibility(View.GONE);
                mManageApplications.mEmptyView.setVisibility(View.VISIBLE);
            } else {
                mManageApplications.mEmptyView.setVisibility(View.GONE);
                mManageApplications.mRecyclerView.setVisibility(View.VISIBLE);
            }
            // Restore the last scroll position if the number of entries added so far is bigger than
            // it.
            if (mLastIndex != -1 && getItemCount() > mLastIndex) {
                mManageApplications.mRecyclerView.getLayoutManager().scrollToPosition(mLastIndex);
                mLastIndex = -1;
            }

            if (mSession.getAllApps().size() != 0
                    && mManageApplications.mListContainer.getVisibility() != View.VISIBLE) {
                mLoadingViewController.showContent(true /* animate */);
            }
            if (mManageApplications.mListType == LIST_TYPE_USAGE_ACCESS) {
                // No enabled or disabled filters for usage access.
                return;
            }

            mManageApplications.setHasDisabled(mState.haveDisabledApps());
            mManageApplications.setHasInstant(mState.haveInstantApps());
        }

        @VisibleForTesting
        void updateLoading() {
            final boolean appLoaded = mHasReceivedLoadEntries && mSession.getAllApps().size() != 0;
            if (appLoaded) {
                mLoadingViewController.showContent(false /* animate */);
            } else {
                mLoadingViewController.showLoadingViewDelayed();
            }
        }

        @Override
        public void onExtraInfoUpdated() {
            mHasReceivedBridgeCallback = true;
            rebuild();
        }

        @Override
        public void onRunningStateChanged(boolean running) {
            mManageApplications.getActivity().setProgressBarIndeterminateVisibility(running);
        }

        @Override
        public void onPackageListChanged() {
            rebuild();
        }

        @Override
        public void onPackageIconChanged() {
            // We ensure icons are loaded when their item is displayed, so
            // don't care about icons loaded in the background.
        }

        @Override
        public void onLoadEntriesCompleted() {
            mHasReceivedLoadEntries = true;
            // We may have been skipping rebuilds until this came in, trigger one now.
            rebuild();
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            if (mEntries == null) {
                return;
            }
            final int size = mEntries.size();
            for (int i = 0; i < size; i++) {
                final AppEntry entry = mEntries.get(i);
                final ApplicationInfo info = entry.info;
                if (info == null && !TextUtils.equals(packageName, info.packageName)) {
                    continue;
                }
                if (TextUtils.equals(mManageApplications.mCurrentPkgName, info.packageName)) {
                    // We got the size information for the last app the
                    // user viewed, and are sorting by size...  they may
                    // have cleared data, so we immediately want to resort
                    // the list with the new size to reflect it to the user.
                    rebuild();
                    return;
                } else {
                    notifyItemChanged(i);
                }

            }
        }

        @Override
        public void onLauncherInfoChanged() {
            if (!mManageApplications.mShowSystem) {
                rebuild();
            }
        }

        @Override
        public void onAllSizesComputed() {
            if (mLastSortMode == R.id.sort_order_size) {
                rebuild();
            }
        }

        public void onExtraViewCompleted() {
            if (!hasExtraView()) {
                return;
            }
            // Update last item - this is assumed to be the extra view.
            notifyItemChanged(getItemCount() - 1);
        }

        @Override
        public int getItemCount() {
            if (mEntries == null) {
                return 0;
            }
            return mEntries.size() + (hasExtraView() ? 1 : 0);
        }

        public int getApplicationCount() {
            return mEntries != null ? mEntries.size() : 0;
        }

        public AppEntry getAppEntry(int position) {
            return mEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            if (position == mEntries.size()) {
                return -1;
            }
            return mEntries.get(position).id;
        }

        public boolean isEnabled(int position) {
            if (getItemViewType(position) == VIEW_TYPE_EXTRA_VIEW
                    || mManageApplications.mListType != LIST_TYPE_HIGH_POWER) {
                return true;
            }
            ApplicationsState.AppEntry entry = mEntries.get(position);
            return !PowerWhitelistBackend.getInstance().isSysWhitelisted(entry.info.packageName);
        }

        @Override
        public void onBindViewHolder(ApplicationViewHolder holder, int position) {
            if (mEntries != null && mExtraViewController != null && position == mEntries.size()) {
                // set up view for extra view controller
                mExtraViewController.setupView(holder);
            } else {
                // Bind the data efficiently with the holder
                ApplicationsState.AppEntry entry = mEntries.get(position);
                synchronized (entry) {
                    holder.setTitle(entry.label);
                    mState.ensureIcon(entry);
                    holder.setIcon(entry.icon);
                    updateSummary(holder, entry);
                    holder.updateDisableView(entry.info);
                }
                holder.setEnabled(isEnabled(position));
            }
            holder.itemView.setOnClickListener(mManageApplications);
        }

        private void updateSummary(ApplicationViewHolder holder, AppEntry entry) {
            switch (mManageApplications.mListType) {
                case LIST_TYPE_NOTIFICATION:
                    if (entry.extraInfo != null) {
                        holder.setSummary(InstalledAppDetails.getNotificationSummary(
                                (AppRow) entry.extraInfo, mContext));
                    } else {
                        holder.setSummary(null);
                    }
                    break;
                case LIST_TYPE_USAGE_ACCESS:
                    if (entry.extraInfo != null) {
                        holder.setSummary(
                                (new UsageState((PermissionState) entry.extraInfo)).isPermissible()
                                        ? R.string.app_permission_summary_allowed
                                        : R.string.app_permission_summary_not_allowed);
                    } else {
                        holder.setSummary(null);
                    }
                    break;
                case LIST_TYPE_HIGH_POWER:
                    holder.setSummary(HighPowerDetail.getSummary(mContext, entry));
                    break;
                case LIST_TYPE_OVERLAY:
                    holder.setSummary(DrawOverlayDetails.getSummary(mContext, entry));
                    break;
                case LIST_TYPE_WRITE_SETTINGS:
                    holder.setSummary(WriteSettingsDetails.getSummary(mContext, entry));
                    break;
                case LIST_TYPE_MANAGE_SOURCES:
                    holder.setSummary(ExternalSourcesDetails.getPreferenceSummary(mContext, entry));
                    break;
                default:
                    holder.updateSizeText(entry, mManageApplications.mInvalidSizeStr, mWhichSize);
                    break;
            }
        }

        private boolean hasExtraView() {
            return mExtraViewController != null
                    && mExtraViewController.shouldShow();
        }
    }

    private static class SummaryProvider implements SummaryLoader.SummaryProvider {

        private final Context mContext;
        private final SummaryLoader mLoader;
        private ApplicationsState.Session mSession;

        private SummaryProvider(Context context, SummaryLoader loader) {
            mContext = context;
            mLoader = loader;
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                new InstalledAppCounter(mContext, InstalledAppCounter.IGNORE_INSTALL_REASON,
                        new PackageManagerWrapper(mContext.getPackageManager())) {
                    @Override
                    protected void onCountComplete(int num) {
                        mLoader.setSummary(SummaryProvider.this,
                                mContext.getString(R.string.apps_summary, num));
                    }
                }.execute();
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                SummaryLoader summaryLoader) {
            return new SummaryProvider(activity, summaryLoader);
        }
    };
}