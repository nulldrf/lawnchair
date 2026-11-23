package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        ConnectedButtonGroup(
            options = listOf(firstPageLabel, secondPageLabel),
            selectedIndex = pagerState.targetPage,
            onSelect = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

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
