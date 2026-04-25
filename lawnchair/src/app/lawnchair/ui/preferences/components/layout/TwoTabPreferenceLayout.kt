package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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

        // Single source of truth: pager offset drives everything.
        // Goes 0.0 (fully on page 0) → 1.0 (fully on page 1).
        val pageProgress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, 1f)

        val inactiveCorner: Dp = 16.dp
        val activeCorner: Dp = 50.dp

        val primary = MaterialTheme.colorScheme.primary
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        val surface = MaterialTheme.colorScheme.surfaceVariant
        val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(firstPageLabel, secondPageLabel).forEachIndexed { index, pageLabel ->
                // progress = 1f when this tab is fully selected, 0f when not
                val tabProgress = if (index == 0) 1f - pageProgress else pageProgress

                val cornerRadius = lerp(inactiveCorner, activeCorner, tabProgress)
                val containerColor = lerp(surface, primary, tabProgress)
                val contentColor = lerp(onSurface, onPrimary, tabProgress)

                Surface(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(cornerRadius),
                    color = containerColor,
                    contentColor = contentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = pageLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tabProgress > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
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