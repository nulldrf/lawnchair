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
package com.android.launcher3.taskbar.allapps;

import static android.os.Trace.TRACE_TAG_APP;

import static com.android.app.animation.Interpolators.DECELERATED_EASE;
import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.touch.AllAppsSwipeController.ALL_APPS_FADE_MANUAL;
import static com.android.launcher3.touch.AllAppsSwipeController.SCRIM_FADE_MANUAL;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.Nullable;

import app.lawnchair.theme.color.tokens.ColorTokens;
import app.lawnchair.util.LawnchairUtilsKt;
import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsViewController.TaskbarAllAppsCallbacks;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.views.AbstractSlideInView;

import java.util.function.Consumer;

/** Wrapper for taskbar all apps with slide-in behaviour. */
public class TaskbarAllAppsSlideInView extends AbstractSlideInView<TaskbarOverlayContext>
        implements Insettable, DeviceProfile.OnDeviceProfileChangeListener {

    private static final String TAG = "TaskbarAllAppsSlideInView";

    private final Handler mHandler;
    // LC-Note: System blur (mMaxBlurRadius / mBlurRadius / CrossWindowBlurListeners) removed.
    // The overlay controller is told 0 so the system blur is disabled.
    private final Consumer<Boolean> mWindowBlurListener = blursEnabled -> invalidate();

    private TaskbarAllAppsContainerView mAppsView;
    private float mShiftRange;
    private @Nullable Runnable mShowOnFullyAttachedToWindowRunnable;

    private TaskbarAllAppsCallbacks mAllAppsCallbacks;

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsSlideInView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHandler = new Handler(Looper.myLooper());
    }

    void init(TaskbarAllAppsCallbacks callbacks) {
        mAllAppsCallbacks = callbacks;
    }

    /** Opens the all apps view. */
    void show(boolean animate) {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                removeOnAttachStateChangeListener(this);
                mShowOnFullyAttachedToWindowRunnable = () -> showOnFullyAttachedToWindow(animate);
                mHandler.post(mShowOnFullyAttachedToWindowRunnable);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                removeOnAttachStateChangeListener(this);
            }
        });
        attachToContainer();
    }

    private void showOnFullyAttachedToWindow(boolean animate) {
        // LC-Note: isAllAppsBackgroundBlurEnabled / notifyRendererOfExpensiveFrame block removed;
        // system cross-window blur has been replaced by HokoBlur.

        mAllAppsCallbacks.onAllAppsTransitionStart(true);
        if (!animate) {
            mAllAppsCallbacks.onAllAppsTransitionEnd(true);
            setTranslationShift(TRANSLATION_SHIFT_OPENED);
            // System blur disabled; tell overlay controller radius = 0.
            mActivityContext.getOverlayController().setBackgroundBlurRadius(0);
            return;
        }

        setUpOpenAnimation(mAllAppsCallbacks.getOpenDuration());
        Animator animator = mOpenCloseAnimation.getAnimationPlayer();
        animator.setInterpolator(EMPHASIZED);
        animator.addListener(AnimatorListeners.forEndCallback(() -> {
            if (mIsOpen) {
                mAllAppsCallbacks.onAllAppsTransitionEnd(true);
            }
        }));
        animator.start();
    }

    @Override
    protected void onOpenCloseAnimationPending(PendingAnimation animation) {
        final boolean isOpening = mToTranslationShift == TRANSLATION_SHIFT_OPENED;

        if (mActivityContext.getDeviceProfile().getDeviceProperties().isPhone()) {
            final Interpolator allAppsFadeInterpolator =
                    isOpening ? ALL_APPS_FADE_MANUAL : Interpolators.reverse(ALL_APPS_FADE_MANUAL);
            animation.setViewAlpha(mAppsView, 1 - mToTranslationShift, allAppsFadeInterpolator);
        }

        // LC-Note: Flags.allAppsBlur() blur-radius animation block removed entirely.
        // System cross-window blur has been replaced by HokoBlur; mBlurRadius stays 0.

        mAllAppsCallbacks.onAllAppsAnimationPending(animation, isOpening);
    }

    @Override
    protected Interpolator getScrimInterpolator() {
        if (mActivityContext.getDeviceProfile().getDeviceProperties().isTablet()) {
            return super.getScrimInterpolator();
        }
        return mToTranslationShift == TRANSLATION_SHIFT_OPENED
                ? SCRIM_FADE_MANUAL
                : Interpolators.reverse(SCRIM_FADE_MANUAL);
    }

    TaskbarAllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mShowOnFullyAttachedToWindowRunnable != null) {
            mHandler.removeCallbacks(mShowOnFullyAttachedToWindowRunnable);
            mShowOnFullyAttachedToWindowRunnable = null;
        }
        if (mIsOpen) {
            mAllAppsCallbacks.onAllAppsTransitionStart(false);
        }
        handleClose(animate, mAllAppsCallbacks.getCloseDuration());
    }

    @Override
    protected void onCloseComplete() {
        mAllAppsCallbacks.onAllAppsTransitionEnd(false);
        super.onCloseComplete();
    }

    @Override
    protected Interpolator getIdleInterpolator() {
        return EMPHASIZED;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASKBAR_ALL_APPS) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAppsView = findViewById(R.id.apps_view);
        if (mActivityContext.getDeviceProfile().getDeviceProperties().isPhone()) {
            mAppsView.setAlpha(0);
        }
        mContent = mAppsView;

        mAppsView.setOnInvalidateHeaderListener(this::invalidate);

        DeviceProfile dp = mActivityContext.getDeviceProfile();
        setShiftRange(dp.allAppsShiftRange);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
        mAppsView.getAppsRecyclerViewContainer().setOutlineProvider(mViewOutlineProvider);
        mAppsView.getAppsRecyclerViewContainer().setClipToOutline(true);
        if (!Utilities.ATLEAST_U) return;
        OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
        if (dispatcher != null) {
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this);
        }
        // LC-Note: CrossWindowBlurListeners removed; system blur replaced by HokoBlur.
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
        mAppsView.getAppsRecyclerViewContainer().setOutlineProvider(null);
        mAppsView.getAppsRecyclerViewContainer().setClipToOutline(false);
        if (!Utilities.ATLEAST_U) return;
        OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
        if (dispatcher != null) {
            dispatcher.unregisterOnBackInvokedCallback(this);
        }
        // LC-Note: CrossWindowBlurListeners removed; system blur replaced by HokoBlur.
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mAppsView.drawOnScrimWithBottomOffset(canvas, getBottomOffsetPx());
        // LC-Note: System blur removed; pass 0 so the overlay controller disables its blur.
        mActivityContext.getOverlayController().setBackgroundBlurRadius(0);
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setTranslationShift(mTranslationShift);
    }

    /**
     * LC-Note: System cross-window blur has been replaced by HokoBlur.
     *
     * <p>Two solid-colour paths remain:
     * <ul>
     *   <li>No sheet (phone) → opaque {@code AllAppsScrimColor}</li>
     *   <li>Sheet (tablet)   → Lawnchair-aware background colour (honours user prefs)</li>
     * </ul>
     */
    @Override
    protected int getScrimColor(Context context) {
        if (!mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            return ColorTokens.AllAppsScrimColor.resolveColor(context);
        }
        // Tablet sheet: use the Lawnchair-aware colour that respects the user's
        // chosen background colour and opacity.
        return LawnchairUtilsKt.getAllAppsBackgroundColor(
                context, ColorTokens.WidgetsPickerScrim.resolveColor(context));
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !mAppsView.shouldContainerScroll(ev)
                    || getTopOpenViewWithType(
                            mActivityContext, TYPE_TOUCH_CONTROLLER_NO_INTERCEPT) != null;
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    @Override
    public void setInsets(Rect insets) {
        mAppsView.setInsets(insets);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        setShiftRange(dp.allAppsShiftRange);
        setTranslationShift(TRANSLATION_SHIFT_OPENED);
        // LC-Note: System blur removed; mBlurRadius = mMaxBlurRadius replaced with 0.
        mActivityContext.getOverlayController().setBackgroundBlurRadius(0);
    }

    private void setShiftRange(float shiftRange) {
        mShiftRange = shiftRange;
    }

    @Override
    protected float getShiftRange() {
        return mShiftRange;
    }

    @Override
    protected boolean isEventOverContent(MotionEvent ev) {
        return getPopupContainer().isEventOverView(mAppsView.getVisibleContainerView(), ev);
    }

    @Override
    public boolean shouldAnimateContentViewInBackSwipe() {
        return mAllAppsCallbacks.canHandleSearchBackInvoked();
    }

    @Override
    protected void onUserSwipeToDismissProgressChanged() {
        super.onUserSwipeToDismissProgressChanged();
        mAppsView.setClipChildren(!mIsDismissInProgress);
        mAppsView.getAppsRecyclerViewContainer().setClipChildren(!mIsDismissInProgress);
    }

    @Override
    public void onBackInvoked() {
        if (mAllAppsCallbacks.handleSearchBackInvoked()) {
            post(this::animateSwipeToDismissProgressToStart);
        } else {
            super.onBackInvoked();
        }
    }
}
