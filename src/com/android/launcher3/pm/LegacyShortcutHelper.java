package com.android.launcher3.pm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;

/**
 * Restores legacy {@code ACTION_CREATE_SHORTCUT} result parsing that was removed from AOSP
 * Launcher3 in Android 14 (commit 84b48d8).
 *
 * <h3>Background</h3>
 * <p>Before Android 8.0 (API 26 / Oreo), apps implementing {@code ACTION_CREATE_SHORTCUT}
 * returned shortcut data as plain {@code Intent.EXTRA_SHORTCUT_*} extras in the activity result.
 * Android 8.0 introduced {@link android.content.pm.ShortcutManager} and
 * {@link android.content.pm.LauncherApps.PinItemRequest}. AOSP Launcher3 dropped the old parsing
 * path, breaking many real-world apps that still return legacy extras regardless of their
 * {@code targetSdkVersion}, including:
 * <ul>
 *   <li>Android Settings shortcut widgets</li>
 *   <li>Shortcut Maker</li>
 *   <li>Chrome "Add to home screen" deep links</li>
 *   <li>Many third-party shortcut-creator apps</li>
 * </ul>
 *
 * <h3>Icon loading notes</h3>
 * <p>The initial icon display may briefly show a low-resolution placeholder before the
 * high-resolution icon is loaded. This is completely normal Launcher3 behavior — the launcher
 * uses {@link BitmapInfo#LOW_RES_INFO} as a placeholder that triggers background high-res
 * loading via {@link IconCache#updateIconInBackground}. The icon becomes full-res within
 * milliseconds after the icon view is bound.
 *
 * <p>When the result intent contains no icon extras at all, we load the host app's package
 * icon via {@link IconCache#getTitleAndIconForApp} rather than leaving {@code LOW_RES_INFO}
 * permanently, which would cause the icon to stay grey if {@code resolveActivity(intent)}
 * fails for non-main intents (e.g. {@code android.settings.DISPLAY_SETTINGS}).
 */
public final class LegacyShortcutHelper {

    private static final String TAG = "LegacyShortcutHelper";

    private LegacyShortcutHelper() {}

