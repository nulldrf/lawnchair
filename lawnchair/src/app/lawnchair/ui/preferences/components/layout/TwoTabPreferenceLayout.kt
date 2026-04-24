package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import kotlinx.coroutines.launch

@Composable
fun TwoTabPreferenceLayout(
    label: String,
    firstPageLabel: String,
    firstPageContent: @Composable ColumnScope.() -> Unit,
    secondPageLabel: String,
    secondPageContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    backArrowVisible: Boolean = true,
    isExpandedScreen: Boolean = LocalIsExpandedScreen.current,
    defaultPage: Int = 0,
) {
    PreferenceLayout(
        label = label,
        modifier = modifier,
        backArrowVisible = backArrowVisible,
        isExpandedScreen = isExpandedScreen,
    ) {
        val pagerState = rememberPagerState(
            initialPage = defaultPage,
            pageCount = { 2 },
        )
        val scope = rememberCoroutineScope()

        var selectedPage by remember { mutableIntStateOf(defaultPage) }

        val colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primary,
            activeContentColor = MaterialTheme.colorScheme.onPrimary,
            activeBorderColor = Color.Transparent,
            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            inactiveBorderColor = Color.Transparent,
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SegmentedButton(
                selected = selectedPage == 0,
                onClick = {
                    selectedPage = 0
                    scope.launch { pagerState.animateScrollToPage(0) }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = colors,
                icon = {},
                label = { Text(firstPageLabel) },
            )
            SegmentedButton(
                selected = selectedPage == 1,
                onClick = {
                    selectedPage = 1
                    scope.launch { pagerState.animateScrollToPage(1) }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = colors,
                icon = {},
                label = { Text(secondPageLabel) },
            )
        }

        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.animateContentSize(),
        ) { page ->
            when (page) {
                0 -> Column { firstPageContent() }
                1 -> Column { secondPageContent() }
            }
        }
    }
}
