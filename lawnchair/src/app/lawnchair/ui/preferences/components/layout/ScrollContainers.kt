package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Set to true inside [PreferenceScaffold]'s content ComposeView, which sits inside a
 * [StretchNestedScrollView]. LazyColumn crashes under NestedScrollView (unbounded height
 * constraints), so [PreferenceLazyColumn] switches to an eager Column in that context.
 * Screens backed by a plain Compose Scaffold (e.g. [PreferenceSearchScaffold]) leave this
 * false and get a real LazyColumn with proper item virtualization.
 */
val LocalInsideNestedScrollView = compositionLocalOf { false }

@Composable
fun PreferenceColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState? = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // No wrapContentHeight — let content size naturally so NestedScrollView
    // can scroll all the way to the bottom without cutting off items.
    // No verticalScroll — NestedScrollView handles scrolling.
    // No NestedScrollStretch — StretchNestedScrollView handles overscroll.
    Column(
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(top = 8.dp, bottom = 16.dp),
        content = content,
    )
}

@Composable
fun PreferenceLazyColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isChild: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    if (LocalInsideNestedScrollView.current) {
        // LazyColumn crashes inside NestedScrollView (unbounded height constraints).
        // Render items eagerly in a Column instead. Only screens inside
        // PreferenceScaffold (which uses StretchNestedScrollView) hit this path.
        val scope = remember { EagerLazyListScope() }
        scope.reset()
        scope.content()

        // Render only the first INITIAL_VISIBLE_ITEMS on the first composition so
        // the initial pass finishes within one frame. Heavy screens (About, Dock,
        // Search) have 30-50+ items — composing all of them eagerly takes 50-100ms
        // on a cold process, dropping frames during the navigation enter transition.
        // LaunchedEffect expands to all items on the next frame after composition
        // settles; items below the fold aren't visible yet so the user never sees
        // the expansion.
        var visibleCount by remember { mutableIntStateOf(INITIAL_VISIBLE_ITEMS) }
        LaunchedEffect(scope.items.size) {
            visibleCount = scope.items.size
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding),
        ) {
            scope.items.take(visibleCount).forEach { keyed ->
                key(keyed.key) {
                    keyed.content()
                }
            }
        }
    } else {
        // Pure-Compose Scaffold (e.g. PreferenceSearchScaffold) — use a real
        // LazyColumn so only visible items are composed. This is critical for
        // the font list: without virtualization, all 1000+ items recompose on
        // every selection change, freezing the main thread.
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            state = state,
            content = content,
        )
    }
}

// Number of items composed on the first frame. Enough to fill any phone screen
// (preference rows are ~56-72dp tall, so 8 covers ~450-580dp). The rest are
// added by LaunchedEffect on the next frame after the enter transition starts.
private const val INITIAL_VISIBLE_ITEMS = 8

private class EagerLazyListScope : LazyListScope {
    data class KeyedItem(val key: Any?, val content: @Composable () -> Unit)

    val items = mutableListOf<KeyedItem>()

    fun reset() = items.clear()

    override fun item(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        items.add(KeyedItem(key) { FakeLazyItemScope.content() })
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        for (i in 0 until count) {
            items.add(KeyedItem(key?.invoke(i)) { FakeLazyItemScope.itemContent(i) })
        }
    }

    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        items.add(KeyedItem(key) { FakeLazyItemScope.content() })
    }
}

private object FakeLazyItemScope : LazyItemScope {
    override fun Modifier.animateItem(
        fadeInSpec: FiniteAnimationSpec<Float>?,
        placementSpec: FiniteAnimationSpec<IntOffset>?,
        fadeOutSpec: FiniteAnimationSpec<Float>?,
    ): Modifier = this

    override fun Modifier.fillParentMaxSize(fraction: Float): Modifier = this
    override fun Modifier.fillParentMaxWidth(fraction: Float): Modifier = this
    override fun Modifier.fillParentMaxHeight(fraction: Float): Modifier = this
}
