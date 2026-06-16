package com.android.launcher3.pm;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.WorkspaceItemInfo;

/**
 * Restores legacy {@code ACTION_CREATE_SHORTCUT} result parsing that was removed from AOSP
 * Launcher3 in Android 14 (commit 84b48d8).
 *
 * <p>Before Android 8.0 (API 26 / Oreo), apps that implemented {@code ACTION_CREATE_SHORTCUT}
 * returned the shortcut data as plain {@code Intent.EXTRA_SHORTCUT_*} extras inside the result
 * {@code Intent}. Android 8.0 introduced {@link android.content.pm.ShortcutManager} and the
 * {@link android.content.pm.LauncherApps.PinItemRequest} API. AOSP Launcher3 dropped the old
 * parsing path on the assumption that every app that declares the shortcut-config activity now
 * targets API 26+. That assumption is wrong in practice: many widely-used apps still return the
 * legacy extras format regardless of their {@code targetSdkVersion}, including:
 * <ul>
 *   <li>Android Settings shortcut widgets</li>
 *   <li>Shortcut Maker</li>
 *   <li>Chrome "Add to home screen" deep links</li>
 *   <li>Any third-party shortcut-creator app not yet updated</li>
 * </ul>
 *
 * <p>This helper re-implements the old parsing logic so that Lawnchair can accept both the modern
 * {@code PinItemRequest} path (handled by {@link PinRequestHelper}) and the legacy extras path.
 *
 * <h3>Legacy Intent extras</h3>
 * <ul>
 *   <li>{@link Intent#EXTRA_SHORTCUT_NAME} — the display title (String)</li>
 *   <li>{@link Intent#EXTRA_SHORTCUT_INTENT} — the {@link Intent} to launch</li>
 *   <li>{@link Intent#EXTRA_SHORTCUT_ICON_RESOURCE} — a {@link Intent.ShortcutIconResource}
 *       pointing to a drawable resource inside the shortcut's package</li>
 *   <li>{@link Intent#EXTRA_SHORTCUT_ICON} — a raw {@link Bitmap} (used when the icon is
 *       dynamically generated rather than a static resource)</li>
 * </ul>
 */
public final class LegacyShortcutHelper {

    private static final String TAG = "LegacyShortcutHelper";

    private LegacyShortcutHelper() {}

    /**
     * Attempts to build a {@link WorkspaceItemInfo} from the legacy
     * {@code Intent.EXTRA_SHORTCUT_*} extras present in a result {@code Intent} returned by an
     * {@code ACTION_CREATE_SHORTCUT} activity.
     *
     * <p>This method runs icon loading synchronously; callers must not invoke it on the main
     * thread when performance is a concern. However, since it is only called from
     * {@code Launcher.completeAddShortcut()} which already runs on the UI thread right after an
     * {@code onActivityResult} callback, the icon bitmap loading is kept synchronous for
     * simplicity — matching the behaviour of the old {@code InstallShortcutReceiver}.
     *
     * @param context  An application or activity {@link Context}.
     * @param data     The result {@link Intent} received in {@code onActivityResult}.
     * @return A fully-populated {@link WorkspaceItemInfo}, or {@code null} if {@code data} does
     *         not contain the required legacy extras (title + launch intent).
     */
    @Nullable
    public static WorkspaceItemInfo createWorkspaceItemFromLegacyIntent(
            Context context, Intent data) {

        // --- 1. Extract the mandatory extras ---

        // The display title is required.
        String title = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (TextUtils.isEmpty(title)) {
            Log.w(TAG, "Legacy shortcut result is missing EXTRA_SHORTCUT_NAME — ignoring.");
            return null;
        }

        // The launch intent is required.
        Intent launchIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (launchIntent == null) {
            Log.w(TAG, "Legacy shortcut result is missing EXTRA_SHORTCUT_INTENT — ignoring.");
            return null;
        }

        // --- 2. Build the WorkspaceItemInfo ---

        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.title = title;
        info.intent = launchIntent;
        // Legacy shortcuts are stored as ITEM_TYPE_APPLICATION so they survive DB migration
        // (ITEM_TYPE_SHORTCUT = 1 is deprecated and stripped in onUpgrade case 31).
        // We leave itemType at its default (ITEM_TYPE_APPLICATION = 0) which is set by the
        // no-arg WorkspaceItemInfo() constructor.
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user = Process.myUserHandle();
        info.contentDescription = title;

        // --- 3. Load the icon ---

        BitmapInfo iconBitmapInfo = loadLegacyIcon(context, data);
        info.bitmap = iconBitmapInfo;

        Log.d(TAG, "Created legacy WorkspaceItemInfo: title=\"" + title
                + "\", intent=" + launchIntent.toUri(0));
        return info;
    }

