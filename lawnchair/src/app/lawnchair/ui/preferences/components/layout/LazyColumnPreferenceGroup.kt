/*
 * Copyright 2021, Lawnchair
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

package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

fun LazyListScope.preferenceGroupItems(
    count: Int,
    isFirstChild: Boolean,
    showDividers: Boolean = true,
    heading: @Composable (() -> String)? = null,
    key: ((index: Int) -> Any)? = null,
    contentType: (index: Int) -> Any? = { null },
    itemContent: @Composable (LazyItemScope.(index: Int) -> Unit),
) {
    item {
        if (!isFirstChild) {
            Spacer(modifier = Modifier.requiredHeight(8.dp))
        }
        PreferenceGroupHeading(heading?.let { it() })
    }
    items(count, key, contentType) { index ->
        // Matches PreferenceGroup's own itemSpacing (ListItemDefaults.SegmentedGap)
        // so lazy-list groups and eager-Column groups look identical.
        if (showDividers && index > 0) {
            Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
        }
        PreferenceGroupItem(
            cutTop = index > 0,
            cutBottom = index < count - 1,
        ) {
            itemContent(index)
        }
    }
}

inline fun <T> LazyListScope.preferenceGroupItems(
    items: List<T>,
    isFirstChild: Boolean,
    showDividers: Boolean = true,
    noinline heading: @Composable (() -> String)? = null,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    noinline contentType: (index: Int) -> Any? = { null },
    crossinline itemContent: @Composable (LazyItemScope.(index: Int, item: T) -> Unit),
) {
    preferenceGroupItems(
        items.size,
        isFirstChild,
        showDividers = showDividers,
        heading,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = contentType,
    ) {
        itemContent(it, items[it])
    }
}

// Corner radius at an exposed (non-cut) end of the continuous card, and at an
// inner seam where two rows in the same group meet.
private val ExposedCorner = 16.dp
private val SeamCorner = 4.dp

// Corner radius while pressed — matches the "pill" press-morph language every
// other SegmentedListItem in this app already uses (see MainSwitchPreference's
// pressedShape = CircleShape; 28.dp reads as fully rounded on a normal-height row).
private val PressedCorner = ExposedCorner

/**
 * A single row inside a continuous, seamed preference "card": rounded at
 * group-exposed ends, square at inner seams — matching [PreferenceGroup]'s
 * card language for lazy-list contexts.
 *
 * Background/color come from here (unlike a standalone [PreferenceTemplate],
 * which is transparent and expects a parent to paint the card), since a
 * continuous card's background must be drawn once per row, not per group.
 *
 * Press feedback uses [detectTapGestures]'s `onPress` — the standard idiom
 * for observing press state on a container without stealing the gesture from
 * a nested clickable: `onPress` never consumes the pointer change, and
 * [androidx.compose.foundation.gestures.PressGestureScope.tryAwaitRelease]
 * correctly cooperates with ancestor scrollables (it returns false and we
 * reset [pressed] if the LazyColumn ends up claiming the gesture as a scroll
 * instead of a tap), unlike a raw Initial-pass-only observer which doesn't
 * participate in that arbitration and can silently never fire.
 */
@Composable
fun PreferenceGroupItem(
    modifier: Modifier = Modifier,
    cutTop: Boolean = false,
    cutBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    val restingTop = if (cutTop) SeamCorner else ExposedCorner
    val restingBottom = if (cutBottom) SeamCorner else ExposedCorner

    val topCorner by animateDpAsState(
        targetValue = if (pressed) PressedCorner else restingTop,
        label = "preferenceGroupItemTopCorner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (pressed) PressedCorner else restingBottom,
        label = "preferenceGroupItemBottomCorner",
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // requireUnconsumed = false + Final pass: the content()
                    // row's own clickable (e.g. ContributorRow, AppItem) runs
                    // on the default Main pass and consumes the down there —
                    // Main resolves child-before-parent, so by the time this
                    // ancestor node would see the event on Main, it's already
                    // consumed and awaitFirstDown()'s default
                    // requireUnconsumed = true silently ignores it forever.
                    // Final runs after Main has resolved, and passing
                    // requireUnconsumed = false means we observe the down
                    // regardless of who already consumed it — purely passive,
                    // we never call change.consume() ourselves, so the child's
                    // real click handling is completely unaffected.
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    pressed = true
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    pressed = false
                }
            },
        shape = RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner,
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        content()
    }
}
