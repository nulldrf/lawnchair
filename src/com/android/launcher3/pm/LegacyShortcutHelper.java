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

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.WorkspaceItemInfo;

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
 * <h3>What this class does</h3>
 * <p>Parses the old-style result {@code Intent} extras and builds a {@link WorkspaceItemInfo}
 * that is safe to use throughout Lawnchair. In particular, it ensures that the stored
 * {@code intent} always has either a {@link ComponentName} or a package name set, so that
 * {@link com.android.launcher3.model.data.ItemInfo#getTargetComponent()} and
 * {@link com.android.launcher3.model.data.ItemInfo#getTargetPackage()} never return {@code null}
 * for our items. Returning {@code null} from those methods causes {@code NullPointerException}
 * in {@link com.android.launcher3.util.ComponentKey}, {@link com.android.launcher3.util.PackageUserKey},
 * and several {@link com.android.launcher3.popup.SystemShortcut} factories.
 *
 * <h3>Legacy Intent extras</h3>
 * <ul>
 *   <li>{@link Intent#EXTRA_SHORTCUT_NAME} — display title (required)</li>
 *   <li>{@link Intent#EXTRA_SHORTCUT_INTENT} — the {@link Intent} to launch (required)</li>
 *   <li>{@link Intent#EXTRA_SHORTCUT_ICON_RESOURCE} — drawable resource in the shortcut's
 *       package (optional)</li>
 *   <li>{@link Intent#EXTRA_SHORTCUT_ICON} — raw {@link Bitmap} (optional)</li>
 * </ul>
 */
public final class LegacyShortcutHelper {

    private static final String TAG = "LegacyShortcutHelper";

    private LegacyShortcutHelper() {}

    /**
     * Attempts to build a {@link WorkspaceItemInfo} from legacy {@code Intent.EXTRA_SHORTCUT_*}
     * extras in a result {@code Intent} returned by an {@code ACTION_CREATE_SHORTCUT} activity.
     *
     * <p>Returns {@code null} if the required extras (title + launch intent) are absent, so the
     * caller knows to treat the result as invalid.
     *
     * <p>The returned item has {@code itemType} set to
     * {@link LauncherSettings.Favorites#ITEM_TYPE_APPLICATION} and its {@code intent} is
     * guaranteed to have either a {@link ComponentName} or a package name set, preventing
     * downstream {@code NullPointerException}s in {@code ComponentKey} and friends.
     *
     * @param context application or activity context
     * @param data    the result {@link Intent} from {@code onActivityResult}
     * @return populated {@link WorkspaceItemInfo}, or {@code null} if data is unusable
     */
    @Nullable
    public static WorkspaceItemInfo createWorkspaceItemFromLegacyIntent(
            Context context, Intent data) {

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

        // ── 3. Ensure the launch intent has a package so downstream code never gets null ──────
        //
        // ItemInfo.getTargetComponent() → intent.getComponent() → may be null for generic intents
        // ItemInfo.getTargetPackage()   → falls back to intent.getPackage() → also may be null
        //
        // Both ComponentKey and PackageUserKey call these methods without null checks and crash
        // with NPE if they return null. We fix this by ensuring intent.getPackage() is set.
        //
        // Priority:
        //   a) The intent already has a ComponentName  → getPackageName() from it
        //   b) The intent already has a package string → keep it
        //   c) Resolve the activity and use its package
        //   d) No package discoverable            → log and bail, we cannot create a safe item
        if (launchIntent.getComponent() != null) {
            // (a) ComponentName present – getTargetComponent() will return it directly.
            // Also make sure the package string matches so both paths return consistently.
            if (launchIntent.getPackage() == null) {
                launchIntent.setPackage(launchIntent.getComponent().getPackageName());
            }
        } else if (launchIntent.getPackage() == null) {
            // (c) No component, no package – try resolving
            android.content.pm.ResolveInfo ri = context.getPackageManager()
                    .resolveActivity(launchIntent, 0);
            if (ri != null && ri.activityInfo != null) {
                String resolvedPackage = ri.activityInfo.packageName;
                String resolvedClass   = ri.activityInfo.name;
                launchIntent.setComponent(new ComponentName(resolvedPackage, resolvedClass));
                launchIntent.setPackage(resolvedPackage);
                Log.d(TAG, "Resolved legacy shortcut intent to " + resolvedPackage);
            } else {
                // (d) Unresolvable – cannot produce a safe item
                Log.w(TAG, "Cannot resolve legacy shortcut intent " + launchIntent
                        + " — getTargetPackage() would be null, aborting to prevent NPE.");
                return null;
            }
        }
        // At this point, launchIntent.getPackage() is guaranteed non-null.

        // ── 4. Build the WorkspaceItemInfo ────────────────────────────────────────────────────
        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.title            = title;
        info.contentDescription = title;
        info.intent           = launchIntent;
        // ITEM_TYPE_APPLICATION is the correct type for legacy shortcuts after DB migration
        // (ITEM_TYPE_SHORTCUT = 1 is deprecated and stripped in onUpgrade case 31).
        // Using ITEM_TYPE_APPLICATION keeps ShortcutUtil.isApp() true so that
        // ShortcutUtil.supportsShortcuts() works and the long-press popup is shown,
        // but we must ensure getTargetComponent() is non-null (done above).
        info.itemType         = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user             = Process.myUserHandle();

        // ── 5. Load icon ──────────────────────────────────────────────────────────────────────
        info.bitmap = loadLegacyIcon(context, data);

        Log.d(TAG, "Created legacy WorkspaceItemInfo: title=\"" + title
                + "\", pkg=" + launchIntent.getPackage()
                + ", component=" + launchIntent.getComponent());
        return info;
    }

    // ── Icon loading ──────────────────────────────────────────────────────────────────────────

    /**
     * Loads the icon by trying, in order:
     * <ol>
     *   <li>Raw {@link Bitmap} from {@link Intent#EXTRA_SHORTCUT_ICON}</li>
     *   <li>{@link Intent.ShortcutIconResource} from {@link Intent#EXTRA_SHORTCUT_ICON_RESOURCE}</li>
     *   <li>{@link BitmapInfo#LOW_RES_INFO} as a final fallback</li>
     * </ol>
     */
    private static BitmapInfo loadLegacyIcon(Context context, Intent data) {
        // a) Raw Bitmap
        Bitmap rawBitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        if (rawBitmap != null) {
            return createBitmapInfo(context, rawBitmap);
        }

        // b) ShortcutIconResource
        Intent.ShortcutIconResource iconResource =
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
        if (iconResource != null) {
            Bitmap resourceBitmap = loadBitmapFromResource(context, iconResource);
            if (resourceBitmap != null) {
                return createBitmapInfo(context, resourceBitmap);
            }
        }

        // c) Fallback
        Log.w(TAG, "Legacy shortcut has no usable icon — using LOW_RES_INFO placeholder.");
        return BitmapInfo.LOW_RES_INFO;
    }

    /**
     * Passes a {@link Bitmap} through the launcher's own {@link LauncherIcons} factory so it
     * is properly sized, badged, and wrapped in a {@link BitmapInfo}.
     */
    private static BitmapInfo createBitmapInfo(Context context, Bitmap bitmap) {
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            return li.createIconBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create BitmapInfo from legacy shortcut Bitmap", e);
            return BitmapInfo.LOW_RES_INFO;
        }
    }

    /**
     * Loads a {@link Bitmap} from a {@link Intent.ShortcutIconResource} by resolving the
     * package resources and decoding the drawable.  Returns {@code null} on any error.
     */
    @Nullable
    private static Bitmap loadBitmapFromResource(
            Context context, Intent.ShortcutIconResource iconResource) {
        try {
            Resources pkgRes = context.getPackageManager()
                    .getResourcesForApplication(iconResource.packageName);
            int resId = pkgRes.getIdentifier(iconResource.resourceName, null, null);
            if (resId == 0) {
                Log.w(TAG, "Resource not found: " + iconResource.resourceName
                        + " in " + iconResource.packageName);
                return null;
            }
            int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
            Drawable d = pkgRes.getDrawableForDensity(resId, densityDpi, null);
            if (d == null) return null;
            if (d instanceof BitmapDrawable) {
                return ((BitmapDrawable) d).getBitmap();
            }
            // Render vector / adaptive drawables to a Bitmap
            int w = Math.max(d.getIntrinsicWidth(),  1);
            int h = Math.max(d.getIntrinsicHeight(), 1);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
            return bmp;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found for icon: " + iconResource.packageName);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Resource not found for icon: " + iconResource.resourceName);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading legacy shortcut icon", e);
        }
        return null;
    }
}
