/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.Flags.enableExpandingPauseWorkButton;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_COUNT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE;
import static com.android.launcher3.views.RecyclerViewFastScroller.FastScrollerLocation.ALL_APPS_SCROLLER;
import static com.android.window.flags2.Flags.predictiveBackThreeButtonNav;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.AllAppsSearchUiDelegate;
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;
import com.android.systemui.plugins.AllAppsRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import static com.topjohnwu.superuser.internal.Utils.context;
import app.lawnchair.allapps.DrawerWallpaperBlurHelper;
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList;
import app.lawnchair.font.FontManager;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.color.tokens.ColorTokens;
import app.lawnchair.util.LawnchairUtilsKt;
import app.lawnchair.ui.StretchRecyclerViewContainer;

/**
 * All apps container view with search support for use in a dragging activity.
 *
 * @param <T> Type of context inflating all apps.
 */
public class ActivityAllAppsContainerView<T extends Context & ActivityContext>
        extends SpringRelativeLayout implements DragSource, Insettable,
        OnDeviceProfileChangeListener, PersonalWorkSlidingTabStrip.OnActivePageChangedListener,
        ScrimView.ScrimDrawingController {

    private static final String TAG = "ActivityAllAppsContainerView";
    public static final float PULL_MULTIPLIER = .02f;
    public static final float FLING_VELOCITY_MULTIPLIER = 1200f;
    protected static final String BUNDLE_KEY_CURRENT_PAGE = "launcher.allapps.current_page";
    private static final long DEFAULT_SEARCH_TRANSITION_DURATION_MS = 300;
    private static final boolean DEBUG_HEADER_PROTECTION = false;

    protected final T mActivityContext;
    protected final List<AdapterHolder> mAH;
    protected final Predicate<ItemInfo> mPersonalMatcher = ItemInfoMatcher.ofUser(
            Process.myUserHandle());
    protected WorkProfileManager mWorkManager;
    protected final PrivateProfileManager mPrivateProfileManager;
    protected final Point mFastScrollerOffset = new Point();
    protected int mScrimColor;
    protected final float mHeaderThreshold;
    protected final AllAppsSearchUiDelegate mSearchUiDelegate;

    private final SearchTransitionController mSearchTransitionController;
    private final Paint mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mInsets = new Rect();
    private final AllAppsStore<T> mAllAppsStore;
    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateHeaderScroll(recyclerView.computeVerticalScrollOffset());
                }
            };
    private final Paint mNavBarScrimPaint;
    private final int mHeaderProtectionColor;
    private final int mPrivateSpaceBottomExtraSpace;
    private final Path mTmpPath = new Path();
    private final RectF mTmpRectF = new RectF();
    protected AllAppsPagedView mViewPager;
    protected FloatingHeaderView mHeader;
    protected final List<AllAppsRow> mAdditionalHeaderRows = new ArrayList<>();
    protected View mBottomSheetBackground;
    protected RecyclerViewFastScroller mFastScroller;
    private ConstraintLayout mFastScrollLetterLayout;

    protected View mSearchContainer;
    protected SearchUiManager mSearchUiManager;
    protected boolean mUsingTabs;
    protected RecyclerViewFastScroller mTouchHandler;

    private boolean mIsSearching;
    boolean showFastScroller;
    private boolean mRebindAdaptersAfterSearchAnimation;
    private int mNavBarScrimHeight = 0;
    public SearchRecyclerView mSearchRecyclerView;
    protected SearchAdapterProvider<?> mMainAdapterProvider;
    private View mBottomSheetHandleArea;
    private View mBottomSheetHandle;
    private boolean mHasWorkApps;
    private boolean mHasPrivateApps;
    private float[] mBottomSheetCornerRadii;
    private ScrimView mScrimView;
    private int mHeaderColor;
    // LC-Note: System cross-window blur removed; only the legacy solid-colour path is used.
    private int mBottomSheetBackgroundColorLegacy;
    private int mTabsProtectionAlpha;
    @Nullable private AllAppsTransitionController mAllAppsTransitionController;

    private final PreferenceManager2 pref2;
    private final PreferenceManager pref;

    // LC-Note: Allapps cache colour
    private int mCachedBottomSheetBgColor;

    // -----------------------------------------------------------------------
    // HokoBlur drawer background (phone-only)
    //
    // Stored here and drawn in dispatchDraw() so it is rendered before all
    // child views, immune to setBackground() overrides and XML child backgrounds.
    // -----------------------------------------------------------------------
    @Nullable private Bitmap mBlurBitmap;

    public ActivityAllAppsContainerView(Context context) {
        this(context, null);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        mAllAppsStore = new AllAppsStore<>(mActivityContext);

        pref2 = PreferenceManager2.getInstance(mActivityContext);
        pref = PreferenceManager.getInstance(mActivityContext);

        mScrimColor = ColorTokens.AllAppsScrimColor.resolveColor(context);
        mHeaderThreshold = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        mHeaderProtectionColor = ColorTokens.AllAppsHeaderProtectionColor.resolveColor(context);

        mWorkManager = new WorkProfileManager(
                mActivityContext.getSystemService(UserManager.class),
                this,
                mActivityContext.getStatsLogManager(),
                UserCache.INSTANCE.get(mActivityContext));
        mPrivateProfileManager = new PrivateProfileManager(
                mActivityContext.getSystemService(UserManager.class),
                this,
                mActivityContext.getStatsLogManager(),
                UserCache.INSTANCE.get(mActivityContext));
        mPrivateSpaceBottomExtraSpace = context.getResources().getDimensionPixelSize(
                R.dimen.ps_extra_bottom_padding);
        mAH = Arrays.asList(null, null, null);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(mActivityContext));

        AllAppsStore.OnUpdateListener onAppsUpdated = this::onAppsUpdated;
        mAllAppsStore.addUpdateListener(onAppsUpdated);

        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });
        mSearchUiDelegate = createSearchUiDelegate();
        initContent();

        mSearchTransitionController = new SearchTransitionController(this);
    }

    protected AllAppsSearchUiDelegate createSearchUiDelegate() {
        return new AllAppsSearchUiDelegate(this);
    }

    public AllAppsSearchUiDelegate getSearchUiDelegate() {
        return mSearchUiDelegate;
    }

    /**
     * Initializes the view hierarchy and internal variables. Any initialization which actually uses
     * these members should be done in {@link #onFinishInflate()}.
     */
    protected void initContent() {
        showFastScroller = PreferenceExtensionsKt.firstBlocking(pref2.getShowScrollbar());

        mMainAdapterProvider = mSearchUiDelegate.createMainAdapterProvider();

        mAH.set(AdapterHolder.MAIN, new AdapterHolder(AdapterHolder.MAIN,
                new LawnchairAlphabeticalAppsList<>(mActivityContext,
                        mAllAppsStore, null, mPrivateProfileManager)));
        mAH.set(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK,
                new LawnchairAlphabeticalAppsList<>(mActivityContext, mAllAppsStore, mWorkManager, null)));
        mAH.set(SEARCH, new AdapterHolder(SEARCH,
                new LawnchairAlphabeticalAppsList<>(mActivityContext, mAllAppsStore, null, null)));

        getLayoutInflater().inflate(R.layout.all_apps_content, this);
        mHeader = findViewById(R.id.all_apps_header);
        mAdditionalHeaderRows.clear();
        mAdditionalHeaderRows.addAll(getAdditionalHeaderRows());
        mBottomSheetBackground = findViewById(R.id.bottom_sheet_background);
        mBottomSheetHandleArea = findViewById(R.id.bottom_sheet_handle_area);
        mBottomSheetHandle = findViewById(R.id.bottom_sheet_handle);
        mSearchRecyclerView = findViewById(R.id.search_results_list_view);
        mFastScroller = findViewById(R.id.fast_scroller);
        mFastScroller.setPopupView(findViewById(R.id.fast_scroller_popup));
        mFastScroller.setVisibility(showFastScroller ? VISIBLE : INVISIBLE);
        mFastScrollLetterLayout = findViewById(R.id.scroll_letter_layout);
        setClipChildren(false);

        mSearchContainer = inflateSearchBar();
        if (!isSearchBarFloating()) {
            addView(mSearchContainer);
            mSearchContainer.setFocusedByDefault(true);
        }
        mSearchUiManager = (SearchUiManager) mSearchContainer;
    }

    public List<AllAppsRow> getAdditionalHeaderRows() {
        return List.of();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAH.get(SEARCH).setup(mSearchRecyclerView, itemInfo -> false);
        rebindAdapters(true /* force */);
        float cornerRadius = Themes.getDialogCornerRadius(getContext());
        mBottomSheetCornerRadii = new float[]{
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                0, 0,
                0, 0
        };

        // LC-Note: System cross-window blur removed; always use legacy solid-colour.
        mBottomSheetBackgroundColorLegacy = ColorTokens.SurfaceDimColor.resolveColor(getContext());

        // LC-Note: Update our allapps cached colour
        updateBottomSheetBackgroundColor();

        updateBackgroundVisibility(mActivityContext.getDeviceProfile());
        mSearchUiManager.initializeSearch(this);

        // LC-Note (search-bar-at-bottom): the slide-up animation was previously
        // only triggered from animateToSearchState(true), which itself is only
        // called from setSearchResults() once results actually exist/change
        // (see setSearchResults() below). That meant tapping the (empty) search
        // bar to focus it had no visual effect — the bar stayed at the bottom
        // until the first keystroke produced results. Use a global focus-change
        // listener instead, since it fires the instant the EditText gains/loses
        // focus, regardless of whether there's any text/results yet. This is
        // additive via addOnGlobalFocusChangeListener (not
        // setOnFocusChangeListener), so it does not clobber whatever focus
        // handling the EditText itself or AllAppsSearchInput already do
        // internally for IME show/hide.
        View searchEditText = mSearchUiManager.getEditText();
        if (searchEditText != null) {
            searchEditText.getViewTreeObserver().addOnGlobalFocusChangeListener(
                    (oldFocus, newFocus) -> {
                        if (newFocus == searchEditText) {
                            animateSearchContainerForSearchState(
                                    true, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
                        } else if (oldFocus == searchEditText && !isSearching()) {
                            // Only slide back down here if we're not actually in
                            // the search results state — in that case dismissal
                            // is already handled by animateToSearchState(false,
                            // ...) via the existing exit-search flow (back press /
                            // clear button), which already works correctly.
                            animateSearchContainerForSearchState(
                                    false, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
                        }
                    });
        }

        // Kick off the async HokoBlur bitmap computation.
        applyDrawerHokoBlur();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isSearchBarFloating()) {
            mActivityContext.getDragLayer().addView(mSearchContainer);
            mSearchUiDelegate.onInitializeSearchBar();
        }
        mActivityContext.addOnDeviceProfileChangeListener(this);
        // LC-Note: CrossWindowBlurListeners removed; system blur replaced by HokoBlur.
        // Retry blur here: WallpaperManager.getDrawable() can return null at
        // onFinishInflate time (before the window token is live).  A second attempt
        // once the window is attached succeeds on those devices.  If mBlurBitmap is
        // already non-null from the first attempt this is a cheap no-op (cache hit).
        applyDrawerHokoBlur();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
        // LC-Note: CrossWindowBlurListeners removed; system blur replaced by HokoBlur.
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    public void onClearSearchResult() {
        getMainAdapterProvider().clearHighlightedItem();
        animateToSearchState(false);
        rebindAdapters();
    }

    public void setSearchResults(ArrayList<AdapterItem> results) {
        getMainAdapterProvider().clearHighlightedItem();
        if (getSearchResultList().setSearchResults(results)) {
            getSearchRecyclerView().onSearchResultsChanged();
        }
        if (results != null) {
            animateToSearchState(true);
        }
    }

    public void setSearchResults(ArrayList<AdapterItem> results, int searchResultCode) {
        setSearchResults(results);
        mSearchUiDelegate.onSearchResultsChanged(results, searchResultCode);
    }

    private void animateToSearchState(boolean goingToSearch) {
        animateToSearchState(goingToSearch, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
    }

    public void setAllAppsTransitionController(
            AllAppsTransitionController allAppsTransitionController) {
        mAllAppsTransitionController = allAppsTransitionController;
    }

    void animateToSearchState(boolean goingToSearch, long durationMs) {
        if (!mSearchTransitionController.isRunning() && goingToSearch == isSearching()) {
            return;
        }
        mFastScroller.setVisibility(goingToSearch ? INVISIBLE : VISIBLE);
        if (goingToSearch) {
            mWorkManager.onActivePageChanged(SEARCH);
        } else if (mAllAppsTransitionController != null) {
            mAllAppsTransitionController.animateAllAppsToNoScale();
            mFastScroller.setVisibility(showFastScroller ? VISIBLE : INVISIBLE);
        }
        animateSearchContainerForSearchState(goingToSearch, durationMs);
        mSearchTransitionController.animateToState(goingToSearch, durationMs, () -> {
            mIsSearching = goingToSearch;
            updateSearchResultsVisibility();
            int previousPage = getCurrentPage();
            if (mRebindAdaptersAfterSearchAnimation) {
                rebindAdapters(false);
                mRebindAdaptersAfterSearchAnimation = false;
            }
            if (goingToSearch) {
                mSearchUiDelegate.onAnimateToSearchStateCompleted();
            } else {
                setSearchResults(null);
                if (mViewPager != null) {
                    mViewPager.setCurrentPage(previousPage);
                }
                onActivePageChanged(previousPage);
            }
        });
    }

    /**
     * LC-Note (search-bar-at-bottom): when the search bar is pinned to the
     * bottom of the drawer, tapping it to enter search should slide it up to
     * the top of the drawer, and dismissing search should slide it back down
     * to the bottom — independent of SearchTransitionController, which only
     * animates the A-Z grid/header content, never mSearchContainer's own
     * position. No-op (translationY stays 0) when the preference is off, when
     * the search bar is floating (it already manages its own position), or
     * before the view has been laid out (getTop()==0 && getBottom()==0).
     */
    private void animateSearchContainerForSearchState(boolean goingToSearch, long durationMs) {
        boolean searchBarAtBottom = PreferenceExtensionsKt.firstBlocking(
                pref2.getAppDrawerSearchBarAtBottom());
        if (!searchBarAtBottom || isSearchBarFloating()) return;
        if (mSearchContainer.getHeight() == 0) return;

        mSearchContainer.animate().cancel();
        if (goingToSearch) {
            // Slide up so the bar's bottom edge lands at this container's own
            // top padding (the same resting position a top-anchored search bar
            // would occupy), i.e. translate by (top padding - current top).
            //
            // LC-Note: AppsSearchContainerLayout.onLayout() unconditionally
            // calls offsetTopAndBottom(mContentOverlap) on every layout pass
            // (see all_apps_search_bar_content_overlap, 24dp) — this is how the
            // top-anchored bar overlaps down into the header/RV content by
            // design. mSearchContainer IS the AppsSearchContainerLayout (it
            // extends ExtendedEditText directly, there's no separate wrapper),
            // so that same self-shift already happened to its laid-out getTop()
            // here too. Without adding it back, targetTop landed
            // mContentOverlap px too high versus the true top-mode resting
            // position, which only ever existed with that offset baked in.
            int contentOverlap = getResources().getDimensionPixelSize(
                    R.dimen.all_apps_search_bar_content_overlap);
            int targetTop = getPaddingTop() + contentOverlap;
            float targetTranslationY = targetTop - mSearchContainer.getTop();
            mSearchContainer.animate()
                    .translationY(targetTranslationY)
                    .setDuration(durationMs)
                    .start();
        } else {
            mSearchContainer.animate()
                    .translationY(0f)
                    .setDuration(durationMs)
                    .start();
        }
    }

    public boolean shouldContainerScroll(MotionEvent ev) {
        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        if (dragLayer.isEventOverView(mSearchContainer, ev)) {
            View editText = mSearchUiManager.getEditText();
            if (editText != null && dragLayer.isEventOverView(editText, ev)) {
                return !editText.canScrollVertically(-1);
            }
            return true;
        }
        if (dragLayer.isEventOverView(mBottomSheetHandleArea, ev)) return true;
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) return true;
        if (rv.getScrollbar() != null
                && rv.getScrollbar().getThumbOffsetY() >= 0
                && dragLayer.isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        if (!dragLayer.isEventOverView(getVisibleContainerView(), ev)) return true;
        return rv.shouldContainerScroll(ev, dragLayer);
    }

    public void reset(boolean animate) {
        reset(animate, true);
    }

    public void reset(boolean animate, boolean exitSearch) {
        if (!PreferenceExtensionsKt.firstBlocking(pref2.getRememberPosition())) {
            for (int i = 0; i < mAH.size(); i++) {
                if (i != SEARCH && mAH.get(i).mRecyclerView != null) {
                    mAH.get(i).mRecyclerView.scrollToTop();
                }
            }
        }
        if (mTouchHandler != null) mTouchHandler.endFastScrolling();
        if (mHeader != null && mHeader.getVisibility() == VISIBLE) mHeader.reset(animate);
        updateBackgroundVisibility(mActivityContext.getDeviceProfile());
        updateHeaderScroll(0);
        if (exitSearch) {
            MAIN_EXECUTOR.getHandler().post(mSearchUiManager::resetSearch);
        }
        if (isSearching()) mWorkManager.reset();
    }

    public void resetAndScrollToPrivateSpaceHeader() {
        animateToSearchState(false, 0);
        MAIN_EXECUTOR.getHandler().post(() -> {
            mSearchUiManager.resetSearch();
            switchToTab(ActivityAllAppsContainerView.AdapterHolder.MAIN);
            if (mPrivateProfileManager != null) {
                mPrivateProfileManager.scrollForHeaderToBeVisibleInContainer(
                        getActiveAppsRecyclerView(),
                        getPersonalAppList().getAdapterItems(),
                        mPrivateProfileManager.getPsHeaderHeight(),
                        mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx());
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    public String getDescription() {
        if (!mUsingTabs && isSearching()) {
            return getContext().getString(R.string.all_apps_search_results);
        } else {
            StringCache cache = mActivityContext.getStringCache();
            if (mUsingTabs) {
                if (cache != null) {
                    return isPersonalTab()
                            ? cache.allAppsPersonalTabAccessibility
                            : cache.allAppsWorkTabAccessibility;
                } else {
                    return isPersonalTab()
                            ? getContext().getString(R.string.all_apps_button_personal_label)
                            : getContext().getString(R.string.all_apps_button_work_label);
                }
            }
            return getContext().getString(R.string.all_apps_button_label);
        }
    }

    public boolean isSearching() { return mIsSearching; }

    public boolean shouldBackExitSearch() { return isSearching(); }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        if (mSearchTransitionController.isRunning()) return;
        if (currentActivePage != SEARCH) mActivityContext.hideKeyboard();
        if (mAH.get(currentActivePage).mRecyclerView != null) {
            mAH.get(currentActivePage).mRecyclerView.bindFastScrollbar(mFastScroller, ALL_APPS_SCROLLER);
        }
        mHeader.setActiveRV(currentActivePage);
        reset(true, !isSearching());
        mWorkManager.onActivePageChanged(currentActivePage);
    }

    protected void rebindAdapters() {
        rebindAdapters(false);
    }

    protected void rebindAdapters(boolean force) {
        Log.d(TAG, "rebindAdapters: force: " + force);
        if (mSearchTransitionController.isRunning()) {
            mRebindAdaptersAfterSearchAnimation = true;
            return;
        }
        updateSearchResultsVisibility();

        boolean showTabs = shouldShowTabs();
        if (showTabs == mUsingTabs && !force) {
            Log.d(TAG, "rebindAdapters: Not needed.");
            return;
        }

        replaceAppsRVContainer(showTabs);
        mUsingTabs = showTabs;

        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);

        final AllAppsRecyclerView mainRecyclerView;
        final AllAppsRecyclerView workRecyclerView;
        if (mUsingTabs) {
            mainRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(0);
            workRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(1);
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView, mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).setup(workRecyclerView, mWorkManager.getItemInfoMatcher());
            workRecyclerView.setId(R.id.apps_list_view_work);
            if (enableExpandingPauseWorkButton()
                    || FeatureFlags.ENABLE_EXPANDING_PAUSE_WORK_BUTTON.get()) {
                mAH.get(AdapterHolder.WORK).mRecyclerView.addOnScrollListener(
                        mWorkManager.newScrollListener());
            }
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.MAIN);
            findViewById(R.id.tab_personal).setOnClickListener((View view) -> {
                Log.d(TAG, "rebindAdapters: Clicked personal tab.");
                if (mViewPager.snapToPage(AdapterHolder.MAIN)) {
                    mActivityContext.getStatsLogManager().logger()
                            .log(LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB);
                }
            });
            findViewById(R.id.tab_work).setOnClickListener((View view) -> {
                Log.d(TAG, "rebindAdapters: Clicked work tab.");
                if (mViewPager.snapToPage(AdapterHolder.WORK)) {
                    mActivityContext.getStatsLogManager().logger()
                            .log(LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB);
                }
            });
            setDeviceManagementResources();
            if (mHeader.isSetUp()) onActivePageChanged(mViewPager.getNextPage());
        } else {
            mainRecyclerView = findViewById(R.id.apps_list_view);
            workRecyclerView = null;
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView, mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).mRecyclerView = null;
        }
        setUpCustomRecyclerViewPool(mainRecyclerView, workRecyclerView,
                mAllAppsStore.getRecyclerViewPool());
        setupHeader();

        if (isSearchBarFloating()) {
            RelativeLayout.LayoutParams lp = (LayoutParams) mFastScroller.getLayoutParams();
            lp.bottomMargin = mSearchContainer.getHeight()
                    + getResources().getDimensionPixelSize(
                            R.dimen.fastscroll_bottom_margin_floating_search);
        }

        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);
    }

    private static void setUpCustomRecyclerViewPool(
            @NonNull AllAppsRecyclerView mainRV,
            @Nullable AllAppsRecyclerView workRV,
            @NonNull AllAppsRecyclerViewPool pool) {
        final boolean hasWork = workRV != null;
        pool.setHasWorkProfile(hasWork);
        mainRV.setRecycledViewPool(pool);
        if (workRV != null) workRV.setRecycledViewPool(pool);
        mainRV.updatePoolSize(hasWork);
    }

    private void replaceAppsRVContainer(boolean showTabs) {
        Log.d(TAG, "replaceAppsRVContainer: showTabs: " + showTabs);
        for (int i = AdapterHolder.MAIN; i <= AdapterHolder.WORK; i++) {
            AdapterHolder h = mAH.get(i);
            if (h.mRecyclerView != null) {
                h.mRecyclerView.setLayoutManager(null);
                h.mRecyclerView.setAdapter(null);
            }
        }
        View oldView = getAppsRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;
        final View rvContainer = getLayoutInflater().inflate(layout, this, false);
        addView(rvContainer, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) rvContainer;
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);
            mViewPager.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    @Px final int bottomOffsetPx =
                            (int) (ActivityAllAppsContainerView.this.getMeasuredHeight()
                                    * PREDICTIVE_BACK_MIN_SCALE);
                    outline.setRect(0, 0, view.getMeasuredWidth(),
                            view.getMeasuredHeight() + bottomOffsetPx);
                }
            });
            mWorkManager.reset();
            post(() -> mAH.get(AdapterHolder.WORK).applyPadding());
        } else {
            mWorkManager.detachWorkUtilityViews();
            mViewPager = null;
        }

        removeCustomRules(rvContainer);
        removeCustomRules(getSearchRecyclerView());
        if (isSearchBarFloating()) {
            alignParentTop(rvContainer, showTabs);
            alignParentTop(getSearchRecyclerView(), false);
        } else {
            layoutBelowSearchContainer(rvContainer, showTabs);
            layoutBelowSearchContainer(getSearchRecyclerView(), false);
        }
        updateSearchResultsVisibility();
    }

    void setupHeader() {
        mAdditionalHeaderRows.forEach(row -> mHeader.onPluginDisconnected(row));

        var hideHeader = PreferenceExtensionsKt.firstBlocking(pref2.getHideAppDrawerSearchBar());
        mHeader.setVisibility(hideHeader ? View.GONE : View.VISIBLE);
        boolean tabsHidden = !mUsingTabs;
        mHeader.setup(
                mAH.get(AdapterHolder.MAIN).mRecyclerView,
                mAH.get(AdapterHolder.WORK).mRecyclerView,
                (SearchRecyclerView) mAH.get(SEARCH).mRecyclerView,
                getCurrentPage(), tabsHidden);

        // LC-Note (fix for app-drawer-search-bar-disabled blank space): when the
        // header is hidden there is no search bar / tab-pill content left to leave
        // room for. mHeader.getMaxTranslation() would otherwise still fall back to
        // R.dimen.all_apps_search_bar_bottom_padding (the gap meant to sit *under*
        // a visible search bar), which is exactly the leftover blank space at the
        // top of the drawer that was reported. Force this padding to 0 in that
        // case so the app grid / search results sit flush right under the existing
        // top inset (i.e. right under the drag handle) instead of leaving a gap.
        //
        // LC-Note: when hideHeader=true we intentionally leave padding=0 here.
        // The handle-area clearance is applied later in setInsets(), which runs
        // after the view tree has been measured and mBottomSheetHandleArea.getHeight()
        // returns the real pixel value. Doing it here would always read 0 because
        // setupHeader() is called from onFinishInflate(), before layout/measure.
        int padding = hideHeader ? 0 : mHeader.getMaxTranslation();
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.top = padding;
            adapterHolder.applyPadding();
            if (adapterHolder.mRecyclerView != null) adapterHolder.mRecyclerView.scrollToTop();
        });
        mAdditionalHeaderRows.forEach(row -> mHeader.onPluginConnected(row, mActivityContext));

        removeCustomRules(mHeader);
        if (isSearchBarFloating()) {
            alignParentTop(mHeader, false);
        } else {
            layoutBelowSearchContainer(mHeader, false);
        }
        layoutSearchContainer();
    }

    public void forceUpdateHeaderHeight(int offset) {
        mHeader.updateSearchBarOffset(offset);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> arrayList) {
        super.addChildrenForAccessibility(arrayList);
        if (!Flags.floatingSearchBar()) {
            arrayList.stream().filter(v -> v.getId() == R.id.search_container_all_apps)
                    .findFirst().ifPresent(v -> {
                        arrayList.remove(v);
                        arrayList.add(0, v);
                    });
        }
    }

    protected void updateHeaderScroll(int scrolledOffset) {
        if (PreferenceExtensionsKt.firstBlocking(pref2.getHideAppDrawerSearchBar())) return;

        boolean showTabContainerBackground = PreferenceExtensionsKt.firstBlocking(
                pref2.getWorkProfileTabContainerBackground());

        float prog = Utilities.boundToRange((float) scrolledOffset / mHeaderThreshold, 0f, 1f);
        int headerColor = getHeaderColor(prog);
        int tabsAlpha = (!showTabContainerBackground
                || mHeader.getPeripheralProtectionHeight(false) == 0) ? 0
                : (int) (Utilities.boundToRange(
                        (scrolledOffset + mHeader.mSnappedScrolledY) / mHeaderThreshold, 0f, 1f)
                        * 255);
        if (headerColor != mHeaderColor || mTabsProtectionAlpha != tabsAlpha) {
            mHeaderColor = headerColor;
            mTabsProtectionAlpha = tabsAlpha;
            invalidateHeader();
        }
        if (mSearchUiManager.getEditText() == null) return;
        mSearchUiManager.setBackgroundVisibility(true, 1f);
    }

    protected int getHeaderColor(float blendRatio) {
        // appDrawerSearchBarBackground is the single on/off control for the
        // header-protection scrim across ALL layout modes (sheet / phone) and
        // ALL blur states.
        //
        // false → Color.TRANSPARENT (== 0).  The draw path's early-return guard
        //         "mHeaderPaint.getColor() == 0" then fires before any canvas
        //         drawing, so neither the sheet rounded-rect (line ~1275) nor the
        //         phone full-width drawRect (line ~1278) is ever executed.
        //
        // true  → fall through to per-mode colour calculation below.
        boolean showHeaderBackground = PreferenceExtensionsKt.firstBlocking(
                pref2.getAppDrawerSearchBarBackground());
        if (!showHeaderBackground) {
            return Color.TRANSPARENT;
        }

        if (!mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            // Phone / non-sheet mode: use drawer opacity as the scrim alpha so
            // the protection blends proportionally with the background.
            float opacity = pref.getDrawerOpacity().get();
            opacity = MathUtils.clamp(opacity, 0f, 1f);
            return ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(getBackgroundColor(), mHeaderProtectionColor, blendRatio),
                    Math.round(opacity * 255));
        }
        // Sheet mode: when HokoBlur is active show a semi-transparent tint so
        // the blurred wallpaper is still partially visible through the header.
        // Without blur, blend the background with the header-protection colour.
        return (mBlurBitmap != null)
                ? ColorUtils.setAlphaComponent(mHeaderProtectionColor, (int) (blendRatio * 255))
                : ColorUtils.blendARGB(getBackgroundColor(), mHeaderProtectionColor, blendRatio);
    }

    private int getBackgroundColor() {
        return mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()
                ? getBottomSheetBackgroundColor() : mScrimColor;
    }

    // LC-Note: cached — see updateBottomSheetBackgroundColor()
    int getBottomSheetBackgroundColor() {
        return mCachedBottomSheetBgColor;
    }

    /**
     * Recomputes the cached bottom-sheet background colour.
     *
     * LC-Note: System cross-window blur has been fully replaced by HokoBlur.
     * Only the legacy solid-colour path is used here.
     */
    private boolean updateBottomSheetBackgroundColor() {
        int newColor = LawnchairUtilsKt.getAllAppsBackgroundColor(
                mActivityContext, mBottomSheetBackgroundColorLegacy);
        if (mCachedBottomSheetBgColor != newColor) {
            mCachedBottomSheetBgColor = newColor;
            return true;
        }
        return false;
    }

    /**
     * System cross-window blur has been replaced by HokoBlur.
     * Always {@code false} so callers like PrivateProfileManager take the non-blur path.
     */
    boolean isBackgroundBlurEnabled() {
        return false;
    }

    protected boolean isSearchBarFloating() {
        return mSearchUiDelegate.isSearchBarFloating();
    }

    public boolean shouldFloatingSearchBarBePillWhenUnfocused() { return false; }

    public int getFloatingSearchBarRestingMarginBottom() { return 0; }

    public int getFloatingSearchBarRestingMarginStart() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    public int getFloatingSearchBarRestingMarginEnd() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    /**
     * LC-Note (fix for app-drawer-search-bar-disabled blank space):
     *
     * R.dimen.all_apps_header_top_margin is sized to clear the *visible* search
     * bar it bakes in the search bar's own rendered height plus a deliberate
     * visual gap below it. When hideAppDrawerSearchBar is on, AllAppsSearchInput
     * collapses its own height to 0 (see AllAppsSearchInput.onFinishInflate),
     * but this method was still unconditionally applying that margin on top of
     * the (now zero-height) search container, plus an extra
     * all_apps_header_pill_height margin for the personal/work tab pill even
     * though setupHeader() puts the *entire* mHeader (which contains that pill)
     * into View.GONE in this state. The net effect was exactly the blank gap
     * that was reported: the content was being pushed down by a margin sized
     * for UI that is no longer there.
     *
     * Since there is nothing left to clear in that state, skip the margin
     * entirely so the content's top aligns directly with the (now zero-height)
     * search container's top i.e. right under the existing top inset / drag
     * handle area, with no extra blank space.
     */
    private void layoutBelowSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;
        RelativeLayout.LayoutParams lp = (LayoutParams) v.getLayoutParams();

        // LC-Note (search-bar-at-bottom): when the search bar is pinned to the
        // bottom of the drawer, content (RV containers, header) should fill from
        // the parent's top instead of being anchored below the search container,
        // since the search container is no longer at the top at all. The bottom
        // padding needed to clear the now-bottom-pinned search bar is handled
        // separately in applyAdapterSideAndBottomPaddings(), not here.
        boolean searchBarAtBottom = PreferenceExtensionsKt.firstBlocking(
                pref2.getAppDrawerSearchBarAtBottom());
        if (searchBarAtBottom) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            lp.topMargin = 0;
            return;
        }

        lp.addRule(RelativeLayout.ALIGN_TOP, R.id.search_container_all_apps);

        boolean hideSearchBar =
                PreferenceExtensionsKt.firstBlocking(pref2.getHideAppDrawerSearchBar());
        int topMargin = 0;
        if (!hideSearchBar) {
            topMargin = getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_top_margin);
            if (includeTabsMargin) {
                topMargin += getContext().getResources().getDimensionPixelSize(
                        R.dimen.all_apps_header_pill_height);
            }
        }
        lp.topMargin = topMargin;
    }

    /**
     * LC-Note: used only when the search bar is floating (overlaid via the drag
     * layer rather than reserving space in this RelativeLayout's flow), so
     * content always starts at the parent's top regardless of whether the
     * search bar itself is shown or hidden. The only thing that still needs
     * accounting for here is the personal/work tab pill margin and that pill
     * lives inside mHeader, which is set to View.GONE whenever
     * hideAppDrawerSearchBar is on, so that margin must be skipped in that case
     * too (previously this method just bailed out entirely when the pref was
     * on, leaving stale/unset RelativeLayout rules instead of doing the right
     * thing).
     */
    private void alignParentTop(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;
        RelativeLayout.LayoutParams lp = (LayoutParams) v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        boolean hideSearchBar =
                PreferenceExtensionsKt.firstBlocking(pref2.getHideAppDrawerSearchBar());
        lp.topMargin = (includeTabsMargin && !hideSearchBar)
                ? getContext().getResources().getDimensionPixelSize(
                        R.dimen.all_apps_header_pill_height)
                : 0;
    }

    /**
     * LC-Note (search-bar-at-bottom): pins mSearchContainer to the bottom of this
     * RelativeLayout instead of leaving it at its implicit default top position.
     * Bottom margin clears the system nav bar inset so the bar isn't obscured.
     * Only applies when the search bar is not floating (floating search bar
     * already manages its own bottom-anchored position via the DragLayer /
     * mSearchUiDelegate, independent of this preference).
     */
    private void layoutSearchContainer() {
        if (!(mSearchContainer.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;
        RelativeLayout.LayoutParams lp = (LayoutParams) mSearchContainer.getLayoutParams();
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        boolean searchBarAtBottom = PreferenceExtensionsKt.firstBlocking(
                pref2.getAppDrawerSearchBarAtBottom());
        if (searchBarAtBottom && !isSearchBarFloating()) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            // LC-Note: mNavBarScrimHeight (computeNavBarScrimHeight()) reads the
            // RAW WindowInsets handed to dispatchApplyWindowInsets(). That object
            // never passes through LawnchairWindowManagerProxy.normalizeWindowInsets()
            // - the proxy's corrected values only ever reach this view via
            // LauncherRootView.updateInsets() -> handleSystemWindowInsets(Rect) ->
            // setInsets(Rect), i.e. mInsets. Confirmed via logging that
            // mNavBarScrimHeight reads 0 in every nav mode (gesture/3-button/
            // 2-button) while mInsets.bottom correctly carries the normalized nav
            // bar height. Use mInsets.bottom alone; do not factor in
            // mNavBarScrimHeight here, it is not meaningful for this view.
            lp.bottomMargin = mInsets.bottom;
            // LC-Note: AppsSearchContainerLayout.setInsets() (its own Insettable
            // implementation, called earlier via InsettableFrameLayout
            // .dispatchInsets() in our setInsets()) unconditionally sets
            // topMargin = insets.top on this same view, for the top-anchored
            // case. That topMargin is meaningless once this view is
            // bottom-anchored, but left as-is it can still pollute getTop()'s
            // resolved value, so zero it here.
            lp.topMargin = 0;
        } else {
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.bottomMargin = 0;
        }
        // TEMP-DEBUG (remove after confirming fix): if this line never appears
        // in logcat, the running APK does not contain this code change.
        android.util.Log.d("LCSearchBarDebug", "BUILD_MARKER_V2: layoutSearchContainer "
                + "set bottomMargin=" + lp.bottomMargin + " searchBarAtBottom=" + searchBarAtBottom
                + " isSearchBarFloating=" + isSearchBarFloating() + " mInsets.bottom=" + mInsets.bottom);
        mSearchContainer.setLayoutParams(lp);
    }

    /**
     * LC-Note: this must always clear stale rules before the caller re-applies
     * the correct one via layoutBelowSearchContainer()/alignParentTop() above.
     * It previously bailed out early whenever hideAppDrawerSearchBar was on,
     * which left old RelativeLayout rules in place instead of resetting them
     * removed that short-circuit since it served no purpose other than leaving
     * the layout in a stale state.
     */
    private void removeCustomRules(View v) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;
        RelativeLayout.LayoutParams lp = (LayoutParams) v.getLayoutParams();
        lp.removeRule(RelativeLayout.ABOVE);
        lp.removeRule(RelativeLayout.ALIGN_TOP);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
    }

    protected BaseAllAppsAdapter<T> createAdapter(AlphabeticalAppsList<T> appsList) {
        return new AllAppsGridAdapter<>(mActivityContext, getLayoutInflater(), appsList,
                mMainAdapterProvider);
    }

    public boolean isInAllApps() { return true; }

    protected SearchAdapterProvider<?> createMainAdapterProvider() {
        return new DefaultSearchAdapterProvider(mActivityContext);
    }

    protected View inflateSearchBar() {
        return mSearchUiDelegate.inflateSearchBar();
    }

    public final SearchAdapterProvider<?> getMainAdapterProvider() {
        return mMainAdapterProvider;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> sparseArray) {
        try {
            super.dispatchRestoreInstanceState(sparseArray);
        } catch (Exception e) {
            Log.e("AllAppsContainerView", "restoreInstanceState viewId = 0", e);
        }
        Bundle state = (Bundle) sparseArray.get(R.id.work_tab_state_id, null);
        if (state != null) {
            int currentPage = state.getInt(BUNDLE_KEY_CURRENT_PAGE, 0);
            if (currentPage == AdapterHolder.WORK && mViewPager != null) {
                mViewPager.setCurrentPage(currentPage);
                rebindAdapters();
            } else {
                reset(true);
            }
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        Bundle state = new Bundle();
        state.putInt(BUNDLE_KEY_CURRENT_PAGE, getCurrentPage());
        container.put(R.id.work_tab_state_id, state);
    }

    public AllAppsStore<T> getAppsStore() { return mAllAppsStore; }

    public WorkProfileManager getWorkManager() { return mWorkManager; }

    public boolean hasPrivateProfile() { return mHasPrivateApps; }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            holder.mAdapter.setAppsPerRow(dp.numShownAllAppsColumns);
            holder.mAppsList.setNumAppsPerRowAllApps(dp.numShownAllAppsColumns);
            if (holder.mRecyclerView != null) {
                holder.mRecyclerView.swapAdapter(holder.mRecyclerView.getAdapter(), true);
                holder.mRecyclerView.getRecycledViewPool().clear();
            }
        }
        updateBackgroundVisibility(dp);

        boolean needsInvalidate = false;
        int navBarScrimColor = Themes.getNavBarScrimColor(mActivityContext);
        if (mNavBarScrimPaint.getColor() != navBarScrimColor) {
            mNavBarScrimPaint.setColor(navBarScrimColor);
            needsInvalidate = true;
        }
        // LC-Note: Update our allapps cached colour
        if (updateBottomSheetBackgroundColor()) needsInvalidate = true;
        if (needsInvalidate) invalidate();

        // Screen dimensions changed (rotation): recompute the HokoBlur bitmap.
        DrawerWallpaperBlurHelper.clearCache();
        applyDrawerHokoBlur();
    }

    protected void updateBackgroundVisibility(DeviceProfile deviceProfile) {
        mBottomSheetBackground.setVisibility(
                deviceProfile.shouldShowAllAppsOnSheet() ? View.VISIBLE : View.GONE);
    }

    @VisibleForTesting
    public void onAppsUpdated() {
        Log.d(TAG, "onAppsUpdated; number of apps: " + mAllAppsStore.getApps().length);
        mHasWorkApps = Stream.of(mAllAppsStore.getApps()).anyMatch(mWorkManager.getItemInfoMatcher());
        mHasPrivateApps = Stream.of(mAllAppsStore.getApps())
                .anyMatch(mPrivateProfileManager.getItemInfoMatcher());
        if (!isSearching()) rebindAdapters();
        if (mHasWorkApps) mWorkManager.reset();
        if (mHasPrivateApps) mPrivateProfileManager.reset();
        mActivityContext.getStatsLogManager().logger()
                .withCardinality(mAllAppsStore.getApps().length)
                .log(LAUNCHER_ALLAPPS_COUNT);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isInAllApps()) { mTouchHandler = null; return false; }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;
            }
        }
        if (mTouchHandler != null) return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isInAllApps()) return false;
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;
            }
        }
        if (mTouchHandler != null) { mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset); return true; }
        if (isSearching() && mActivityContext.getDragLayer().isEventOverView(getVisibleContainerView(), ev)) return true;
        return false;
    }

    public AllAppsRecyclerView getActiveRecyclerView() {
        return isSearching() ? getSearchRecyclerView() : getActiveAppsRecyclerView();
    }

    protected void forAllRecyclerViews(Consumer<AllAppsRecyclerView> consumer) {
        for (AdapterHolder holder : mAH) {
            if (holder.mRecyclerView != null) consumer.accept(holder.mRecyclerView);
        }
    }

    public OnFocusChangeListener getSearchFocusChangeListener() {
        return mAH.get(AdapterHolder.SEARCH).mOnFocusChangeListener;
    }

    private AllAppsRecyclerView getActiveAppsRecyclerView() {
        if (!mUsingTabs || isPersonalTab()) return mAH.get(AdapterHolder.MAIN).mRecyclerView;
        return mAH.get(AdapterHolder.WORK).mRecyclerView;
    }

    public ViewGroup getAppsRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    public SearchRecyclerView getSearchRecyclerView() { return mSearchRecyclerView; }

    protected boolean isPersonalTab() {
        return mViewPager == null || mViewPager.getNextPage() == 0;
    }

    public void switchToTab(int tab) {
        if (mUsingTabs) mViewPager.setCurrentPage(tab);
    }

    public LayoutInflater getLayoutInflater() { return mSearchUiDelegate.getLayoutInflater(); }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {}

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        applyAdapterSideAndBottomPaddings(grid);
        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        if (grid.getDeviceProperties().isTablet()) {
            mlp.leftMargin = mlp.rightMargin = 0;
        } else {
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
        }
        setLayoutParams(mlp);
        if (!grid.isVerticalBarLayout() || FeatureFlags.enableResponsiveWorkspace()) {
            int topPadding = grid.allAppsPadding.top;
            if (isSearchBarFloating() && !grid.shouldShowAllAppsOnSheet()) {
                topPadding += getResources().getDimensionPixelSize(
                        R.dimen.all_apps_additional_top_padding_floating_search);
            }
            setPadding(grid.allAppsLeftRightMargin, topPadding, grid.allAppsLeftRightMargin, 0);
        }
        InsettableFrameLayout.dispatchInsets(this, insets);
        layoutSearchContainer();

        // LC-Note (fix for icon clipping behind drag handle when hideAppDrawerSearchBar
        // is on): setupHeader() sets adapterHolder.mPadding.top = 0 when the header is
        // hidden, but it runs from onFinishInflate() before layout/measure so
        // mBottomSheetHandleArea.getHeight() returns 0 there. setInsets() is called
        // after the view tree is measured, so this is the earliest safe place to read
        // the real handle area height and push it into the adapter top padding.
        // However, mBottomSheetHandleArea's parent (bottom_sheet_background) is marked
        // visibility="gone" in the layout, so getHeight() always returns 0 regardless
        // of timing. Use the dimen directly instead — it is the fixed pixel height of
        // the handle area and is always correct.
        // In phone/non-sheet mode shouldShowAllAppsOnSheet() is false so we skip this,
        // keeping the adapter padding at 0 as before.
        boolean hideHeader = PreferenceExtensionsKt.firstBlocking(
                pref2.getHideAppDrawerSearchBar());
        if (hideHeader && mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            int handleHeight = getResources().getDimensionPixelSize(
                    R.dimen.bottom_sheet_handle_area_height);
            mAH.forEach(adapterHolder -> {
                adapterHolder.mPadding.top = handleHeight;
                adapterHolder.applyPadding();
            });
        }
    }

    protected int computeNavBarScrimHeight(WindowInsets insets) { return 0; }

    public int getNavBarScrimHeight() { return mNavBarScrimHeight; }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = computeNavBarScrimHeight(insets);
        applyAdapterSideAndBottomPaddings(mActivityContext.getDeviceProfile());
        // LC-Note (search-bar-at-bottom): mNavBarScrimHeight is only known to be
        // fresh as of the line above. layoutSearchContainer()'s bottom margin
        // depends on it (see its own Math.max(mInsets.bottom, mNavBarScrimHeight)
        // comment), so recompute the search bar's position here too, not just in
        // setInsets()/setupHeader(), or it can end up using a stale (too small)
        // value from before this callback fired.
        layoutSearchContainer();
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mNavBarScrimHeight > 0) {
            float left = (getWidth() - getWidth() / getScaleX()) / 2;
            float top = getHeight() / 2f + (getHeight() / 2f - mNavBarScrimHeight) / getScaleY();
            canvas.drawRect(left, top, getWidth() / getScaleX(),
                    top + mNavBarScrimHeight / getScaleY(), mNavBarScrimPaint);
        }
    }

    protected void updateSearchResultsVisibility() {
        if (isSearching()) {
            getSearchRecyclerView().setVisibility(VISIBLE);
            getAppsRecyclerViewContainer().setVisibility(GONE);
            mHeader.setVisibility(GONE);
        } else {
            getSearchRecyclerView().setVisibility(GONE);
            getAppsRecyclerViewContainer().setVisibility(VISIBLE);
            // LC-Note (fix for tab-pill re-appearing after search when header is
            // hidden): the original code unconditionally restored mHeader to VISIBLE
            // here, overriding the View.GONE that setupHeader() applied when
            // hideAppDrawerSearchBar is on. That caused the tab pill to reappear
            // on top of the icon grid with no top padding to clear it (icon
            // clipping bug in image 3). Guard the restore with the same pref.
            boolean hideHeader = PreferenceExtensionsKt.firstBlocking(
                    pref2.getHideAppDrawerSearchBar());
            mHeader.setVisibility(hideHeader ? GONE : VISIBLE);
        }
        if (mHeader.isSetUp()) mHeader.setActiveRV(getCurrentPage());
    }

    private void applyAdapterSideAndBottomPaddings(DeviceProfile grid) {
        int bottomPadding = Math.max(mInsets.bottom, mNavBarScrimHeight);

        // LC-Note (search-bar-at-bottom): when the search bar is pinned to the
        // bottom of the drawer (non-floating), reserve extra bottom padding equal
        // to its height so the last row of icons doesn't render behind it. The
        // floating search bar already does the analogous thing for itself via
        // AdapterHolder.applyPadding()'s own isSearchBarFloating() check, so this
        // only applies to the non-floating bottom-pin case to avoid double-adding.
        boolean searchBarAtBottom = PreferenceExtensionsKt.firstBlocking(
                pref2.getAppDrawerSearchBarAtBottom());
        if (searchBarAtBottom && !isSearchBarFloating()) {
            bottomPadding += mSearchContainer.getHeight();
        }

        // Lambdas below require an effectively-final capture; bottomPadding was
        // reassigned above via +=, so it no longer qualifies. Copy into a final.
        final int finalBottomPadding = bottomPadding;
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.bottom = finalBottomPadding;
            adapterHolder.mPadding.left = grid.allAppsPadding.left;
            adapterHolder.mPadding.right = grid.allAppsPadding.right;
            adapterHolder.applyPadding();
        });
    }

    private void setDeviceManagementResources() {
        if (mActivityContext.getStringCache() != null) {
            Button personalTab = findViewById(R.id.tab_personal);
            personalTab.setText(R.string.all_apps_personal_tab);
            personalTab.setAllCaps(false);
            FontManager.INSTANCE.get(getContext()).setCustomFont(personalTab, R.id.font_button);
            Button workTab = findViewById(R.id.tab_work);
            workTab.setText(R.string.all_apps_work_tab);
            workTab.setAllCaps(false);
            FontManager.INSTANCE.get(getContext()).setCustomFont(workTab, R.id.font_button);
        }
    }

    public boolean shouldShowTabs() { return mHasWorkApps; }

    private boolean isDescendantViewVisible(int viewId) {
        final View view = findViewById(viewId);
        if (view == null || !view.isShown()) return false;
        return view.getGlobalVisibleRect(new Rect());
    }

    public void updateWorkUI() {
        setDeviceManagementResources();
        if (mWorkManager.getWorkUtilityView() != null) {
            mWorkManager.getWorkUtilityView().updateStringFromCache();
        }
        inflateWorkCardsIfNeeded();
    }

    private void inflateWorkCardsIfNeeded() {
        AllAppsRecyclerView workRV = mAH.get(AdapterHolder.WORK).mRecyclerView;
        if (workRV != null) {
            for (int i = 0; i < workRV.getChildCount(); i++) {
                View cv = workRV.getChildAt(i);
                int vt = workRV.getChildViewHolder(cv).getItemViewType();
                if (vt == VIEW_TYPE_WORK_EDU_CARD) ((WorkEduCard) cv).updateStringFromCache();
                else if (vt == VIEW_TYPE_WORK_DISABLED_CARD) ((WorkPausedCard) cv).updateStringFromCache();
            }
        }
    }

    @VisibleForTesting public void setWorkManager(WorkProfileManager wm) { mWorkManager = wm; }
    @VisibleForTesting public boolean isPersonalTabVisible() { return isDescendantViewVisible(R.id.tab_personal); }
    @VisibleForTesting public boolean isWorkTabVisible() { return isDescendantViewVisible(R.id.tab_work); }

    public AlphabeticalAppsList<T> getSearchResultList() { return mAH.get(SEARCH).mAppsList; }
    public AlphabeticalAppsList<T> getPersonalAppList() { return mAH.get(MAIN).mAppsList; }
    public AlphabeticalAppsList<T> getWorkAppList() { return mAH.get(WORK).mAppsList; }
    public FloatingHeaderView getFloatingHeaderView() { return mHeader; }

    @VisibleForTesting
    public View getContentView() {
        return isSearching() ? getSearchRecyclerView() : getAppsRecyclerViewContainer();
    }

    public int getCurrentPage() {
        return isSearching() ? SEARCH
                : mViewPager == null ? AdapterHolder.MAIN : mViewPager.getNextPage();
    }

    public PrivateProfileManager getPrivateProfileManager() { return mPrivateProfileManager; }

    public void addSpringFromFlingUpdateListener(ValueAnimator animator,
            float velocity, float progress) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                float distance = (1 - progress) * getHeight();
                float settleVelocity = Math.min(0, distance
                        / (AllAppsTransitionController.INTERP_COEFF * animator.getDuration())
                        + velocity);
                absorbSwipeUpVelocity(Math.max(1000, Math.abs(
                        Math.round(settleVelocity * FLING_VELOCITY_MULTIPLIER))));
            }
        });
    }

    public void onPull(float deltaDistance, float displacement) {
        absorbPullDeltaDistance(PULL_MULTIPLIER * deltaDistance, PULL_MULTIPLIER * displacement);
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        invalidateHeader();
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        try {
            if (predictiveBackThreeButtonNav() && mNavBarScrimHeight > 0) {
                invalidate(20, getHeight() - mNavBarScrimHeight, getWidth(), getHeight());
            }
        } catch (Throwable t) { /* LC-Ignored */ }
    }

    public void setAllAppsSearchBackAnimatorListener(Animator.AnimatorListener listener) {
        Preconditions.assertNotNull(mAllAppsTransitionController);
        if (mAllAppsTransitionController == null) return;
        mAllAppsTransitionController.setAllAppsSearchBackAnimationListener(listener);
    }

    public void setScrimView(ScrimView scrimView) { mScrimView = scrimView; }

    @Override
    public void drawOnScrimWithScaleAndBottomOffset(Canvas canvas, float scale,
            @Px int bottomOffsetPx) {
        final View panel = mBottomSheetBackground;
        final boolean hasBottomSheet = panel.getVisibility() == VISIBLE;

        // ── HokoBlur ──────────────────────────────────────────────────────────
        // The dark background visible in the drawer is ScrimView drawing through
        // the transparent AllApps container.  Drawing the blur bitmap here (on
        // ScrimView's own canvas, before any other scrim content) replaces that
        // dark scrim with the blurred wallpaper.  Phone-only: tablets use the
        // bottom-sheet path and their background is managed separately.
        final Bitmap hokoBlur = mBlurBitmap;
        if (hokoBlur != null && !hokoBlur.isRecycled() && !hasBottomSheet) {
            canvas.drawBitmap(hokoBlur, 0f, 0f, null);
        }
        // ─────────────────────────────────────────────────────────────────────
        final float translationY = ((View) panel.getParent()).getTranslationY();

        final float horizontalScaleOffset = (1 - scale) * panel.getWidth() / 2;
        final float verticalScaleOffset = (1 - scale) * (panel.getHeight() - getHeight() / 2);
        float left = getLeft() + panel.getLeft();
        float right = left + panel.getWidth();

        final float topNoScale = panel.getTop() + translationY;
        final float topWithScale = topNoScale + verticalScaleOffset;
        final float leftWithScale = left + horizontalScaleOffset;
        final float rightWithScale = right - horizontalScaleOffset;
        final float bottomWithOffset = panel.getBottom() + bottomOffsetPx;

        int bottomSheetBgColor = getBottomSheetBackgroundColor();
        float bottomSheetBgAlpha = Color.alpha(bottomSheetBgColor) / 255.0f;
        if (hasBottomSheet) {
            mHeaderPaint.setColor(bottomSheetBgColor);
            mHeaderPaint.setAlpha((int) (bottomSheetBgAlpha * 255));
            mTmpRectF.set(leftWithScale, topWithScale, rightWithScale, bottomWithOffset);
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
            if (hokoBlur != null && !hokoBlur.isRecycled()) {
                // Blur mode: clip to the sheet's rounded-rect, draw the blurred
                // wallpaper bitmap, then draw the user's background-color as a
                // semi-transparent overlay.  bottomSheetBgAlpha encodes the
                // drawerOpacity preference, so lowering opacity reveals more blur.
                canvas.save();
                canvas.clipPath(mTmpPath);
                canvas.drawBitmap(hokoBlur, 0f, 0f, null);
                canvas.restore();
                canvas.drawPath(mTmpPath, mHeaderPaint); // tinted overlay
            } else {
                // No blur — fall back to solid color.
                canvas.drawPath(mTmpPath, mHeaderPaint);
            }
            // LC-Note: Flags.allAppsBlur() early-return removed; always draw header protection.
        }

        if (DEBUG_HEADER_PROTECTION) {
            mHeaderPaint.setColor(Color.MAGENTA);
            mHeaderPaint.setAlpha(255);
        } else {
            mHeaderPaint.setColor(mHeaderColor);
            mHeaderPaint.setAlpha((int) (getAlpha() * Color.alpha(mHeaderColor)));
        }

        int headerNoAlpha = ColorUtils.setAlphaComponent(mHeaderPaint.getColor(), 0);
        int bgNoAlpha = ColorUtils.setAlphaComponent(getBackgroundColor(), 0);
        if (headerNoAlpha == bgNoAlpha || mHeaderPaint.getColor() == 0) return;

        if (hasBottomSheet) {
            mHeaderPaint.setAlpha((int) (mHeaderPaint.getAlpha() * bottomSheetBgAlpha));
        }

        final float headerBottomNoScale =
                getHeaderBottom() + getVisibleContainerView().getPaddingTop();
        final float headerHeightNoScale = headerBottomNoScale - topNoScale;
        final float headerBottomTablet = topWithScale + headerHeightNoScale * scale;
        final float headerBottomOffset = (getVisibleContainerView().getHeight() * (1 - scale) / 2);
        final float headerBottomPhone = headerBottomNoScale * scale + headerBottomOffset;
        final FloatingHeaderView headerView = getFloatingHeaderView();

        if (hasBottomSheet) {
            if (!isSearchBarFloating() || mUsingTabs) {
                mTmpRectF.set(leftWithScale, topWithScale, rightWithScale, headerBottomTablet);
                mTmpPath.reset();
                mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
                canvas.drawPath(mTmpPath, mHeaderPaint);
            }
        } else {
            canvas.drawRect(0, 0, canvas.getWidth(), headerBottomPhone, mHeaderPaint);
        }

        final int tabsHeight = headerView.getPeripheralProtectionHeight(false);
        if (mTabsProtectionAlpha > 0 && tabsHeight != 0) {
            if (DEBUG_HEADER_PROTECTION) {
                mHeaderPaint.setColor(Color.BLUE);
                mHeaderPaint.setAlpha(255);
            } else {
                float tabAlpha = getAlpha() * mTabsProtectionAlpha;
                if (hasBottomSheet) tabAlpha *= bottomSheetBgAlpha;
                mHeaderPaint.setAlpha((int) tabAlpha);
            }
            left = hasBottomSheet ? leftWithScale : 0f;
            right = hasBottomSheet ? rightWithScale : canvas.getWidth();
            final float tabTop = hasBottomSheet ? headerBottomTablet : headerBottomPhone;
            canvas.drawRect(left, tabTop, right, tabTop + tabsHeight * scale, mHeaderPaint);
        }
    }

    float getHeaderProtectionHeight() {
        float headerBottom = getHeaderBottom() - getTranslationY();
        return mUsingTabs
                ? headerBottom + mHeader.getPeripheralProtectionHeight(true)
                : headerBottom;
    }

    ConstraintLayout getFastScrollerLetterList() { return mFastScrollLetterLayout; }

    public void invalidateHeader() {
        if (mScrimView != null) mScrimView.invalidate();
    }

    public int getHeaderBottom() {
        int bottom = (int) getTranslationY() + mHeader.getClipTop();
        if (isSearchBarFloating()) {
            if (mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
                return bottom + mBottomSheetBackground.getTop();
            }
            return bottom;
        }
        return bottom + mHeader.getTop();
    }

    boolean isUsingTabs() { return mUsingTabs; }

    public View getVisibleContainerView() {
        return mBottomSheetBackground.getVisibility() == VISIBLE ? mBottomSheetBackground : this;
    }

    protected void onInitializeRecyclerView(RecyclerView rv) {
        rv.addOnScrollListener(mScrollListener);
        mSearchUiDelegate.onInitializeRecyclerView(rv);
    }

    public SearchTransitionController getSearchTransitionController() {
        return mSearchTransitionController;
    }

    // -----------------------------------------------------------------------
    // HokoBlur drawer background
    // -----------------------------------------------------------------------

    /**
     * Computes a HokoBlur blurred-wallpaper bitmap and stores it in
     * {@link #mBlurBitmap}.  Works in both full-screen phone mode and
     * bottom-sheet mode.
     *
     * <p>In phone mode the bitmap is drawn as a full-screen background.
     * In sheet mode it is clipped to the rounded-rect sheet boundary with
     * the user's background-color preference drawn on top as a tinted overlay
     * (lower drawer opacity = more blur visible through the overlay).
     *
     * <p>Bitmap computation runs on {@code UI_HELPER_EXECUTOR} (background
     * thread).  The result is posted back to the UI thread where
     * {@link #mBlurBitmap} is updated and {@link #invalidateHeader()} is called
     * so ScrimView redraws and picks up the new value on the next frame.
     */
    private void applyDrawerHokoBlur() {
        boolean blurEnabled = PreferenceExtensionsKt.firstBlocking(pref2.getDrawerBlurBackground());
        boolean isSheet = mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet();

        // TAG matches DrawerWallpaperBlurHelper — one filter covers the whole flow:
        //   adb logcat -s DrawerWallpaperBlur
        android.util.Log.e("DrawerWallpaperBlur",
                "applyDrawerHokoBlur: blurEnabled=" + blurEnabled
                + " isSheet=" + isSheet
                + " bitmapAlreadySet=" + (mBlurBitmap != null));

        if (!blurEnabled) {
            android.util.Log.e("DrawerWallpaperBlur",
                    "applyDrawerHokoBlur: EARLY RETURN — blurEnabled=false");
            if (mBlurBitmap != null) {
                mBlurBitmap = null;
                invalidateHeader();
            }
            return;
        }

        int intensity = Math.round(
                PreferenceExtensionsKt.firstBlocking(pref2.getDrawerBlurIntensity()));
        android.util.Log.e("DrawerWallpaperBlur",
                "applyDrawerHokoBlur: dispatching blur, intensity=" + intensity);
        final Context ctx = getContext();

        UI_HELPER_EXECUTOR.execute(() -> {
            android.util.Log.e("DrawerWallpaperBlur",
                    "applyDrawerHokoBlur: background thread running");
            Bitmap blurred = DrawerWallpaperBlurHelper.getBlurredBitmap(ctx, intensity);
            android.util.Log.e("DrawerWallpaperBlur",
                    "applyDrawerHokoBlur: getBlurredBitmap returned "
                    + (blurred != null
                            ? blurred.getWidth() + "x" + blurred.getHeight()
                            : "null (permission denied or live wallpaper — blur disabled)"));
            MAIN_EXECUTOR.getHandler().post(() -> {
                mBlurBitmap = blurred; // null → no blur; non-null → blur shown
                android.util.Log.e("DrawerWallpaperBlur",
                        "applyDrawerHokoBlur: mBlurBitmap updated on main thread, calling invalidateHeader");
                invalidateHeader();
            });
        });
    }

    // -----------------------------------------------------------------------

    /** Holds a {@link BaseAllAppsAdapter} and related fields. */
    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;
        public static final int SEARCH = 2;

        private final int mType;
        public final BaseAllAppsAdapter<T> mAdapter;
        final RecyclerView.LayoutManager mLayoutManager;
        final AlphabeticalAppsList<T> mAppsList;
        final Rect mPadding = new Rect();
        AllAppsRecyclerView mRecyclerView;
        private OnFocusChangeListener mOnFocusChangeListener;

        AdapterHolder(int type, AlphabeticalAppsList<T> appsList) {
            mType = type;
            mAppsList = appsList;
            mAdapter = createAdapter(mAppsList);
            mAppsList.setAdapter(mAdapter);
            mLayoutManager = mAdapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable Predicate<ItemInfo> matcher) {
            mAppsList.updateItemFilter(matcher);
            mRecyclerView = (AllAppsRecyclerView) rv;
            mRecyclerView.bindFastScrollbar(mFastScroller, ALL_APPS_SCROLLER);
            mRecyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            mRecyclerView.setApps(mAppsList);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setHasFixedSize(true);
            mRecyclerView.setItemAnimator(null);
            onInitializeRecyclerView(mRecyclerView);
            FocusedItemDecorator focusedItemDecorator = isSearch()
                    ? new FocusedItemDecorator(new ViewGroupFocusHelper(mRecyclerView))
                    : new FocusedItemDecorator(mRecyclerView);
            mRecyclerView.addItemDecoration(focusedItemDecorator);
            if (isSearch()) {
                RecyclerView.ItemDecoration searchDecorator =
                        getMainAdapterProvider().getDecorator();
                if (searchDecorator != null) mRecyclerView.addItemDecoration(searchDecorator);
            }
            mOnFocusChangeListener = focusedItemDecorator.getFocusListener();
            mAdapter.setIconFocusListener(mOnFocusChangeListener);
            applyPadding();
        }

        void applyPadding() {
            if (mRecyclerView != null) {
                int bottomOffset = 0;
                if (isWork() && mWorkManager.getWorkUtilityView() != null) {
                    bottomOffset = mInsets.bottom + mWorkManager.getWorkUtilityView().getHeight();
                } else if (isMain() && mPrivateProfileManager != null) {
                    Optional<AdapterItem> psHeader = mAppsList.getAdapterItems().stream()
                            .filter(item -> item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER)
                            .findFirst();
                    if (psHeader.isPresent()) bottomOffset = mPrivateSpaceBottomExtraSpace;
                }
                if (isSearchBarFloating()) bottomOffset += mSearchContainer.getHeight();
                mRecyclerView.setPadding(mPadding.left, mPadding.top, mPadding.right,
                        mPadding.bottom + bottomOffset);
            }
        }

        private boolean isWork()   { return mType == WORK; }
        private boolean isSearch() { return mType == SEARCH; }
        private boolean isMain()   { return mType == MAIN; }
    }
}
