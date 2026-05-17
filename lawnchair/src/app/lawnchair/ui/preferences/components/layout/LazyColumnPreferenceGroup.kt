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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.preferenceGroupColor

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
        // 4dp gap between items — matches Arrangement.spacedBy(4.dp) used in
        // PreferenceGroup so the spacing is consistent across the settings UI.
        if (showDividers && index > 0) {
            Spacer(modifier = Modifier.height(4.dp))
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

@Composable
fun PreferenceGroupItem(
    modifier: Modifier = Modifier,
    cutTop: Boolean = false,
    cutBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Large corner (28dp) on exposed ends, small corner (4dp) on inner joins —
    // matches preferenceGroupItemShape(largeCorner=24dp, smallCorner=4dp) used
    // by PreferenceGroup so all card groups look identical.
    val shape = remember(cutTop, cutBottom) {
        val top = if (cutTop) 4.dp else 28.dp
        val bottom = if (cutBottom) 4.dp else 28.dp
        RoundedCornerShape(top, top, bottom, bottom)
    }
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = shape,
        color = preferenceGroupColor(),
    ) {
        content()
    }
}
