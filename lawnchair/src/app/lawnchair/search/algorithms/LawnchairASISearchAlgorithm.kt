package app.lawnchair.search.algorithms

import android.app.search.Query
import android.app.search.SearchContext
import android.app.search.SearchSession
import android.app.search.SearchTarget
import android.app.search.SearchUiManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import androidx.core.os.bundleOf
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.preferences2.setCached
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class LawnchairASISearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {
    private val prefs = PreferenceManager2.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchSession: SearchSession? = null
    private var activeQuery: PendingQuery? = null

    init {
        createSearchSession()
        merge(
            prefs.searchResultShortcuts.get().distinctUntilChanged().drop(1),
            prefs.searchResultPeople.get().distinctUntilChanged().drop(1),
            prefs.searchResultPixelTips.get().distinctUntilChanged().drop(1),
            prefs.searchResultSettings.get().distinctUntilChanged().drop(1),
        ).onEach {
            createSearchSession()
        }.launchIn(scope)
    }

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        activeQuery?.cancel()
        searchSession ?: return
        activeQuery = PendingQuery(query, callback)
        val searchQuery = Query(query, System.currentTimeMillis())
//        searchSession!!.query(searchQuery, Executors.MAIN_EXECUTOR, null)
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        activeQuery?.cancel()
    }

    override fun destroy() {
        super.destroy()
        scope.cancel()
        Executors.UI_HELPER_EXECUTOR.execute {
            searchSession?.destroy()
        }
    }

    private fun createSearchSession() {
        if (LawnchairQuickstepCompat.ATLEAST_U) return
        Executors.UI_HELPER_EXECUTOR.execute {
            searchSession?.destroy()
            val idp = LauncherAppState.getIDP(context)
            val extras = bundleOf(
                "launcher.gridSize" to idp.numDatabaseAllAppsColumns,
                "allowlist_enabled" to false,
                "launcher.maxInlineIcons" to 3,
            )
            // apps or shortcuts
            var resultTypes = 1 or 2
            if (prefs.searchResultShortcuts.firstCached()) {
                resultTypes = resultTypes or 1546
            }
            if (prefs.searchResultPeople.firstCached()) {
                resultTypes = resultTypes or 4
            }
            if (prefs.searchResultPixelTips.firstCached()) {
                resultTypes = resultTypes or 8192
                extras.putString("tips_source", "superpacks_tips_source")
            }
            if (prefs.searchResultSettings.firstCached()) {
                resultTypes = resultTypes or 80
                extras.putString("settings_source", "superpacks_settings_source")
            }
            val searchContext = SearchContext(resultTypes, 200, extras)
            val searchSession = context.requireSystemService<SearchUiManager>()
                .createSearchSession(searchContext)
            Executors.MAIN_EXECUTOR.post { this.searchSession = searchSession }
        }
    }

    private inner class PendingQuery(
        private val query: String,
        private val callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>,
    ) : Consumer<List<SearchTarget>> {
        private var canceled = false

        override fun accept(platformTargets: List<SearchTarget>) {
            if (!canceled) {
                val targets = platformTargets.map { SearchTargetCompat.wrap(it) }
                setFirstItemQuickLaunch(targets)
                val adapterItems = transformSearchResults(targets)
                callback.onSearchResult(
                    query,
                    ArrayList<BaseAllAppsAdapter.AdapterItem>(adapterItems),
                )
            }
        }

        fun cancel() {
            canceled = true
        }
    }

    companion object {
        fun checkSearchCompatibility(context: Context) {
            if (LawnchairQuickstepCompat.ATLEAST_U) return
            Executors.UI_HELPER_EXECUTOR.execute {
                val searchContext = SearchContext(1 or 2, 200, Bundle())
                val searchManager = context.requireSystemService<SearchUiManager>()
                val searchSession: SearchSession = searchManager.createSearchSession(searchContext)
                val searchQuery = Query("dummy", System.currentTimeMillis())
                val prefs = PreferenceManager2.getInstance(context)
                val checkDone = AtomicBoolean(false)
                searchSession.query(searchQuery, Executors.MAIN_EXECUTOR) { targets ->
                    checkDone.set(true)
                    finishCompatibilityCheck(prefs, searchSession, targets.isNotEmpty())
                }
                Handler(Executors.UI_HELPER_EXECUTOR.looper).postDelayed(
                    {
                        if (!checkDone.get()) {
                            finishCompatibilityCheck(prefs, searchSession, false)
                        }
                    },
                    300,
                )
            }
        }

        private fun finishCompatibilityCheck(
            prefs: PreferenceManager2,
            session: SearchSession,
            isCompatible: Boolean,
        ) {
            Executors.MAIN_EXECUTOR.execute { prefs.deviceSearch.setCached(isCompatible) }
            try {
                session.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
