package app.lawnchair.util

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun View.repeatOnAttached(block: suspend CoroutineScope.() -> Unit) {
    var launchedJob: Job? = null

    val mutex = Mutex()
    observeAttachedState { isAttached ->
        if (isAttached) {
            launchedJob = MainScope().launch(
                context = Dispatchers.Main.immediate,
                start = CoroutineStart.UNDISPATCHED,
            ) {
                mutex.withLock {
                    coroutineScope {
                        block()
                    }
                }
            }
            return@observeAttachedState
        }
        launchedJob?.cancel()
        launchedJob = null
    }
}

/**
 * Like [repeatOnAttached], but additionally scopes [block] to [Lifecycle.State.STARTED]
 * when a view-tree [androidx.lifecycle.LifecycleOwner] is available so background/screen-off
 * states automatically cancel upstream flows and resume them when the host returns to foreground.
 */
fun View.repeatOnAttachedAndStarted(block: suspend CoroutineScope.() -> Unit) {
    repeatOnAttached {
        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                block()
            }
        } else {
            block()
        }
    }
}
