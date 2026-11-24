package app.lawnchair.search.algorithms

import android.content.Context
import android.os.Handler
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)

    // todo maybe use D.I.?
    private val searchTargetFactory = SearchTargetFactory(context)

    private val prefs2 = PreferenceManager2.getInstance(context)

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var modelRetryJob: Job? = null
    private var currentJob: Job? = null

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        modelRetryJob?.cancel()
        modelRetryJob = appState.model.runWhenModelLoaded(coroutineScope) { apps ->
            // Track the delivery job so cancel(true) (e.g. from a drawer close) can stop a
            // late-arriving result from being delivered after reset. See issue #7325.
            currentJob?.cancel()
            currentJob = coroutineScope.launch(Dispatchers.Main) {
                val results = getResult(apps.data, query)
                callback.onSearchResult(query, results)
            }
        }
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        if (interruptActiveRequests) {
            modelRetryJob?.cancel()
            currentJob?.cancel()
            resultHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun destroy() {
        cancel(true)
        coroutineScope.cancel()
        super.destroy()
    }

    private fun getResult(
        apps: MutableList<AppInfo>,
        query: String,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        val enableFuzzySearch = prefs2.enableFuzzySearch.firstCached()
        val maxResultsCount = prefs2.maxAppSearchResultCount.firstCached()
        val hiddenApps = prefs2.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs2.hiddenAppsInSearch.firstCached()

        val appResults = if (enableFuzzySearch) {
            SearchUtils.fuzzySearch(apps, query, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        } else {
            SearchUtils.normalSearch(apps, query, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        }

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty()) {
            if (appResults.size == 1 && context.isDefaultLauncher()) {
                val singleAppResult = appResults.firstOrNull()
                val shortcuts = singleAppResult?.let { SearchUtils.getShortcuts(it, context) }
                if (shortcuts != null && shortcuts.isNotEmpty()) {
                    // Show app as a row alongside its shortcuts (no duplicate icon)
                    singleAppResult.let { searchTargets.add(searchTargetFactory.createAppSearchTarget(it, true)) }
                    searchTargets.addAll(shortcuts.map(searchTargetFactory::createShortcutTarget))
                } else {
                    // No shortcuts: show as icon
                    appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)
                }
            } else {
                // Multiple results: show as icons
                appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)
            }
            searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
        }

        searchTargetFactory.createMarketSearchTarget(query)?.let { searchTargets.add(it) }

        setFirstItemQuickLaunch(searchTargets)
        val adapterItems = transformSearchResults(searchTargets)
        return ArrayList(adapterItems)
    }
}
