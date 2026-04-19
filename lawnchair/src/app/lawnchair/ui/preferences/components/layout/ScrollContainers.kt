package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceColumn(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState? = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
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
    // LazyColumn is incompatible with NestedScrollView (infinite height constraints crash).
    // Render all items eagerly in a Column instead.
    val scope = remember { EagerLazyListScope() }
    scope.reset()
    scope.content()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(contentPadding),
    ) {
        scope.items.forEach { item ->
            item()
        }
    }
}

private class EagerLazyListScope : LazyListScope {
    val items = mutableListOf<@Composable () -> Unit>()

    fun reset() = items.clear()

    override fun item(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        items.add { FakeLazyItemScope.content() }
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        for (i in 0 until count) {
            items.add { FakeLazyItemScope.itemContent(i) }
        }
    }

    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        items.add { FakeLazyItemScope.content() }
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
