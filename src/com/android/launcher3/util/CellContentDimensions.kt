/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.util

import com.android.launcher3.Utilities
import kotlin.math.max

class CellContentDimensions(
    var iconSizePx: Int,
    var iconDrawablePaddingPx: Int,
    var iconTextSizePx: Int,
    var maxLineCount: Int,
) {
    /**
     * This method goes through some steps to reduce the padding between icon and label, icon size
     * and then label size, until it can fit in the [cellHeightPx].
     *
     * @return the height of the content after being sized down.
     */
    fun resizeToFitCellHeight(cellHeightPx: Int, iconSizeSteps: IconSizeSteps): Int {
        var cellContentHeight = getCellContentHeight()

        // Step 1, Decrease the number of lines of text that can be shown within the cell.
        while (cellContentHeight > cellHeightPx && maxLineCount >= 2) {
            --maxLineCount
            cellContentHeight = getCellContentHeight()
        }

        // Step 2. Decrease drawable padding
        if (cellContentHeight > cellHeightPx) {
            val diff = cellContentHeight - cellHeightPx
            // Lawnchair: with two-line labels (maxLineCount >= 2), don't let this collapse all
            // the way towards zero — keep at least half of the originally computed padding so
            // the label doesn't end up visually touching the icon. Any remaining deficit is
            // absorbed by the icon/label size reduction steps below instead, same as it already
            // would be if padding alone weren't enough. Single-line cells (maxLineCount == 1,
            // today's default) are completely unaffected, since the floor is 0 there, matching
            // the original `max(0, ...)` exactly.
            val minIconDrawablePaddingPx = if (maxLineCount >= 2) iconDrawablePaddingPx / 2 else 0
            iconDrawablePaddingPx = max(minIconDrawablePaddingPx, iconDrawablePaddingPx - diff)
            cellContentHeight = getCellContentHeight()
        }

        while (
            (iconTextSizePx > iconSizeSteps.minimumIconLabelSize ||
                iconSizePx > iconSizeSteps.minimumIconSize()) && cellContentHeight > cellHeightPx
        ) {
            // Step 3. Decrease icon size
            iconSizePx = iconSizeSteps.getNextLowerIconSize(iconSizePx)
            cellContentHeight = getCellContentHeight()

            // Step 4. Decrease label size
            if (
                cellContentHeight > cellHeightPx &&
                    iconTextSizePx > iconSizeSteps.minimumIconLabelSize
            ) {
                iconTextSizePx =
                    max(
                        iconSizeSteps.minimumIconLabelSize,
                        iconTextSizePx - IconSizeSteps.TEXT_STEP,
                    )
                cellContentHeight = getCellContentHeight()
            }
        }

        // For some cases, depending on the display size, the content might not fit inside the
        // cell height after considering the minimum icon and label size allowed.
        // For these extreme cases, we will allow the icon size to be smaller than
        // [IconSizeSteps.minimumIconSize] to fit inside the cell height without cropping.
        while (
            cellContentHeight > cellHeightPx && iconSizePx > IconSizeSteps.ICON_SIZE_STEP_EXTRA
        ) {
            iconSizePx -= IconSizeSteps.ICON_SIZE_STEP_EXTRA
            cellContentHeight = getCellContentHeight()
        }

        return cellContentHeight
    }

    /** Calculate new cellContentHeight */
    fun getCellContentHeight(): Int {
        val iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx.toFloat())
        // Lawnchair: Utilities.calculateTextHeight() returns a single-line estimate that
        // doesn't always exactly match what a real multi-line StaticLayout (built by
        // BubbleTextView.modifyTitleToSupportMultiLine()) ends up needing once actual font
        // metrics/line spacing are accounted for. With zero margin, that mismatch can be just
        // large enough to fail the height check there right at a user's full (100%) label-size
        // setting, silently falling back to single-line "…". For maxLineCount == 1 (the
        // default, single-line case) extraLines is 0 and this is identical to the original
        // zero-margin formula.
        val extraLines = max(0, maxLineCount - 1)
        val marginPx = (extraLines * iconTextHeight) / 4
        return iconSizePx + iconDrawablePaddingPx + (iconTextHeight * maxLineCount) + marginPx
    }
}