    /**
     * Attempts to build a {@link WorkspaceItemInfo} from legacy {@code Intent.EXTRA_SHORTCUT_*}
     * extras in a result {@code Intent} returned by an {@code ACTION_CREATE_SHORTCUT} activity.
     *
     * <p>Returns {@code null} if the required extras (title + launch intent) are absent, or if
     * no package can be determined for the launch intent (which would cause downstream
     * {@code NullPointerException}s in {@link com.android.launcher3.util.ComponentKey} and
     * related classes).
     *
     * @param context application or activity context
     * @param data    the result {@link Intent} received in {@code onActivityResult}
     * @return a fully-populated {@link WorkspaceItemInfo}, or {@code null} if data is unusable
     */
    @Nullable
    public static WorkspaceItemInfo createWorkspaceItemFromLegacyIntent(
            @NonNull Context context, @NonNull Intent data) {

        // ── 1. Mandatory: title ───────────────────────────────────────────────────────────────
        String title = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (TextUtils.isEmpty(title)) {
            Log.w(TAG, "Legacy shortcut result missing EXTRA_SHORTCUT_NAME — ignored.");
            return null;
        }

        // ── 2. Mandatory: launch intent ───────────────────────────────────────────────────────
        Intent launchIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (launchIntent == null) {
            Log.w(TAG, "Legacy shortcut result missing EXTRA_SHORTCUT_INTENT — ignored.");
            return null;
        }

        // ── 3. Guarantee a non-null package on the launch intent ──────────────────────────────
        //
        // ItemInfo.getTargetComponent() returns intent.getComponent() which may be null.
        // ItemInfo.getTargetPackage()   returns intent.getPackage() as fallback, also may be null.
        //
        // Both ComponentKey and PackageUserKey are constructed from these values throughout the
        // popup, drag, and dot-info paths without null checks, causing NullPointerException if
        // either is null. We ensure launchIntent.getPackage() is always non-null.
        //
        //  (a) ComponentName present  → derive package from it; also set intent.getPackage()
        //  (b) Package string present → already OK, leave as-is
        //  (c) Neither → resolve via PackageManager and set both component and package
        //  (d) Unresolvable           → bail; cannot produce a safe item
        if (launchIntent.getComponent() != null) {
            // (a) ComponentName present — getTargetComponent() will return it directly.
            // Mirror the package string so getTargetPackage() is also consistent.
            if (launchIntent.getPackage() == null) {
                launchIntent.setPackage(launchIntent.getComponent().getPackageName());
            }
        } else if (launchIntent.getPackage() == null) {
            // (c) No component, no explicit package — try resolving the intent.
            android.content.pm.ResolveInfo ri =
                    context.getPackageManager().resolveActivity(launchIntent, 0);
            if (ri != null && ri.activityInfo != null) {
                String pkg   = ri.activityInfo.packageName;
                String cls   = ri.activityInfo.name;
                launchIntent.setComponent(new ComponentName(pkg, cls));
                launchIntent.setPackage(pkg);
                Log.d(TAG, "Resolved legacy shortcut intent to " + pkg + "/" + cls);
            } else {
                // (d) Cannot resolve — abort to prevent downstream NPE.
                Log.w(TAG, "Cannot resolve legacy shortcut intent " + launchIntent.toUri(0)
                        + "; aborting to prevent NullPointerException in ComponentKey.");
                return null;
            }
        }
        // Invariant: launchIntent.getPackage() is now guaranteed non-null.

        // ── 4. Build the WorkspaceItemInfo ────────────────────────────────────────────────────
        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.title              = title;
        info.contentDescription = title;
        info.intent             = launchIntent;
        // ITEM_TYPE_APPLICATION is the correct type for legacy shortcuts after DB migration.
        // ITEM_TYPE_SHORTCUT (1) is deprecated and stripped in DatabaseHelper.onUpgrade case 31.
        info.itemType           = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user               = Process.myUserHandle();

        // ── 5. Load icon ──────────────────────────────────────────────────────────────────────
        //
        // Priority:
        //   i.  Raw Bitmap   from EXTRA_SHORTCUT_ICON
        //   ii. Drawable res from EXTRA_SHORTCUT_ICON_RESOURCE
        //   iii.Package icon from IconCache (for intents with no icon in extras)
        //
        // Options (i) and (ii) produce a full-res BitmapInfo that gets persisted to DB by
        // WorkspaceItemInfo.onAddToDatabase(), so the icon survives launcher restarts.
        //
        // Option (iii) uses LOW_RES_INFO as the initial value so that BubbleTextView triggers
        // IconCache.updateIconInBackground(), which loads the full icon asynchronously. This is
        // the same "low-res placeholder → high-res" behavior used by all app icons in Launcher3
        // and is completely expected.
        info.bitmap = loadLegacyIcon(context, data, launchIntent.getPackage());

        Log.d(TAG, "Created legacy WorkspaceItemInfo: title=\"" + title
                + "\", pkg=" + launchIntent.getPackage()
                + ", component=" + launchIntent.getComponent()
                + ", bitmapIsLowRes=" + info.bitmap.isNullOrLowRes());
        return info;
    }

    // ── Icon loading ──────────────────────────────────────────────────────────────────────────

    /**
     * Loads the icon for the shortcut, trying sources in priority order.
     *
     * @param context     context for resource and icon loading
     * @param data        the result Intent containing optional EXTRA_SHORTCUT_ICON* extras
     * @param packageName the resolved package name of the host app (guaranteed non-null)
     * @return a {@link BitmapInfo} for the icon, never {@code null}
     */
    @NonNull
    private static BitmapInfo loadLegacyIcon(
            @NonNull Context context, @NonNull Intent data, @NonNull String packageName) {

        // (i) Raw Bitmap directly from the result extras
        Bitmap rawBitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        if (rawBitmap != null) {
            BitmapInfo bi = bitmapToBitmapInfo(context, rawBitmap);
            if (!bi.isNullOrLowRes()) return bi;
        }

        // (ii) ShortcutIconResource pointing to a drawable inside the shortcut's package
        Intent.ShortcutIconResource iconResource =
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
        if (iconResource != null) {
            Bitmap resourceBitmap = loadBitmapFromResource(context, iconResource);
            if (resourceBitmap != null) {
                BitmapInfo bi = bitmapToBitmapInfo(context, resourceBitmap);
                if (!bi.isNullOrLowRes()) return bi;
            }
        }

        // (iii) No icon in extras: load the host app's package icon so the shortcut shows
        //       the app's own icon rather than a permanent grey placeholder.
        //       LOW_RES_INFO is set first; IconCache will upgrade it to full-res in background.
        return loadPackageIcon(context, packageName);
    }

