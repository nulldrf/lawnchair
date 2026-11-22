/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.preferences2

import android.content.Context
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.util.Executors
import com.android.quickstep.RecentsModel
import com.android.quickstep.TouchInteractionService
import com.android.quickstep.util.TISBindHelper

class ReloadHelper(private val context: Context) {

    private val idp: InvariantDeviceProfile
        get() = InvariantDeviceProfile.INSTANCE.get(context)
    private val recentsModel: RecentsModel
        get() = RecentsModel.INSTANCE.get(context)
    private val las: LauncherAppState
        get() = LauncherAppState.INSTANCE.get(context)
    private var tis: TouchInteractionService.TISBinder? = null
    private val tisBinder = TISBindHelper(context) { tis = it }

    fun reloadGrid() {
        // CachedPreferencesDataStore updates in-memory state synchronously before onSet runs,
        // so the grid immediately sees every preference value written so far.
        idp.onPreferencesChanged(context)
    }

    fun recreate() {
        LawnchairLauncher.instance?.recreateIfNotScheduled()
    }

    fun restart() {
        reloadGrid()
        recreate()
    }

    /**
     * This doesn't work, please migrate this to being a [ThemeManager] reload,
     * as of right now the behaviour is defaulted to L3 that listens on prefs changes
     */
    fun reloadIcons() {
        Executors.MODEL_EXECUTOR.execute {
            las.iconCache.clearMemoryCache()
            las.model.reloadIfActive()
        }
    }

    /**
     * Clears the recents thumbnail + icon cache and pool, updates icon params, and forces a full
     * model reload (used by icon pack and themed icon switches).
     */
    fun reloadIconsFull() {
        recentsModel.onThemeChanged()
        Executors.MODEL_EXECUTOR.execute {
            LauncherIcons.clearPool(context)
            las.iconCache.clearMemoryCache()
            las.iconCache.updateIconParams(idp.fillResIconDpi, idp.iconBitmapSize)
            las.model.forceReload()
        }
    }

    fun reloadTaskbar() {
        tisBinder.runOnBindToTouchInteractionService {
            tis?.taskbarManager?.recreateTaskbars()
        }
    }
}
