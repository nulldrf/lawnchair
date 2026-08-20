/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.CANCEL_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.FINISH_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.INVALID_VELOCITY_ON_SWIPE_UP;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.LAUNCHER_DESTROYED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_DOWN;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_MOVE;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_UP;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.NAVIGATION_MODE_SWITCHED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_CANCEL_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_FINISH_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_SETTLED_ON_END_TARGET;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_START_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.QUICK_SWITCH_FROM_HOME_FAILED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.QUICK_SWITCH_FROM_HOME_FALLBACK;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENTS_ANIMATION_START_PENDING;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENTS_ANIMATION_START_TIMEOUT;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENT_TASKS_MISSING;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.START_RECENTS_ANIMATION;
import static com.android.quickstep.util.QuickstepProtoLogGroup.ACTIVE_GESTURE_LOG;

import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.annotation.RequiresApi;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.common.IProtoLogGroup;
/**
 * Proxy class used for ActiveGestureLog ProtoLog support.
 * <p>
 * This file will have all of its static strings in the
 * {@code ProtoLog.d} calls replaced by dynamic code/strings.
 * <p>
 * When a new ActiveGestureLog entry needs to be added to the codebase (or and existing entry needs
 * to be modified), add it here under a new unique method and make sure the ProtoLog entry matches
 * to avoid confusion.
 * <p>
 * NOTE ON DEVICE COMPATIBILITY: on platform versions/OEM builds where
 * {@code com.android.internal.protolog.common.IProtoLogGroup} doesn't exist (observed on Android 11
 * devices), any reference to it throws {@code NoClassDefFoundError} at runtime. Two separate things
 * in this file guard against that, addressing two separate ways the type gets referenced. See
 * {@link ProtoLogSafety} for the full explanation of both:
 * <p>
 * 1. Every direct reference to {@code ProtoLog.d(...)} / {@code ACTIVE_GESTURE_LOG} lives inside the
 * nested {@link ProtoLogCalls} class below, never directly in the methods on this outer class.
 * <p>
 * 2. The guard on every method uses {@link ProtoLogSafety#isSafe()} rather than calling
 * {@code QuickstepProtoLogGroup.isProtoLogInitialized()} directly.
 */
@RequiresApi(31) // LC-Note: IProtoLogGroup only available to Android 11 Releases 41, or Android 12.0 for us. DO NOT call anything related to this or ProtoLog 
public class ActiveGestureProtoLogProxy {

    public static void logLauncherDestroyed() {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed", LAUNCHER_DESTROYED);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logLauncherDestroyed();
    }

