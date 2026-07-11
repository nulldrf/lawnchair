/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.util;

import static com.android.quickstep.util.QuickstepProtoLogGroup.OVERVIEW_COMMAND_HELPER;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.quickstep.util.ProtoLogSafety;

/**
 * Proxy class used for OverviewCommandHelper ProtoLog support. (e.g. for 3 button nav)
 * <p>
 * See {@link ProtoLogSafety} for why every direct {@code ProtoLog.d(...)}/{@code ProtoLog.e(...)}
 * call below lives inside the nested {@link ProtoLogCalls} class, and why the guard uses
 * {@link ProtoLogSafety#isSafe()} rather than calling
 * {@code QuickstepProtoLogGroup.isProtoLogInitialized()} directly.
 */
public class OverviewCommandHelperProtoLogProxy {

    public static void logCommandQueueFull(@NonNull Object type, @NonNull Object commandQueue) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandQueueFull(type, commandQueue);
    }

    public static void logCommandAdded(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandAdded(command);
    }

    public static void logCommandExecuted(@NonNull Object command, int queueSize) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandExecuted(command, queueSize);
    }

    public static void logCommandNotExecuted(@NonNull Object command, int queueSize) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandNotExecuted(command, queueSize);
    }

    public static void logClearPendingCommands(@NonNull Object commandQueue) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logClearPendingCommands(commandQueue);
    }

    public static void logNoPendingCommands() {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logNoPendingCommands();
    }

    public static void logExecutingCommand(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logExecutingCommand(command);
    }

    public static void logExecutingCommand(@NonNull Object command, @Nullable Object recentsView) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logExecutingCommand(command, recentsView);
    }

    public static void logExecutedCommandWithResult(@NonNull Object command, boolean isCompleted) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logExecutedCommandWithResult(command, isCompleted);
    }

    public static void logWaitingForCommandCallback(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logWaitingForCommandCallback(command);
    }

    public static void logLaunchingTaskCallback(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logLaunchingTaskCallback(command);
    }

    public static void logLaunchingTaskWaitingForCallback(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logLaunchingTaskWaitingForCallback(command);
    }

    public static void logSwitchingToOverviewStateStart(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSwitchingToOverviewStateStart(command);
    }

    public static void logSwitchingToOverviewStateEnd(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSwitchingToOverviewStateEnd(command);
    }

    public static void logSwitchingToOverviewStateWaiting(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSwitchingToOverviewStateWaiting(command);
    }

    public static void logRecentsAnimStarted(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRecentsAnimStarted(command);
    }

    public static void logOnInitBackgroundStateUI(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnInitBackgroundStateUI(command);
    }

    public static void logRecentsAnimCanceled(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logRecentsAnimCanceled(command);
    }

    public static void logSwitchingViaRecentsAnim(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSwitchingViaRecentsAnim(command);
    }

    public static void logSwitchingViaRecentsAnimComplete(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logSwitchingViaRecentsAnimComplete(command);
    }

    public static void logCommandFinishedButNotScheduled(@Nullable Object nextCommandInQueue,
            @NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandFinishedButNotScheduled(nextCommandInQueue, command);
    }

    public static void logCommandFinishedSuccessfully(@NonNull Object command) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandFinishedSuccessfully(command);
    }

    public static void logCommandCanceled(@NonNull Object command, @Nullable Throwable throwable) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logCommandCanceled(command, throwable);
    }

    public static void logOnNewIntent(boolean alreadyOnHome, boolean shouldMoveToDefaultScreen,
            String intentAction, boolean internalStateHandled) {
        if (!ProtoLogSafety.isSafe()) return;
        ProtoLogCalls.logOnNewIntent(
                alreadyOnHome, shouldMoveToDefaultScreen, intentAction, internalStateHandled);
    }

    /**
     * Holder for every direct {@code ProtoLog.d(...)}/{@code ProtoLog.e(...)} call. See the
     * class-level note above (and {@link ProtoLogSafety}) for why this needs to be a separate
     * class rather than inline in the methods above.
     */
    private static class ProtoLogCalls {

        private static void logCommandQueueFull(Object type, Object commandQueue) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "command not added: %s - queue is full (%s).",
                    type,
                    commandQueue);
        }

        private static void logCommandAdded(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "command added: %s", command);
        }

        private static void logCommandExecuted(Object command, int queueSize) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "execute: %s - queue size: %d",
                    command,
                    queueSize);
        }

        private static void logCommandNotExecuted(Object command, int queueSize) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "command not executed: %s - queue size: %d",
                    command,
                    queueSize);
        }

        private static void logClearPendingCommands(Object commandQueue) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "clearing pending commands: %s", commandQueue);
        }

        private static void logNoPendingCommands() {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "no pending commands to be executed.");
        }

        private static void logExecutingCommand(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "executing command: %s", command);
        }

        private static void logExecutingCommand(Object command, Object recentsView) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "executing command: %s - visibleRecentsView: %s",
                    command,
                    recentsView);
        }

        private static void logExecutedCommandWithResult(Object command, boolean isCompleted) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "command executed: %s with result: %b",
                    command,
                    isCompleted);
        }

        private static void logWaitingForCommandCallback(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "waiting for command callback: %s", command);
        }

        private static void logLaunchingTaskCallback(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "launching task callback: %s", command);
        }

        private static void logLaunchingTaskWaitingForCallback(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "launching task - waiting for callback: %s", command);
        }

        private static void logSwitchingToOverviewStateStart(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "switching to Overview state - onAnimationStart: %s", command);
        }

        private static void logSwitchingToOverviewStateEnd(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "switching to Overview state - onAnimationEnd: %s", command);
        }

        private static void logSwitchingToOverviewStateWaiting(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "switching to Overview state - waiting: %s", command);
        }

        private static void logRecentsAnimStarted(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "recents animation started: %s", command);
        }

        private static void logOnInitBackgroundStateUI(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "recents animation started - onInitBackgroundStateUI: %s", command);
        }

        private static void logRecentsAnimCanceled(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "recents animation canceled: %s", command);
        }

        private static void logSwitchingViaRecentsAnim(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "switching via recents animation - onGestureStarted: %s", command);
        }

        private static void logSwitchingViaRecentsAnimComplete(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "switching via recents animation - onTransitionComplete: %s", command);
        }

        private static void logCommandFinishedButNotScheduled(Object nextCommandInQueue,
                Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "next task not scheduled. First pending command type is %s - "
                            + "command type is: %s",
                    nextCommandInQueue,
                    command);
        }

        private static void logCommandFinishedSuccessfully(Object command) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER, "command executed successfully: %s", command);
        }

        private static void logCommandCanceled(Object command, Throwable throwable) {
            ProtoLog.e(OVERVIEW_COMMAND_HELPER, "command canceled: %s - %s", command, throwable);
        }

        private static void logOnNewIntent(boolean alreadyOnHome,
                boolean shouldMoveToDefaultScreen, String intentAction,
                boolean internalStateHandled) {
            ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                    "Launcher.onNewIntent: alreadyOnHome: %b, shouldMoveToDefaultScreen: %b, "
                            + "intentAction: %s, internalStateHandled: %b",
                    alreadyOnHome,
                    shouldMoveToDefaultScreen,
                    intentAction,
                    internalStateHandled);
        }
    }
}
