package app.lawnchair.preferences2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.util.subscribeBlocking
import com.patrykmichalik.opto.core.PreferenceImpl
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun <C, S> Preference<C, S, *>.asState() = if (this is PreferenceImpl<C, S>) {
    val flow = remember(this) { get().distinctUntilChanged() }
    flow.collectAsStateWithLifecycle(initialValue = firstCached())
} else {
    val flow = remember(this) { get().distinctUntilChanged() }
    flow.collectAsStateWithLifecycle(initialValue = firstBlocking())
}

@Composable
fun <C, S> PreferenceImpl<C, S>.asState() = remember(this) {
    get().distinctUntilChanged()
}.collectAsStateWithLifecycle(initialValue = firstCached())

fun <C, S> PreferenceImpl<C, S>.subscribeBlocking(
    prefs2: PreferenceManager2,
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    val initial = firstCached(prefs2)
    block(initial)
    get()
        .distinctUntilChanged()
        .dropWhile { it == initial }
        .onEach { block(it) }
        .launchIn(scope = scope)
}

fun <C, S> Preference<C, S, *>.subscribeBlocking(
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    if (this is PreferenceImpl<C, S>) {
        val initial = firstCached()
        block(initial)
        get()
            .distinctUntilChanged()
            .dropWhile { it == initial }
            .onEach { block(it) }
            .launchIn(scope = scope)
    } else {
        get().subscribeBlocking(scope, block)
    }
}
