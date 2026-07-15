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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceGroupItem(
    modifier: Modifier = Modifier,
    cutTop: Boolean = false,
    cutBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Map the cutTop/cutBottom boundary flags onto a representative (index, count)
    // pair. ListItemDefaults.segmentedShapes only cares about first/middle/last/
    // single-item status, not the literal count, so any pair with the same
    // boundary status produces an identical shape — this keeps every call site
    // (About.kt, ChangesDialog.kt, FontSelectionPreference.kt, and the bulk
    // preferenceGroupItems() helper above) visually consistent with the rest of
    // the segmented-list system without needing to thread real index/count
    // through every caller.
    val (index, count) = when {
        !cutTop && !cutBottom -> 0 to 1 // standalone item
        !cutTop && cutBottom -> 0 to 2 // first of a group
        cutTop && !cutBottom -> 1 to 2 // last of a group
        else -> 1 to 3 // middle of a group
    }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = shapes.shape,
        color = colors.containerColor,
    ) {
        content()
    }
}