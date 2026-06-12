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

@LauncherAppSingleton
class IconOverrideRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()

    /**
     * In-memory snapshot of all active icon overrides.
     *
     * This map is the authoritative source the icon provider reads on every
     * icon-load request.  It must be up-to-date *before* we tell the launcher
     * to redraw any icons, otherwise the launcher re-reads the map, finds the
     * old value, and renders the stale icon — producing the classic "one step
     * behind" symptom.
     *
     * Writes happen in two places:
     *  1. Eagerly inside [setOverride] / [deleteOverride] (same coroutine,
     *     before [updatePackageIcons] is called) — this is what makes the
     *     change visible on the very next frame.
     *  2. Via the Room [dao.observeAll()] collector — this keeps the map in
     *     sync after process restarts and handles any external DB mutations.
     */
    @Volatile
    private var _overridesMap = mapOf<ComponentKey, IconPickerItem>()
    val overridesMap get() = _overridesMap

    init {
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

        // 2. Update the in-memory map immediately so the icon provider sees
        //    the new value before we trigger a redraw.
        _overridesMap = _overridesMap + (target to item)

        // 3. Now tell the launcher to redraw — the map is already correct.
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
