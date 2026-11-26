/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.RemoteViews;

import androidx.annotation.UiThread;

import com.android.launcher3.R;
import com.android.launcher3.util.Executors;

/**
 * Launcher AppWidgetHostView with support for rounded corners and a fallback View.
 */
public abstract class BaseLauncherAppWidgetHostView extends NavigableAppWidgetHostView implements LocalColorExtractor.Listener {

    private static final ViewOutlineProvider VIEW_OUTLINE_PROVIDER = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            // Since ShortcutAndWidgetContainer sets clipChildren to false, we should restrict the
            // outline to be the view bounds, otherwise widgets might draw themselves outside of
            // the launcher view. Setting alpha to 0 to match the previous behavior.
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
            outline.setAlpha(.0f);
        }
    };

    protected final LayoutInflater mInflater;

    private final Rect mEnforcedRectangle = new Rect();
    private float mEnforcedCornerRadius;
    private final ViewOutlineProvider mCornerRadiusEnforcementOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            if (mEnforcedRectangle.isEmpty() || mEnforcedCornerRadius <= 0) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
                outline.setAlpha(.0f);
            } else {
                outline.setRoundRect(mEnforcedRectangle, mEnforcedCornerRadius);
            }
        }
    };

    private boolean mIsCornerRadiusEnforced;
    
    // Lawnchair: LocalColorExtractor
    private final LocalColorExtractor mColorExtractor;

    public BaseLauncherAppWidgetHostView(Context context) {
        super(context);

        setExecutor(Executors.THREAD_POOL_EXECUTOR);
        setClipToOutline(true);

        mInflater = LayoutInflater.from(context);
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(getContext());
        mColorExtractor = LocalColorExtractor.newInstance(getContext());
    }

    @Override
    protected View getErrorView() {
        return mInflater.inflate(R.layout.appwidget_error, this, false);
    }

    /**
     * Fall back to error layout instead of showing widget.
     */
    public void switchToErrorView() {
        AppWidgetProviderInfo info = getAppWidgetInfo();
        if (info != null && info.provider != null) {
            // Update the widget with 0 Layout id, to reset the view to error view.
            updateAppWidget(new RemoteViews(info.provider.getPackageName(), 0));
        } else {
            removeAllViews();
            addView(getErrorView());
        }
    }

    @Override
    public void updateAppWidget(RemoteViews remoteViews) {
        super.updateAppWidget(remoteViews);
        // RemoteViews updates can modify child visibility/layout in place without relayouting
        // the host view; refresh rounded corner bounds after the update settles.
        post(this::enforceRoundedCorners);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mColorExtractor.setListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mColorExtractor.setListener(null);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            super.onLayout(changed, left, top, right, bottom);
        } catch (final RuntimeException e) {
            post(this::switchToErrorView);
            return;
        }

        enforceRoundedCorners();
    }

    @UiThread
    private void resetRoundedCorners() {
        setOutlineProvider(VIEW_OUTLINE_PROVIDER);
        mIsCornerRadiusEnforced = false;
        invalidateOutline();
    }

    @UiThread
    public void enforceRoundedCorners() {
        mEnforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(getContext());
        if (mEnforcedCornerRadius <= 0) {
            resetRoundedCorners();
            return;
        }
        View background = RoundedCornerEnforcement.findBackground(this);
        if (background == null
                || RoundedCornerEnforcement.hasAppWidgetOptedOut(this, background)) {
            resetRoundedCorners();
            return;
        }
        RoundedCornerEnforcement.computeRoundedRectangle(this,
                background,
                mEnforcedRectangle);
        if (mEnforcedRectangle.isEmpty()) {
            resetRoundedCorners();
            return;
        }
        setOutlineProvider(mCornerRadiusEnforcementOutline);
        mIsCornerRadiusEnforced = true;
        invalidateOutline();
    }

    /** Returns the corner radius currently enforced, in pixels. */
    public float getEnforcedCornerRadius() {
        return mEnforcedCornerRadius;
    }

    /** Returns true if the corner radius are enforced for this App Widget. */
    public boolean hasEnforcedCornerRadius() {
        return mIsCornerRadiusEnforced;
    }
}
