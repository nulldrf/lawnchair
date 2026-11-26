package app.lawnchair.widget.model

import android.content.ComponentName
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages widget stack persistence in the Launcher3 `favorites` table and coordinates
 * transient in-memory handoffs (widget picker / bind / configure flows).
 */
object WidgetStackManager {

    private const val TAG = "WidgetStackManager"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Pending stack target keyed by allocated `appWidgetId`. */
    private val pendingStackByWidgetId = ConcurrentHashMap<Int, WidgetStackInfo>()

    /** Pending stack target keyed by provider `ComponentName.flattenToString()` prior to ID allocation. */
    private val pendingStackByProvider = ConcurrentHashMap<String, ConcurrentLinkedDeque<WidgetStackInfo>>()

    /**
     * Preserves in-progress edit sheet state while the user picks a new widget from
     * `WidgetsFullSheet`, so unsaved reorders, removals, and auto-rotate changes are not lost.
     */
    data class SuspendedEditSession(
        val stackInfo: WidgetStackInfo,
        val originalWidgetIds: List<Int>,
        val isEditing: Boolean,
    )

    private val suspendedEditLock = Any()
    private var suspendedEditSession: SuspendedEditSession? = null

    @JvmStatic
    fun serialize(stackInfo: WidgetStackInfo): String = json.encodeToString(stackInfo)