    /**
     * Converts a raw {@link Bitmap} into a properly-sized and badged {@link BitmapInfo} using the
     * launcher's {@link LauncherIcons} factory. Returns {@link BitmapInfo#LOW_RES_INFO} on error.
     */
    @NonNull
    private static BitmapInfo bitmapToBitmapInfo(
            @NonNull Context context, @NonNull Bitmap bitmap) {
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            BitmapInfo bi = li.createIconBitmap(bitmap);
            return bi != null ? bi : BitmapInfo.LOW_RES_INFO;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create BitmapInfo from legacy shortcut Bitmap", e);
            return BitmapInfo.LOW_RES_INFO;
        }
    }

    /**
     * Loads the host application's package icon via {@link IconCache#getTitleAndIconForApp}.
     * This ensures that when a shortcut contains no icon in its result extras, the icon displayed
     * is the host app's own icon rather than a permanent grey square.
     *
     * <p>The initial return value is {@link BitmapInfo#LOW_RES_INFO}, which signals to
     * {@link com.android.launcher3.BubbleTextView} that a background high-res icon load is
     * needed. This is the same low-res-then-high-res pipeline used for all regular app icons
     * and is completely expected Launcher3 behavior.
     */
    @NonNull
    private static BitmapInfo loadPackageIcon(
            @NonNull Context context, @NonNull String packageName) {
        try {
            IconCache iconCache = LauncherAppState.getInstance(context).getIconCache();
            PackageItemInfo pkgInfo = new PackageItemInfo(packageName, Process.myUserHandle());
            iconCache.getTitleAndIconForApp(pkgInfo, DEFAULT_LOOKUP_FLAG);
            if (pkgInfo.bitmap != null && !pkgInfo.bitmap.isNullOrLowRes()) {
                Log.d(TAG, "Loaded package icon for legacy shortcut from pkg: " + packageName);
                return pkgInfo.bitmap;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load package icon for " + packageName
                    + "; falling back to LOW_RES_INFO", e);
        }
        // LOW_RES_INFO causes BubbleTextView to trigger background high-res loading,
        // which is the standard Launcher3 icon loading path.
        return BitmapInfo.LOW_RES_INFO;
    }

    /**
     * Loads a {@link Bitmap} from a {@link Intent.ShortcutIconResource} by resolving the
     * remote package's resources and decoding the drawable at the specified resource name.
     * Returns {@code null} on any error (missing package, invalid resource, etc.).
     */
    @Nullable
    private static Bitmap loadBitmapFromResource(
            @NonNull Context context,
            @NonNull Intent.ShortcutIconResource iconResource) {
        try {
            Resources pkgRes = context.getPackageManager()
                    .getResourcesForApplication(iconResource.packageName);
            int resId = pkgRes.getIdentifier(iconResource.resourceName, null, null);
            if (resId == 0) {
                Log.w(TAG, "Resource not found: " + iconResource.resourceName
                        + " in package " + iconResource.packageName);
                return null;
            }
            int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
            Drawable d = pkgRes.getDrawableForDensity(resId, densityDpi, null /* theme */);
            if (d == null) return null;

            if (d instanceof BitmapDrawable) {
                Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                if (bmp != null) return bmp;
            }

            // For vector / adaptive / layer-list drawables, rasterise to Bitmap
            int w = Math.max(d.getIntrinsicWidth(),  1);
            int h = Math.max(d.getIntrinsicHeight(), 1);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
            return bmp;

        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found for icon resource: " + iconResource.packageName);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Drawable resource not found: " + iconResource.resourceName
                    + " in " + iconResource.packageName);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading legacy shortcut icon resource", e);
        }
        return null;
    }
}
