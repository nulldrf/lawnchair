package app.lawnchair.data.iconoverride

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.icons.picker.IconPickerItem
import com.android.launcher3.LauncherAppState
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking

@LauncherAppSingleton
class IconOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()

    /**
     * In-memory snapshot of all active icon overrides.
     *
     * ## Why pre-loaded synchronously
     *
     * Previously this started as `emptyMap()` and was only populated once the
     * Room [dao.observeAll()] Flow emitted its first value — an async event
     * that arrives sometime after the Dagger singleton is constructed.
     *
     * This created a cold-start race:
     *
     *  1. User opens Lawnchair for the first time (or after a force-stop).
     *  2. [IconOverrideRepository] is constructed; `_overridesMap = emptyMap()`.
     *  3. User immediately picks a custom icon → [setOverride] is called.
     *  4. `_overridesMap` is patched: `emptyMap + (target → item)` ✓
     *  5. [updatePackageIcons] notifies the launcher model.
     *  6. *Sometime later* the Room Flow emits its first value — which at this
     *     point may be the pre-insert snapshot (empty) if Room reads the DB
     *     before the [dao.insert] WAL entry is checkpointed, **overwriting
     *     `_overridesMap` back to `emptyMap()`**.
     *  7. Launcher redraws using the now-empty map → shows the system icon.
     *  8. On the *next* change the Flow has already emitted the correct rows,
     *     so the map is right and the icon updates — "one step behind".
     *
     * The same race affects [deleteOverride]: the eager map subtraction is
     * correct, but if the Flow fires a stale emission afterward it undoes it.
     *
     * ## Solution
     *
     * Pre-populate [_overridesMap] synchronously with a single blocking DB
     * read during construction. This guarantees the map is never empty when
     * [setOverride] or [deleteOverride] is first called, regardless of how
     * quickly the user acts after launch.
     *
     * The [dao.observeAll()] collector still runs in the background and
     * continues to keep the map in sync — but it now acts only as a
     * reconciliation layer (handling external mutations, process restores,
     * multi-process writes), never as the *sole* source for the initial state.
     *
     * The blocking read is intentional and safe: [IconOverrideRepository] is
     * a [LauncherAppSingleton] constructed on a background Dagger thread, so
     * it does not block the main thread. If construction ever moves to the
     * main thread, replace with an `async` + `await` pattern gated by a
     * `Mutex`.
     */
    @Volatile
    private var _overridesMap: Map<ComponentKey, IconPickerItem> = runBlocking(Dispatchers.IO) {
        dao.getAll().associateBy(
            keySelector = { it.target },
            valueTransform = { it.iconPickerItem },
        )
    }

    val overridesMap get() = _overridesMap

    init {
        // Keep the in-memory map in sync with the DB for any changes that
        // arrive after construction (remote writes, other-process mutations,
        // deleteAll, etc.).  The initial value is already set above, so the
        // first emission from this Flow is redundant but harmless.
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.IO)
                .collect { overrides ->
                    _overridesMap = overrides.associateBy(
                        keySelector = { it.target },
                        valueTransform = { it.iconPickerItem },
                    )
                }
        }
    }

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        // 1. Persist to DB.
        dao.insert(IconOverride(target, item))

        // 2. Patch the in-memory map immediately so the icon provider sees
        //    the new entry before [updatePackageIcons] asks it to redraw.
        //    Because _overridesMap is pre-loaded (never starts empty), this
        //    correctly merges into whatever was already there.
        _overridesMap = _overridesMap + (target to item)

        // 3. Tell the launcher to redraw — the map is already correct.
        updatePackageIcons(target)
    }

    suspend fun deleteOverride(target: ComponentKey) {
        // 1. Persist to DB.
        dao.delete(target)

        // 2. Remove from the in-memory map immediately.
        _overridesMap = _overridesMap - target

        // 3. Trigger redraw with the map already reflecting the deletion.
        updatePackageIcons(target)
    }

    fun observeTarget(target: ComponentKey) = dao.observeTarget(target)

    fun observeCount() = dao.observeCount()

    suspend fun deleteAll() {
        dao.deleteAll()
        _overridesMap = emptyMap()
        LauncherAppState.getInstance(context).model.reloadIfActive()
    }

    private fun updatePackageIcons(target: ComponentKey) {
        val model = LauncherAppState.INSTANCE.get(context).model
        model.onPackageIconsUpdated(hashSetOf(target.componentName.packageName), target.user)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getIconOverrideRepository)
    }
}
