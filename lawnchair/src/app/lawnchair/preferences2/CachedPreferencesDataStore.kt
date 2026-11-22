package app.lawnchair.preferences2

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Write-through in-memory [DataStore] wrapper for [Preferences].
 *
 * Reads (`data` and [currentPreferences]) are always served from a single [MutableStateFlow]
 * initialized once from [delegate] (after DataStore migrations complete).
 *
 * Writes (`updateData` / [editBatch]) mutate the in-memory snapshot immediately under [memoryMutex]
 * so Compose UI, `firstCached()`, Flow collectors, and `onSet` callbacks observe the new value
 * synchronously without waiting for disk I/O, while a single coalescing flusher on [Dispatchers.IO]
 * persists the latest snapshot to [delegate]. Rapid bursts of writes (e.g. slider drags) collapse
 * into at most one in-flight write plus one follow-up disk write.
 */
class CachedPreferencesDataStore(
    private val delegate: DataStore<Preferences>,
    ioScope: CoroutineScope,
) : DataStore<Preferences> {

    private val state = MutableStateFlow(
        runBlocking {
            delegate.data.first().toPreferences()
        },
    )

    private val memoryMutex = Mutex()
    private val diskMutex = Mutex()
    private val pendingGeneration = AtomicLong(0L)
    private val flushedGeneration = AtomicLong(0L)
    private val flushSignals = Channel<Unit>(Channel.CONFLATED)

    override val data: Flow<Preferences>
        get() = state

    val currentPreferences: Preferences
        get() = state.value

    val hasPendingWrites: Boolean
        get() = flushedGeneration.get() < pendingGeneration.get()

    init {
        ioScope.launch {
            for (@Suppress("UNUSED_VARIABLE") signal in flushSignals) {
                flushPendingToDisk()
            }
        }
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = memoryMutex.withLock {
        val current = state.value
        val next = transform(current).toPreferences()
        if (next != current) {
            state.value = next
            pendingGeneration.incrementAndGet()
            flushSignals.trySend(Unit)
        }
        next
    }

    suspend fun editBatch(block: MutablePreferences.() -> Unit): Preferences = updateData { current ->
        current.toMutablePreferences().apply(block)
    }

    /**
     * Suspends until all in-memory writes performed so far are durably persisted to disk.
     */
    suspend fun flush() {
        if (!hasPendingWrites) return
        flushPendingToDisk()
    }

    /**
     * Triggers an asynchronous flush if there are unpersisted writes.
     */
    fun flushAsync() {
        if (!hasPendingWrites) return
        flushSignals.trySend(Unit)
    }

    /**
     * Blocks briefly until pending writes are flushed to disk (for process restarts / shutdown).
     */
    fun flushBlocking(timeoutMs: Long = 2_000L) {
        if (!hasPendingWrites) return
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                flushPendingToDisk()
            }
        }
    }

    private suspend fun flushPendingToDisk() {
        diskMutex.withLock {
            while (flushedGeneration.get() < pendingGeneration.get()) {
                val (targetGen, snapshot) = memoryMutex.withLock {
                    pendingGeneration.get() to state.value
                }
                try {
                    delegate.updateData { snapshot }
                    flushedGeneration.set(targetGen)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to persist preferences to disk", e)
                    break
                }
            }
        }
    }

    companion object {
        private const val TAG = "CachedPrefsDataStore"
    }
}