    public static void logAbsSwipeUpHandlerOnRecentsAnimationCanceled() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "AbsSwipeUpHandler.onRecentsAnimationCanceled",
                /* gestureEvent= */ CANCEL_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logAbsSwipeUpHandlerOnRecentsAnimationCanceled();
    }

    public static void logAbsSwipeUpHandlerOnRecentsAnimationFinished() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "RecentsAnimationCallbacks.onAnimationFinished",
                ON_FINISH_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logAbsSwipeUpHandlerOnRecentsAnimationFinished();
    }

    public static void logAbsSwipeUpHandlerCancelCurrentAnimation() {
        ActiveGestureLog.INSTANCE.addLog(
                "AbsSwipeUpHandler.cancelCurrentAnimation",
                ActiveGestureErrorDetector.GestureEvent.CANCEL_CURRENT_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logAbsSwipeUpHandlerCancelCurrentAnimation();
    }

    public static void logAbsSwipeUpHandlerOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.onTasksAppeared: "
                + "force finish recents animation complete; clearing state callback.");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logAbsSwipeUpHandlerOnTasksAppeared();
    }

    public static void logHandOffAnimation() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.handOffAnimation");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logHandOffAnimation();
    }

    public static void logFinishRecentsAnimationOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimationOnTasksAppeared");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logFinishRecentsAnimationOnTasksAppeared();
    }

    public static void logRecentsAnimationCallbacksOnAnimationCancelled() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "RecentsAnimationCallbacks.onAnimationCanceled",
                /* gestureEvent= */ ON_CANCEL_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRecentsAnimationCallbacksOnAnimationCancelled();
    }

    public static void logRecentsAnimationCallbacksOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("RecentsAnimationCallbacks.onTasksAppeared",
                ActiveGestureErrorDetector.GestureEvent.TASK_APPEARED);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRecentsAnimationCallbacksOnTasksAppeared();
    }

    public static void logStartRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "TaskAnimationManager.startRecentsAnimation",
                /* gestureEvent= */ START_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logStartRecentsAnimation();
    }

    public static void logLaunchingSideTaskFailed() {
        ActiveGestureLog.INSTANCE.addLog("Unable to launch side task (no recents)");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logLaunchingSideTaskFailed();
    }

    public static void logContinueRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "continueRecentsAnimation");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logContinueRecentsAnimation();
    }

    public static void logCleanUpRecentsAnimationSkipped() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "cleanUpRecentsAnimation skipped due to wrong callbacks");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCleanUpRecentsAnimationSkipped();
    }

    public static void logCleanUpRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "cleanUpRecentsAnimation");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCleanUpRecentsAnimation();
    }

    public static void logOnInputEventUserLocked(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: user is locked",
                displayId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventUserLocked(displayId);
    }

    public static void logOnInputIgnoringFollowingEvents(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onMotionEvent(displayId=%d): A new gesture has been started, "
                        + "but a previously-requested recents animation hasn't started. "
                        + "Ignoring all following motion events.", displayId),
                RECENTS_ANIMATION_START_PENDING);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputIgnoringFollowingEvents(displayId);
    }

    public static void logOnInputEventThreeButtonNav(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "using 3-button nav and event is not a trackpad event", displayId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventThreeButtonNav(displayId);
    }

    public static void logPreloadRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog("preloadRecentsAnimation");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logPreloadRecentsAnimation();
    }

    public static void logRecentTasksMissing() {
        ActiveGestureLog.INSTANCE.addLog("Null mRecentTasks", RECENT_TASKS_MISSING);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRecentTasksMissing();
    }

    public static void logFinishRecentsAnimationCallback() {
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation-callback");
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logFinishRecentsAnimationCallback();
    }

    public static void logOnScrollerAnimationAborted() {
        ActiveGestureLog.INSTANCE.addLog("scroller animation aborted",
                ActiveGestureErrorDetector.GestureEvent.SCROLLER_ANIMATION_ABORTED);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnScrollerAnimationAborted();
    }

    public static void logInputConsumerBecameActive(@NonNull String consumerName) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "%s became active", consumerName));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logInputConsumerBecameActive(consumerName);
    }

    public static void logTaskLaunchFailed(int launchedTaskId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launch failed, task (id=%d) finished mid transition", launchedTaskId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logTaskLaunchFailed(launchedTaskId);
    }

    public static void logOnPageEndTransition(int nextPageIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onPageEndTransition: current page index updated: %d", nextPageIndex));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnPageEndTransition(nextPageIndex);
    }

    public static void logQuickSwitchFromHomeFallback(int taskIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Quick switch from home fallback case: The TaskView at index %d is missing.",
                        taskIndex),
                QUICK_SWITCH_FROM_HOME_FALLBACK);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logQuickSwitchFromHomeFallback(taskIndex);
    }

    public static void logQuickSwitchFromHomeFailed(int taskIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Quick switch from home failed: TaskViews at indices %d and 0 are missing.",
                        taskIndex),
                QUICK_SWITCH_FROM_HOME_FAILED);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logQuickSwitchFromHomeFailed(taskIndex);
    }

    public static void logFinishRecentsAnimation(boolean toRecents) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "finishRecentsAnimation: %b", toRecents),
                /* gestureEvent= */ FINISH_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logFinishRecentsAnimation(toRecents);
    }

    public static void logSetEndTarget(@NonNull String target) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "setEndTarget %s", target), /* gestureEvent= */ SET_END_TARGET);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSetEndTarget(target);
    }

    public static void logStartHomeIntent(@NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "OverviewComponentObserver.startHomeIntent: %s", reason));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logStartHomeIntent(reason);
    }

    public static void logRunningTaskPackage(@NonNull String packageName) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Current running task package name=%s", packageName));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRunningTaskPackage(packageName);
    }

    public static void logSysuiStateFlags(@NonNull String stateFlags) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Current SystemUi state flags=%s", stateFlags));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSysuiStateFlags(stateFlags);
    }

    public static void logSetInputConsumer(@NonNull String consumerName, @NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "setInputConsumer: %s. reason(s):%s", consumerName, reason));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSetInputConsumer(consumerName, reason);
    }

    public static void logUpdateGestureStateRunningTask(
            @NonNull String otherTaskPackage, @NonNull String runningTaskPackage) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Changing active task to %s because the previous task running on top of this "
                        + "one (%s) was excluded from recents",
                otherTaskPackage,
                runningTaskPackage));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logUpdateGestureStateRunningTask(otherTaskPackage, runningTaskPackage);
    }

    public static void logOnInputEventActionUp(
            int x, int y, int action, @NonNull String classification, int displayId) {
        String actionString = MotionEvent.actionToString(action);
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onMotionEvent(%d, %d): %s, %s, displayId=%d",
                        x,
                        y,
                        actionString,
                        classification,
                        displayId),
                /* gestureEvent= */ action == ACTION_DOWN
                        ? MOTION_DOWN
                        : MOTION_UP);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventActionUp(x, y, actionString, classification, displayId);
    }

    public static void logOnInputEventActionMove(
            @NonNull String action,
            @NonNull String classification,
            int pointerCount,
            int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "onMotionEvent: %s, %s, pointerCount: %d, displayId=%d",
                        action,
                        classification,
                        pointerCount,
                        displayId),
                MOTION_MOVE);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventActionMove(action, classification, pointerCount, displayId);
    }

    public static void logOnInputEventGenericAction(
            @NonNull String action, @NonNull String classification, int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onMotionEvent: %s, %s, displayId=%d", action, classification, displayId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventGenericAction(action, classification, displayId);
    }

    public static void logOnInputEventNavModeSwitched(
            int displayId, @NonNull String startNavMode, @NonNull String currentNavMode) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s -> %s); "
                        + "cancelling gesture.",
                        displayId,
                        startNavMode,
                        currentNavMode),
                NAVIGATION_MODE_SWITCHED);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventNavModeSwitched(displayId, startNavMode, currentNavMode);
    }

    public static void logUnknownInputEvent(int displayId, @NonNull String event) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "received unknown event %s", displayId, event));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logUnknownInputEvent(displayId, event);
    }

    public static void logFinishRunningRecentsAnimation(boolean toHome) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "finishRunningRecentsAnimation: %b", toHome));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logFinishRunningRecentsAnimation(toHome);
    }

    public static void logOnRecentsAnimationStartCancelled() {
        ActiveGestureLog.INSTANCE.addLog("RecentsAnimationCallbacks.onAnimationStart (canceled): 0",
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnRecentsAnimationStartCancelled();
    }

    public static void logOnRecentsAnimationStart(int appCount) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "RecentsAnimationCallbacks.onAnimationStart: %d", appCount),
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnRecentsAnimationStart(appCount);
    }

    public static void logStartRecentsAnimationCallback(@NonNull String callback) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.startRecentsAnimation(%s): "
                        + "Setting mRecentsAnimationStartPending = false",
                callback));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logStartRecentsAnimationCallback(callback);
    }

    public static void logSettingRecentsAnimationStartPending(boolean value) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.startRecentsAnimation: "
                        + "Setting mRecentsAnimationStartPending = %b",
                value));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSettingRecentsAnimationStartPending(value);
    }

    public static void logLaunchingSideTask(int taskId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launching side task id=%d", taskId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logLaunchingSideTask(taskId);
    }

    public static void logOnInputEventActionDown(
            int displayId, @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onMotionEvent(displayId=%d): ", displayId).append(reason));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInputEventActionDown(displayId, reason.toString());
    }

    public static void logStartNewTask(@NonNull ActiveGestureLog.CompoundString tasks) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launching task: ").append(tasks));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logStartNewTask(tasks.toString());
    }

    public static void logMotionPauseDetectorEvent(@NonNull ActiveGestureLog.CompoundString event) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "MotionPauseDetector: ").append(event));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logMotionPauseDetectorEvent(event.toString());
    }

    public static void logHandleTaskAppearedFailed(
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "handleTaskAppeared check failed: ").append(reason));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logHandleTaskAppearedFailed(reason.toString());
    }

    /**
     * This is for special cases where the string is purely dynamic and therefore has no format that
     * can be extracted. Do not use in any other case.
     */
    public static void logDynamicString(
            @NonNull String string,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        ActiveGestureLog.INSTANCE.addLog(string, gestureEvent);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logDynamicString(string);
    }

    public static void logOnSettledOnEndTarget(@NonNull String endTarget) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onSettledOnEndTarget %s", endTarget),
                /* gestureEvent= */ ON_SETTLED_ON_END_TARGET);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnSettledOnEndTarget(endTarget);
    }

    public static void logOnCalculateEndTarget(float velocityX, float velocityY, double angle) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "calculateEndTarget: velocities=(x=%fdp/ms, y=%fdp/ms), angle=%f",
                        velocityX,
                        velocityY,
                        angle),
                velocityX == 0 && velocityY == 0 ? INVALID_VELOCITY_ON_SWIPE_UP : null);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnCalculateEndTarget(velocityX, velocityY, angle);
    }

    public static void logUnexpectedTaskAppeared(int taskId, @NonNull String packageName) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Forcefully finishing recents animation: Unexpected task appeared id=%d, pkg=%s",
                taskId,
                packageName));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logUnexpectedTaskAppeared(taskId, packageName);
    }

    public static void logCreateTouchRegionForDisplay(int displayRotation,
            @NonNull Point displaySize, @NonNull RectF swipeRegion, @NonNull RectF ohmRegion,
            int gesturalHeight, int largerGesturalHeight, @NonNull String reason) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCreateTouchRegionForDisplay(displayRotation, displaySize.flattenToString(),
                swipeRegion.toShortString(), ohmRegion.toShortString(), gesturalHeight,
                largerGesturalHeight, reason);
    }

    public static void logOnTaskAnimationManagerNotAvailable(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager not available for displayId=%d",
                displayId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnTaskAnimationManagerNotAvailable(displayId);
    }

    public static void logOnAbsSwipeUpHandlerNotAvailable(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "AbsSwipeUpHandler not available for displayId=%d",
                displayId));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnAbsSwipeUpHandlerNotAvailable(displayId);
    }

    public static void logGestureStartSwipeHandler(@NonNull String interactionHandler) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "OtherActivityInputConsumer.startTouchTrackingForWindowAnimation: "
                        + "interactionHandler=%s", interactionHandler));
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logGestureStartSwipeHandler(interactionHandler);
    }

    public static void logQueuingForceFinishRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed while mRecentsAnimationStartPending =="
                        + " true, queuing a callback to clean the pending animation up on start",
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logQueuingForceFinishRecentsAnimation();
    }

    public static void logRecentsAnimationStartTimedOut() {
        ActiveGestureLog.INSTANCE.addLog("Recents animation start has timed out; forcefully "
                        + "cleaning up the recents animation.",
                /* gestureEvent= */ RECENTS_ANIMATION_START_TIMEOUT);
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRecentsAnimationStartTimedOut();
    }

    /**
     * Holder for every direct {@code ProtoLog.d(...)} call. See the class-level note above for
     * why this needs to be a separate class rather than inline in the methods above.
     */
    private static class ProtoLogCalls {

        private static void logLauncherDestroyed() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Launcher destroyed");
        }

        private static void logAbsSwipeUpHandlerOnRecentsAnimationCanceled() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.onRecentsAnimationCanceled");
        }

        private static void logAbsSwipeUpHandlerOnRecentsAnimationFinished() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.onAnimationFinished");
        }

        private static void logAbsSwipeUpHandlerCancelCurrentAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.cancelCurrentAnimation");
        }

        private static void logAbsSwipeUpHandlerOnTasksAppeared() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.onTasksAppeared: "
                    + "force finish recents animation complete; clearing state callback.");
        }

        private static void logHandOffAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.handOffAnimation");
        }

        private static void logFinishRecentsAnimationOnTasksAppeared() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRecentsAnimationOnTasksAppeared");
        }

        private static void logRecentsAnimationCallbacksOnAnimationCancelled() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "RecentsAnimationCallbacks.onAnimationCanceled");
        }

        private static void logRecentsAnimationCallbacksOnTasksAppeared() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "RecentsAnimationCallbacks.onTasksAppeared");
        }

        private static void logStartRecentsAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "TaskAnimationManager.startRecentsAnimation");
        }

        private static void logLaunchingSideTaskFailed() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Unable to launch side task (no recents)");
        }

        private static void logContinueRecentsAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "continueRecentsAnimation");
        }

        private static void logCleanUpRecentsAnimationSkipped() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "cleanUpRecentsAnimation skipped due to wrong callbacks");
        }

        private static void logCleanUpRecentsAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "cleanUpRecentsAnimation");
        }

        private static void logOnInputEventUserLocked(int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TIS.onInputEvent(displayId=%d): Cannot process input event: user is locked",
                    displayId);
        }

        private static void logOnInputIgnoringFollowingEvents(int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TIS.onMotionEvent(displayId=%d): A new gesture has been started, "
                            + "but a previously-requested recents animation hasn't started. "
                            + "Ignoring all following motion events.", displayId);
        }

        private static void logOnInputEventThreeButtonNav(int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                            + "using 3-button nav and event is not a trackpad event", displayId);
        }

        private static void logPreloadRecentsAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "preloadRecentsAnimation");
        }

        private static void logRecentTasksMissing() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Null mRecentTasks");
        }

        private static void logFinishRecentsAnimationCallback() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRecentsAnimation-callback");
        }

        private static void logOnScrollerAnimationAborted() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "scroller animation aborted");
        }

        private static void logInputConsumerBecameActive(String consumerName) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "%s became active", consumerName);
        }

        private static void logTaskLaunchFailed(int launchedTaskId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "Launch failed, task (id=%d) finished mid transition", launchedTaskId);
        }

        private static void logOnPageEndTransition(int nextPageIndex) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "onPageEndTransition: current page index updated: %d", nextPageIndex);
        }

        private static void logQuickSwitchFromHomeFallback(int taskIndex) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "Quick switch from home fallback case: The TaskView at index %d is missing.",
                    taskIndex);
        }

        private static void logQuickSwitchFromHomeFailed(int taskIndex) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "Quick switch from home failed: TaskViews at indices %d and 0 are missing.",
                    taskIndex);
        }

        private static void logFinishRecentsAnimation(boolean toRecents) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRecentsAnimation: %b", toRecents);
        }

        private static void logSetEndTarget(String target) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "setEndTarget %s", target);
        }

        private static void logStartHomeIntent(String reason) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "OverviewComponentObserver.startHomeIntent: %s", reason);
        }

        private static void logRunningTaskPackage(String packageName) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Current running task package name=%s", packageName);
        }

        private static void logSysuiStateFlags(String stateFlags) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Current SystemUi state flags=%s", stateFlags);
        }

        private static void logSetInputConsumer(String consumerName, String reason) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "setInputConsumer: %s. reason(s):%s", consumerName, reason);
        }

        private static void logUpdateGestureStateRunningTask(
                String otherTaskPackage, String runningTaskPackage) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "Changing active task to %s because the previous task running on top of this "
                            + "one (%s) was excluded from recents",
                    otherTaskPackage,
                    runningTaskPackage);
        }

        private static void logOnInputEventActionUp(
                int x, int y, String actionString, String classification, int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "onMotionEvent(%d, %d): %s, %s, displayId=%d",
                    x,
                    y,
                    actionString,
                    classification,
                    displayId);
        }

        private static void logOnInputEventActionMove(
                String action, String classification, int pointerCount, int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "onMotionEvent: %s, %s, pointerCount: %d, displayId=%d",
                    action,
                    classification,
                    pointerCount,
                    displayId);
        }

        private static void logOnInputEventGenericAction(
                String action, String classification, int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "onMotionEvent: %s, %s, displayId=%d", action, classification, displayId);
        }

        private static void logOnInputEventNavModeSwitched(
                int displayId, String startNavMode, String currentNavMode) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s -> %s); "
                            + "cancelling gesture.",
                    displayId,
                    startNavMode,
                    currentNavMode);
        }

        private static void logUnknownInputEvent(int displayId, String event) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                            + "received unknown event %s", displayId, event);
        }

        private static void logFinishRunningRecentsAnimation(boolean toHome) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRunningRecentsAnimation: %b", toHome);
        }

        private static void logOnRecentsAnimationStartCancelled() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "RecentsAnimationCallbacks.onAnimationStart (canceled): 0");
        }

        private static void logOnRecentsAnimationStart(int appCount) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "RecentsAnimationCallbacks.onAnimationStart: %d", appCount);
        }

        private static void logStartRecentsAnimationCallback(String callback) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TaskAnimationManager.startRecentsAnimation(%s): "
                            + "Setting mRecentsAnimationStartPending = false",
                    callback);
        }

        private static void logSettingRecentsAnimationStartPending(boolean value) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TaskAnimationManager.startRecentsAnimation: "
                            + "Setting mRecentsAnimationStartPending = %b",
                    value);
        }

        private static void logLaunchingSideTask(int taskId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Launching side task id=%d", taskId);
        }

        private static void logOnInputEventActionDown(int displayId, String reason) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "TIS.onMotionEvent(displayId=%d): %s", displayId, reason);
        }

        private static void logStartNewTask(String tasks) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "TIS.onMotionEvent: %s", tasks);
        }

        private static void logMotionPauseDetectorEvent(String event) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "MotionPauseDetector: %s", event);
        }

        private static void logHandleTaskAppearedFailed(String reason) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "handleTaskAppeared check failed: %s", reason);
        }

        private static void logDynamicString(String string) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "%s", string);
        }

        private static void logOnSettledOnEndTarget(String endTarget) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "onSettledOnEndTarget %s", endTarget);
        }

        private static void logOnCalculateEndTarget(float velocityX, float velocityY, double angle) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "calculateEndTarget: velocities=(x=%fdp/ms, y=%fdp/ms), angle=%f",
                    velocityX,
                    velocityY,
                    angle);
        }

        private static void logUnexpectedTaskAppeared(int taskId, String packageName) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "Forcefully finishing recents animation: Unexpected task appeared id=%d, pkg=%s",
                    taskId,
                    packageName);
        }

        private static void logCreateTouchRegionForDisplay(int displayRotation,
                String displaySize, String swipeRegion, String ohmRegion,
                int gesturalHeight, int largerGesturalHeight, String reason) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "OrientationTouchTransformer.createRegionForDisplay: "
                            + "dispRot=%d, dispSize=%s, swipeRegion=%s, ohmRegion=%s, "
                            + "gesturalHeight=%d, largerGesturalHeight=%d, reason=%s",
                    displayRotation, displaySize, swipeRegion,
                    ohmRegion, gesturalHeight, largerGesturalHeight, reason);
        }

        private static void logOnTaskAnimationManagerNotAvailable(int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "TaskAnimationManager not available for displayId=%d",
                    displayId);
        }

        private static void logOnAbsSwipeUpHandlerNotAvailable(int displayId) {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler not available for displayId=%d",
                    displayId);
        }

        private static void logGestureStartSwipeHandler(String interactionHandler) {
            ProtoLog.d(ACTIVE_GESTURE_LOG,
                    "OtherActivityInputConsumer.startTouchTrackingForWindowAnimation: "
                            + "interactionHandler=%s", interactionHandler);
        }

        private static void logQueuingForceFinishRecentsAnimation() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Launcher destroyed while mRecentsAnimationStartPending =="
                    + " true, queuing a callback to clean the pending animation up on start");
        }

        private static void logRecentsAnimationStartTimedOut() {
            ProtoLog.d(ACTIVE_GESTURE_LOG, "Recents animation start has timed out; forcefully "
                    + "cleaning up the recents animation.");
        }
    }
}
