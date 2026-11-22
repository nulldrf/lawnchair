package app.lawnchair.smartspace.provider

import android.app.Activity
import android.content.Context
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed class SmartspaceDataSource(
    val context: Context,
    val providerName: Int,
    getEnabledPref: PreferenceManager2.() -> Preference<Boolean, Boolean, *>,
) {
    val enabledPref = getEnabledPref(PreferenceManager2.getInstance(context))
    open val isAvailable: Boolean = true

    protected abstract val internalTargets: Flow<List<SmartspaceTarget>>
    open val disabledTargets: List<SmartspaceTarget> = emptyList()

    /**
     * Reactive fallback targets emitted while this data source is unavailable,
     * disabled, or waiting on setup. Subclasses can override this so fallback
     * cards respond to preference changes (e.g. suppressing the Google widget's
     * date-only placeholder when a custom weather provider is active).
     */
    protected open val disabledTargetsFlow: Flow<List<SmartspaceTarget>>
        get() = flowOf(disabledTargets)

    private val restartSignal = MutableStateFlow(0)
    private val enabledTargets
        get() = internalTargets
            .onStart {
                if (requiresSetup()) throw RequiresSetupException()
            }
            .map { State(targets = it) }
            .catch {
                if (it is RequiresSetupException) {
                    emitAll(
                        disabledTargetsFlow.map { fallback ->
                            State(
                                targets = fallback,
                                requiresSetup = listOf(this@SmartspaceDataSource),
                            )
                        },
                    )
                } else {
                    Log.d("SmartspaceDataSource", "data source errored", it)
                    enabledPref.set(false)
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val targets = enabledPref.get()
        .distinctUntilChanged()
        .flatMapLatest { isEnabled ->
            if (isAvailable && isEnabled) {
                restartSignal.flatMapLatest { enabledTargets }
            } else {
                disabledTargetsFlow.map { State(targets = it) }
            }
        }

    open suspend fun requiresSetup(): Boolean = false

    open suspend fun startSetup(activity: Activity) {}

    suspend fun onSetupDone() {
        if (!requiresSetup()) {
            restart()
        } else {
            enabledPref.set(false)
        }
    }

    fun restart() {
        restartSignal.value++
    }

    private class RequiresSetupException : RuntimeException()

    data class State(
        val targets: List<SmartspaceTarget> = emptyList(),
        val requiresSetup: List<SmartspaceDataSource> = emptyList(),
    ) {
        operator fun plus(other: State): State {
            return State(
                targets = this.targets + other.targets,
                requiresSetup = this.requiresSetup + other.requiresSetup,
            )
        }
    }
}
