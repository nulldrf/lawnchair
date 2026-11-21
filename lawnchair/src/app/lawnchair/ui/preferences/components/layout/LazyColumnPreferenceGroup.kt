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

private val ExposedCorner = 16.dp
private val SeamCorner = 4.dp
private val PressedCorner = ExposedCorner

/**
 * A segmented card row for lazy lists that rounds exposed ends and animates corners while pressed.
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
                    // Observe press state passively on the Final pass without consuming child clicks.
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
