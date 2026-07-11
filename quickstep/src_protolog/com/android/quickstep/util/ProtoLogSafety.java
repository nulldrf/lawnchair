package com.android.quickstep.util;

/**
 * Shared safety wrapper around {@link QuickstepProtoLogGroup#isProtoLogInitialized()}, for use by
 * every {@code *ProtoLogProxy} class (e.g. {@code ActiveGestureProtoLogProxy},
 * {@code StateManagerProtoLogProxy}, {@code OverviewCommandHelperProtoLogProxy}, and any future
 * ones backed by {@link QuickstepProtoLogGroup}).
 * <p>
 * NOTE ON DEVICE COMPATIBILITY: on platform versions/OEM builds where
 * {@code com.android.internal.protolog.common.IProtoLogGroup} doesn't exist (observed on Android 11
 * devices), any reference to it throws {@code NoClassDefFoundError} at runtime. This affects
 * {@code *ProtoLogProxy} classes in two distinct ways, which need two distinct guards:
 * <p>
 * 1. Direct calls to {@code ProtoLog.d(...)}/{@code ProtoLog.e(...)} and direct references to the
 * {@link QuickstepProtoLogGroup} enum constants (e.g. {@code ACTIVE_GESTURE_LOG}) require ART to
 * resolve {@code IProtoLogGroup} just to verify the *calling* method's bytecode -- regardless of
 * whether an `if` guard would have skipped the call at runtime. Every proxy class must keep those
 * calls isolated in a nested holder class (e.g. {@code ProtoLogCalls}) that's only loaded once
 * {@link #isSafe()} has confirmed ProtoLog actually works on this device.
 * <p>
 * 2. {@link QuickstepProtoLogGroup#isProtoLogInitialized()} is itself a static method declared on
 * the enum that {@code implements IProtoLogGroup}. Merely calling it forces that enum class to
 * link, which requires resolving {@code IProtoLogGroup} before the method's own body (which would
 * otherwise safely return false) ever runs. {@link #isSafe()} below wraps that call in a
 * try/catch so the failure is caught instead of crashing the caller, and caches the result so we
 * never need to re-attempt (and never re-trigger the underlying error) again.
 */
public final class ProtoLogSafety {

    private static volatile Boolean sAvailable = null;

    private ProtoLogSafety() {}

    /** Returns whether it's safe to call into {@link QuickstepProtoLogGroup}-backed ProtoLog. */
    public static boolean isSafe() {
        Boolean available = sAvailable;
        if (available == null) {
            try {
                available = QuickstepProtoLogGroup.isProtoLogInitialized();
            } catch (Throwable t) {
                available = false;
            }
            sAvailable = available;
        }
        return available;
    }
}