    /**
     * Loads the icon for a legacy shortcut by trying, in order:
     * <ol>
     *   <li>A raw {@link Bitmap} from {@link Intent#EXTRA_SHORTCUT_ICON}</li>
     *   <li>A {@link Intent.ShortcutIconResource} from
     *       {@link Intent#EXTRA_SHORTCUT_ICON_RESOURCE}</li>
     *   <li>{@link BitmapInfo#LOW_RES_INFO} as a final fallback (icon will be loaded later)</li>
     * </ol>
     *
     * <p>The returned {@link BitmapInfo} is created via {@link LauncherIcons} so that it goes
     * through the same badging / sizing pipeline as every other icon in the launcher.
     */
    @Nullable
    private static BitmapInfo loadLegacyIcon(Context context, Intent data) {
        // --- 3a. Try raw Bitmap first ---
        Bitmap rawBitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        if (rawBitmap != null) {
            return bitmapToBitmapInfo(context, rawBitmap);
        }

        // --- 3b. Try ShortcutIconResource ---
        Intent.ShortcutIconResource iconResource =
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
        if (iconResource != null) {
            Bitmap resourceBitmap = loadBitmapFromResource(context, iconResource);
            if (resourceBitmap != null) {
                return bitmapToBitmapInfo(context, resourceBitmap);
            }
        }

        // --- 3c. Fallback: LOW_RES placeholder ---
        Log.w(TAG, "Legacy shortcut has no usable icon — using LOW_RES_INFO placeholder.");
        return BitmapInfo.LOW_RES_INFO;
    }

    /**
     * Converts a raw {@link Bitmap} into a properly-badged {@link BitmapInfo} using the
     * launcher's own {@link LauncherIcons} factory.
     */
    private static BitmapInfo bitmapToBitmapInfo(Context context, Bitmap bitmap) {
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            return li.createIconBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create BitmapInfo from legacy shortcut bitmap", e);
            return BitmapInfo.LOW_RES_INFO;
        }
    }

    /**
     * Loads a {@link Bitmap} from a {@link Intent.ShortcutIconResource} by resolving the
     * package resources and decoding the drawable at the given resource ID.
     *
     * <p>Returns {@code null} on any error (package not found, resource ID invalid, etc.).
     */
    @Nullable
    private static Bitmap loadBitmapFromResource(
            Context context, Intent.ShortcutIconResource iconResource) {
        try {
            PackageManager pm = context.getPackageManager();
            Resources packageResources = pm.getResourcesForApplication(iconResource.packageName);
            int resId = packageResources.getIdentifier(
                    iconResource.resourceName, null, null);
            if (resId == 0) {
                Log.w(TAG, "Could not find resource: " + iconResource.resourceName
                        + " in " + iconResource.packageName);
                return null;
            }
            Drawable drawable = packageResources.getDrawableForDensity(
                    resId,
                    context.getResources().getDisplayMetrics().densityDpi,
                    null /* theme */);
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
            // For non-bitmap drawables, render them to a Bitmap
            if (drawable != null) {
                int size = Math.max(drawable.getIntrinsicWidth(), 1);
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                        size, Math.max(drawable.getIntrinsicHeight(), 1),
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
                drawable.setBounds(0, 0, size, Math.max(drawable.getIntrinsicHeight(), 1));
                drawable.draw(canvas);
                return bmp;
            }
            return null;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found for legacy shortcut icon: " + iconResource.packageName);
            return null;
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Resource not found for legacy shortcut icon: " + iconResource.resourceName);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading legacy shortcut icon resource", e);
            return null;
        }
    }
}
