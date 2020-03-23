/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.ashomok.lullabies.ui.main_activity;

import android.app.ActivityOptions;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;

import com.ashomok.lullabies.R;
import com.ashomok.lullabies.Settings;
import com.ashomok.lullabies.ad.AdMobContainer;
import com.ashomok.lullabies.billing_kotlin.localdb.AugmentedSkuDetails;
import com.ashomok.lullabies.ui.BaseActivity;
import com.ashomok.lullabies.ui.ExitDialogFragment;
import com.ashomok.lullabies.ui.about_activity.AboutActivity;
import com.ashomok.lullabies.ui.full_screen_player_activity.FullScreenPlayerActivity;
import com.ashomok.lullabies.utils.InfoSnackbarUtil;
import com.ashomok.lullabies.utils.LogHelper;
import com.ashomok.lullabies.utils.rate_app.RateAppUtil;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.schedulers.Schedulers;

import static android.view.Menu.NONE;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class MusicPlayerActivity extends BaseActivity
        implements MediaBrowserFragment.MediaFragmentListener,
        MusicPlayerContract.View {

    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class);
    private static final String SAVED_MEDIA_ID = "com.ashomok.lullabies.MEDIA_ID";
    private static final String FRAGMENT_TAG = "lullabies_list_container";
    private static final String INIT_MEDIA_ID_VALUE_ROOT = "__BY_CATEGORY__";
    public static final String EXTRA_START_FULLSCREEN =
            "com.ashomok.lullabies.EXTRA_START_FULLSCREEN";

    /**
     * Optionally used with {@link #EXTRA_START_FULLSCREEN} to carry a MediaDescription to
     * the {@link FullScreenPlayerActivity}, speeding up the screen rendering
     * while the {@link android.support.v4.media.session.MediaControllerCompat} is connecting.
     */
    public static final String EXTRA_CURRENT_MEDIA_DESCRIPTION = "com.ashomok.lullabies.CURRENT_MEDIA_DESCRIPTION";

    private Bundle mVoiceSearchParams;

    @Inject
    MusicPlayerPresenter mPresenter;

    @Inject
    AdMobContainer adMobContainer;

    private View mRootView;

    private DrawerLayout mDrawerLayout;
    private NavigationView navigationView;
    private List<MediaBrowserCompat.MediaItem> categories;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.UAmpAppTheme);
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");
        setContentView(R.layout.activity_player);

        mRootView = findViewById(android.R.id.content);
        if (mRootView == null) {
            mRootView = getWindow().getDecorView().findViewById(android.R.id.content);
        }

        initializeToolbar();
        initializeNavigationDrawer();
        initializeFromParams(savedInstanceState, getIntent());

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(getIntent());
        }

        showBannerAd();
        mPresenter.takeView(this);

        mPresenter.initCategoriesList(INIT_MEDIA_ID_VALUE_ROOT, getMediaBrowser())
                .doOnSubscribe(disposable -> {
                    // Unsubscribing before subscribing is required if this mediaId already has a subscriber
                    // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
                    // the callback, but won't trigger the initial callback.onChildrenLoaded.
                    //
                    // This is temporary: A bug is being fixed that will make subscribe
                    // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
                    // subscriber or not. Currently this only happens if the mediaID has no previous
                    // subscriber or if the media content changes on the service side, so we need to
                    // unsubscribe first.
                    getMediaBrowser().unsubscribe(INIT_MEDIA_ID_VALUE_ROOT);
                })
                .subscribeOn(Schedulers.io())
                .subscribe(mediaItems -> {
                    browseCategory();
                }, throwable -> {
                    LogHelper.e(TAG, throwable, "Error from loading media");
                    checkForUserVisibleErrors(true);
                });
    }

    @Override
    protected void initializeToolbar() {
        super.initializeToolbar();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        }
    }

    private void initializeNavigationDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        // Set up the navigation drawer_actions.
        navigationView = findViewById(R.id.nav_view);
        navigationView.setItemIconTintList(null);
        setupDrawerContent();
    }

    private void setupDrawerContent() {

        navigationView.setNavigationItemSelectedListener(
                menuItem -> {
                    Bundle bundle = ActivityOptions.makeCustomAnimation(
                            this, R.anim.fade_in, R.anim.fade_out).toBundle();
                    String mediaId = null;
                    Class activityClass = null;

                    switch (menuItem.getItemId()) {
                        case R.id.navigation_rate_app:
                            rateApp();
                            break;
                        case R.id.navigation_about:
                            activityClass = AboutActivity.class;
                            break;
                        case R.id.navigation_exit:
                            exit();
                            break;
                        case 0:
                            activityClass = MusicPlayerActivity.class;
                            mediaId = "__BY_CATEGORY__/Base Collection"; //todo remove hardcoding
                            break;
                        case 1:
                            activityClass = MusicPlayerActivity.class;
                            mediaId = "__BY_CATEGORY__/Classic Tones"; //todo remove hardcoding
                            break;
                        default:
                            break;
                    }
                    if (activityClass != null) {
                        Intent intent = new Intent(this, activityClass);
                        if (mediaId != null) {
                            intent.putExtra(MusicPlayerActivity.SAVED_MEDIA_ID, mediaId);
                        }
                        startActivity(intent, bundle);
                    }
                    // Close the navigation drawer_actions when an item is selected.
                    menuItem.setChecked(true);
                    mDrawerLayout.closeDrawers();
                    return true;
                });
    }

    private void exit() {
        ExitDialogFragment exitDialogFragment = ExitDialogFragment.newInstance(
                R.string.exit_dialog_title);
        exitDialogFragment.show(getFragmentManager(), "dialog");
    }

    private void rateApp() {
        RateAppUtil rateAppUtil = new RateAppUtil();
        rateAppUtil.rate(this);
    }

    private void updateRateAppMenuItem(Menu navigationMenu) {
        MenuItem updateToPremiumMenuItem = navigationMenu.findItem(R.id.navigation_rate_app);
        CharSequence menuItemText = updateToPremiumMenuItem.getTitle();
        SpannableString spannableString = new SpannableString(menuItemText);
        spannableString.setSpan(
                new ForegroundColorSpan(getResources().getColor(R.color.orange_600)),
                0,
                spannableString.length(),
                0);
        updateToPremiumMenuItem.setTitle(spannableString);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Open the navigation drawer_actions when the home icon is selected from the toolbar.
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
            case R.id.remove_ads:
                mPresenter.proposeRemoveAds();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        String mediaId = getMediaId();
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId);
        }
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMediaItemSelected(MediaBrowserCompat.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item.isPlayable()) {
            MediaControllerCompat.getMediaController(MusicPlayerActivity.this).getTransportControls()
                    .playFromMediaId(item.getMediaId(), null);
        } else if (item.isBrowsable()) {
            navigateToBrowser(item.getMediaId());
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }

        //todo track events on special firebaseanalytics util
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, item.getMediaId());
        bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, item.getDescription().getTitle().toString());
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "MediaItem");
        getmFirebaseAnalytics().logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

    @Override
    public void setToolbarTitle(CharSequence title) {
        LogHelper.d(TAG, "Setting toolbar title to ", title);
        if (title == null) {
            title = getString(R.string.app_name);
        }
        setTitle(title);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogHelper.d(TAG, "onNewIntent, intent=" + intent);
        initializeFromParams(null, intent);
        startFullScreenActivityIfNeeded(intent);
        super.onNewIntent(intent);
    }

    private void startFullScreenActivityIfNeeded(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            MediaDescriptionCompat description =
                    intent.getParcelableExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION);

            Intent fullScreenIntent = new Intent(this, FullScreenPlayerActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION, description);
            startActivity(fullScreenIntent);
        }
    }

    protected void initializeFromParams(Bundle savedInstanceState, Intent intent) {

        String mediaId = INIT_MEDIA_ID_VALUE_ROOT;

        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.getAction() != null
                && intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mVoiceSearchParams = intent.getExtras();
            LogHelper.d(TAG, "Starting from voice search query=",
                    mVoiceSearchParams.getString(SearchManager.QUERY));
        } else {
            if (savedInstanceState != null) { //screen rotated
                // If there is a saved media ID, use it
                String savedMediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
                if (savedMediaId != null) {
                    mediaId = savedMediaId;
                    LogHelper.d(TAG,
                            "initializeFromParams with savedInstanceState, " +
                                    "mediaId = " + mediaId);

                }
            } else if (intent.getStringExtra(SAVED_MEDIA_ID) != null) { //called from menu
                mediaId = intent.getStringExtra(SAVED_MEDIA_ID);
            }
        }
        navigateToBrowser(mediaId);
    }

    private void navigateToBrowser(@NonNull String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId);

        if (!mediaId.equals(INIT_MEDIA_ID_VALUE_ROOT)) { //don't create fragment for categories
            MediaBrowserFragment fragment = getBrowseFragment();
            if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
                fragment = new MediaBrowserFragment();
                fragment.setMediaId(mediaId);
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setCustomAnimations(
                        R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                        R.animator.slide_in_from_left, R.animator.slide_out_to_right);
                transaction.replace(R.id.container, fragment, FRAGMENT_TAG);

                transaction.commit();
                LogHelper.d(TAG, "fragment with tag " + FRAGMENT_TAG + " commited");
            }
        }
    }

    private void checkForUserVisibleErrors(boolean b) {
        //todo
    }


    public @NonNull
    String getMediaId() {
        String mediaId = null;
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            String savedCategoryMediaId = getIntent().getStringExtra(SAVED_MEDIA_ID);
            if (savedCategoryMediaId != null) {
                mediaId = savedCategoryMediaId;
            }
        } else {
            mediaId = fragment.getMediaId(); //todo check category or track mediaId
        }
        if (mediaId == null) {
            mediaId = INIT_MEDIA_ID_VALUE_ROOT;
        }
        LogHelper.d(TAG, "getMediaId returned " + mediaId);
        return mediaId;
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onMediaControllerConnected() {
        LogHelper.d(TAG, "onMediaControllerConnected");
        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            String query = mVoiceSearchParams.getString(SearchManager.QUERY);
            MediaControllerCompat.getMediaController(MusicPlayerActivity.this).getTransportControls()
                    .playFromSearch(query, mVoiceSearchParams);
            mVoiceSearchParams = null;
        }
        getBrowseFragment().onConnected();
    }

    private void showBannerAd() {
        adMobContainer.initBottomBannerAd(findViewById(R.id.ads_container));
    }

    @Override
    public void showError(int errorMessageRes) {
        InfoSnackbarUtil.showError(errorMessageRes, mRootView);
    }

    @Override
    public void showInfo(int infoMessageRes) {
        InfoSnackbarUtil.showInfo(infoMessageRes, mRootView);
    }

    @Override
    public void showInfo(String message) {
        InfoSnackbarUtil.showInfo(message, mRootView);
    }


    @Override
    public void updateView(boolean isAdsActive) {
        adMobContainer.showAd(isAdsActive);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.remove_ads, menu);
        menu.findItem(R.id.remove_ads).setVisible(Settings.isAdsActive);
        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        Menu navigationMenu = navigationView.getMenu();
        updateRateAppMenuItem(navigationMenu);
        return true;
    }


    @Override
    public void showRemoveAdDialog(AugmentedSkuDetails removeAdsSkuRow) {
        RemoveAdDialogFragment removeAdDialogFragment =
                RemoveAdDialogFragment.newInstance(removeAdsSkuRow.getPrice());

        removeAdDialogFragment.show(getFragmentManager(), "dialog");
    }

    @Override
    public AppCompatActivity getActivity() {
        return this;
    }

    @Override
    public void addMenuItems(List<String> menuTitles) {
        if (navigationView == null) {
            LogHelper.e(TAG, "navigationView == null - unexpected");
        } else {
            for (int i = 0; i < menuTitles.size(); i++) {
                navigationView.getMenu().add(NONE, i, i, menuTitles.get(i));
            }
        }
    }

    @Override
    public void browseCategory() {
        String mMediaId = getMediaId();
        if (mMediaId.equals(INIT_MEDIA_ID_VALUE_ROOT)
                || mMediaId.equals(getMediaBrowser().getRoot())) {
            if (categories.size() > 0) {
                mMediaId = categories.get(0).getMediaId();
            }
        }
        //open item depends on intent extra
        navigateToBrowser(mMediaId);
        LogHelper.d(TAG, "category" + mMediaId + " loaded.");
    }

    @Override
    public void setCategories(List<MediaBrowserCompat.MediaItem> children) {
        categories = children; //todo
    }

    @Override
    public void onBackPressed() {
        LogHelper.d(TAG, "onBackPressed()" + getFragmentManager().getBackStackEntryCount());

        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment != null
                && fragment.getViewPager() != null
                && fragment.getViewPager().getCurrentItem() != 0) {
            ViewPager viewPager = fragment.getViewPager();
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
        } else {
            if (getFragmentManager().getBackStackEntryCount() > 0) {
                super.onBackPressed();
            } else {
                ExitDialogFragment exitDialogFragment = ExitDialogFragment.newInstance(
                        R.string.exit_dialog_title);

                exitDialogFragment.show(getFragmentManager(), "dialog");
            }
        }
    }


//    public String getMediaId() {
//        Bundle args = getArguments();
//        if (args != null) {
//            return args.getString(ARG_MEDIA_ID);
//        }
//        return null;
//    }
//
//    public void setMediaId(String mediaId) {
//        Bundle args = new Bundle(1);
//        args.putString(ARG_MEDIA_ID, mediaId);
//        setArguments(args);
//    }

}