    @JvmStatic
    fun deserialize(raw: String?): WidgetStackInfo? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<WidgetStackInfo>(raw) }
            .onFailure { Log.w(TAG, "Failed to deserialize WidgetStackInfo", it) }
            .getOrNull()
    }

    @JvmStatic
    fun saveStack(db: SQLiteDatabase, stackInfo: WidgetStackInfo): Boolean {
        if (stackInfo.widgetIds.isEmpty()) {
            deleteStack(db, stackInfo.stackId)
            return true
        }
        val payload = serialize(stackInfo)
        return try {
            SQLiteTransaction(db).use { transaction ->
                val values = ContentValues().apply {
                    put(Favorites.WIDGET_STACK_ID, stackInfo.stackId)
                    put(Favorites.WIDGET_STACK_DATA, payload)
                }
                for (widgetId in stackInfo.widgetIds) {
                    db.update(
                        Favorites.TABLE_NAME,
                        values,
                        "${Favorites.APPWIDGET_ID} = ?",
                        arrayOf(widgetId.toString()),
                    )
                }
                transaction.commit()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving widget stack ${stackInfo.stackId}", e)
            false
        }
    }

    @JvmStatic
    fun loadStack(db: SQLiteDatabase, stackId: Long): WidgetStackInfo? {
        // Strategy 1: read serialized JSON from any member row.
        try {
            db.query(
                Favorites.TABLE_NAME,
                arrayOf(Favorites.WIDGET_STACK_DATA),
                "${Favorites.WIDGET_STACK_ID} = ? AND ${Favorites.WIDGET_STACK_DATA} IS NOT NULL",
                arrayOf(stackId.toString()),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val data = cursor.getString(0)
                    deserialize(data)?.takeIf { it.widgetIds.isNotEmpty() }?.let { return it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading WIDGET_STACK_DATA for stack $stackId", e)
        }

        // Strategy 2: reconstruct from member rows if JSON was missing/corrupted.
        return try {
            val widgetIds = mutableListOf<Int>()
            var container = Favorites.CONTAINER_DESKTOP
            var screenId = 0
            var cellX = 0
            var cellY = 0
            var spanX = 1
            var spanY = 1
            var first = true

            db.query(
                Favorites.TABLE_NAME,
                arrayOf(
                    Favorites.APPWIDGET_ID,
                    Favorites.CONTAINER,
                    Favorites.SCREEN,
                    Favorites.CELLX,
                    Favorites.CELLY,
                    Favorites.SPANX,
                    Favorites.SPANY,
                ),
                "${Favorites.WIDGET_STACK_ID} = ?",
                arrayOf(stackId.toString()),
                null,
                null,
                "${Favorites._ID} ASC",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val widgetId = cursor.getInt(0)
                    if (widgetId != LauncherAppWidgetInfo.NO_ID) {
                        widgetIds.add(widgetId)
                        if (first) {
                            container = cursor.getInt(1)
                            screenId = cursor.getInt(2)
                            cellX = cursor.getInt(3)
                            cellY = cursor.getInt(4)
                            spanX = cursor.getInt(5).coerceAtLeast(1)
                            spanY = cursor.getInt(6).coerceAtLeast(1)
                            first = false
                        }
                    }
                }
            }

            if (widgetIds.isEmpty()) {
                null
            } else {
                WidgetStackInfo(
                    stackId = stackId,
                    widgetIds = widgetIds,
                    currentIndex = 0,
                    autoRotate = false,
                    container = container,
                    screenId = screenId,
                    cellX = cellX,
                    cellY = cellY,
                    spanX = spanX,
                    spanY = spanY,
                ).also { saveStack(db, it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reconstructing widget stack $stackId", e)
            null
        }
    }

    /**
     * Low-level primitive: clears `WIDGET_STACK_ID` and `WIDGET_STACK_DATA` on a single widget row.
     * Callers are responsible for calling [saveStack] with the updated [WidgetStackInfo] for sibling rows.
     */
    @JvmStatic
    fun removeWidgetFromStack(db: SQLiteDatabase, appWidgetId: Int) {
        try {
            val values = ContentValues().apply {
                putNull(Favorites.WIDGET_STACK_ID)
                putNull(Favorites.WIDGET_STACK_DATA)
            }
            db.update(
                Favorites.TABLE_NAME,
                values,
                "${Favorites.APPWIDGET_ID} = ?",
                arrayOf(appWidgetId.toString()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed removing widget $appWidgetId from stack", e)
        }
    }

    /**
     * Clears stack metadata from all rows belonging to [stackId] without deleting the widget rows.
     */
    @JvmStatic
    fun deleteStack(db: SQLiteDatabase, stackId: Long) {
        try {
            val values = ContentValues().apply {
                putNull(Favorites.WIDGET_STACK_ID)
                putNull(Favorites.WIDGET_STACK_DATA)
            }
            db.update(
                Favorites.TABLE_NAME,
                values,
                "${Favorites.WIDGET_STACK_ID} = ?",
                arrayOf(stackId.toString()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting widget stack $stackId", e)
        }
    }

    /**
     * Removes an invalid/deleted widget from its stack JSON during model loading,
     * collapsing or deleting the stack if fewer than 1 member remains.
     */
    @JvmStatic
    fun pruneInvalidWidgetFromStack(db: SQLiteDatabase, stackId: Long, appWidgetId: Int) {
        val current = loadStack(db, stackId) ?: return
        val updated = current.withRemovedWidget(appWidgetId)
        removeWidgetFromStack(db, appWidgetId)
        if (updated.widgetIds.size <= 1) {
            deleteStack(db, stackId)
        } else {
            saveStack(db, updated)
        }
    }

    /**
     * Loads a [LauncherAppWidgetInfo] directly from `favorites` by `appWidgetId`.
     * Must be called on a background/model thread.
     */
    @JvmStatic
    fun loadLauncherAppWidgetInfoFromFavorites(
        db: SQLiteDatabase,
        appWidgetId: Int,
    ): LauncherAppWidgetInfo? {
        return try {
            db.query(
                Favorites.TABLE_NAME,
                arrayOf(
                    Favorites._ID,
                    Favorites.APPWIDGET_ID,
                    Favorites.APPWIDGET_PROVIDER,
                    Favorites.CONTAINER,
                    Favorites.SCREEN,
                    Favorites.CELLX,
                    Favorites.CELLY,
                    Favorites.SPANX,
                    Favorites.SPANY,
                    Favorites.RESTORED,
                    Favorites.APPWIDGET_SOURCE,
                    Favorites.WIDGET_STACK_ID,
                ),
                "${Favorites.APPWIDGET_ID} = ?",
                arrayOf(appWidgetId.toString()),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                val providerStr = cursor.getString(2)
                val provider = providerStr?.let(ComponentName::unflattenFromString)
                LauncherAppWidgetInfo(appWidgetId, provider).apply {
                    id = cursor.getInt(0)
                    container = cursor.getInt(3)
                    screenId = cursor.getInt(4)
                    cellX = cursor.getInt(5)
                    cellY = cursor.getInt(6)
                    spanX = cursor.getInt(7).coerceAtLeast(1)
                    spanY = cursor.getInt(8).coerceAtLeast(1)
                    restoreStatus = cursor.getInt(9)
                    sourceContainer = cursor.getInt(10)
                    widgetStackId = if (cursor.isNull(11)) null else cursor.getLong(11)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading LauncherAppWidgetInfo for widgetId=$appWidgetId", e)
            null
        }
    }

    // --- Pending widget-add handoff ---

    @JvmStatic
    fun storePendingStackInfo(appWidgetId: Int, stackInfo: WidgetStackInfo) {
        pendingStackByWidgetId[appWidgetId] = stackInfo
    }

    @JvmStatic
    fun consumePendingStackInfo(appWidgetId: Int): WidgetStackInfo? =
        pendingStackByWidgetId.remove(appWidgetId)

    @JvmStatic
    fun clearPendingStackInfo(appWidgetId: Int) {
        pendingStackByWidgetId.remove(appWidgetId)
    }

    @JvmStatic
    fun storePendingStackInfoByProvider(provider: ComponentName, stackInfo: WidgetStackInfo) {
        val key = provider.flattenToString()
        pendingStackByProvider.computeIfAbsent(key) { ConcurrentLinkedDeque() }.addLast(stackInfo)
    }

    @JvmStatic
    fun consumePendingStackInfoByProvider(provider: ComponentName?): WidgetStackInfo? {
        if (provider == null) return null
        val key = provider.flattenToString()
        val deque = pendingStackByProvider[key] ?: return null
        val item = deque.pollFirst()
        if (deque.isEmpty()) {
            pendingStackByProvider.remove(key, deque)
        }
        return item
    }

    @JvmStatic
    fun transferPendingStackInfo(provider: ComponentName?, appWidgetId: Int) {
        val stackInfo = consumePendingStackInfoByProvider(provider) ?: return
        storePendingStackInfo(appWidgetId, stackInfo)
    }

    @JvmStatic
    fun clearPendingStackInfoByProvider(provider: ComponentName?) {
        if (provider == null) return
        consumePendingStackInfoByProvider(provider)
    }

    // --- Edit sheet suspension across WidgetsFullSheet ---

    fun suspendEditSession(session: SuspendedEditSession) {
        synchronized(suspendedEditLock) {
            suspendedEditSession = session
        }
    }

    fun consumeSuspendedEditSession(): SuspendedEditSession? {
        synchronized(suspendedEditLock) {
            val session = suspendedEditSession
            suspendedEditSession = null
            return session
        }
    }

    fun peekSuspendedEditSession(): SuspendedEditSession? {
        synchronized(suspendedEditLock) {
            return suspendedEditSession
        }
    }
}
