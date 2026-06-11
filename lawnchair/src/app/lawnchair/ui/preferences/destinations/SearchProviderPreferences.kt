package app.lawnchair.ui.preferences.destinations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.qsb.providers.QsbSearchProviderType
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.util.LocalBottomSheetHandler
import com.android.launcher3.R

private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun SearchProviderPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val adapter = preferenceManager2().hotseatQsbProvider.getAdapter()
    val forceWebsiteAdapter = preferenceManager2().hotseatQsbForceWebsite.getAdapter()

    PreferenceLayout(
        label = stringResource(R.string.search_provider),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QsbSearchProvider.values().forEach { provider ->
                val appInstalled = provider.isDownloaded(context)
                val selected = adapter.state.value == provider
                val hasAppAndWebsite = provider.type == QsbSearchProviderType.APP_AND_WEBSITE
                val showDownloadButton = provider.type == QsbSearchProviderType.APP && !appInstalled
                val enabled = provider.type != QsbSearchProviderType.APP || appInstalled
                val title = stringResource(id = provider.name)

                val cardColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    animationSpec = tween(durationMillis = 200),
                    label = "cardColor",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(cardColor),
                ) {
                    // Main provider row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { adapter.onChange(newValue = provider) }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (showDownloadButton) {
                            Text(
                                text = stringResource(id = R.string.qsb_search_provider_app_required),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            ClickableIcon(
                                painter = painterResource(id = R.drawable.ic_download),
                                onClick = { provider.launchOnAppMarket(context = context) },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (provider.sponsored) {
                            ClickableIcon(
                                painter = painterResource(id = R.drawable.ic_about),
                                onClick = {
                                    bottomSheetHandler.show {
                                        SponsorDisclaimer(title) { bottomSheetHandler.hide() }
                                    }
                                },
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    // App / Website sub-options
                    ExpandAndShrink(visible = selected && hasAppAndWebsite) {
                        val dividerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                        val appSelected = !forceWebsiteAdapter.state.value && appInstalled
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(dividerColor),
                        ) {
                            // App option
                            SubOption(
                                label = stringResource(id = R.string.app_label),
                                selected = appSelected,
                                enabled = appInstalled,
                                modifier = Modifier.weight(1f),
                                onClick = { forceWebsiteAdapter.onChange(newValue = false) },
                                endContent = if (!appInstalled) {
                                    {
                                        ClickableIcon(
                                            painter = painterResource(R.drawable.ic_download),
                                            onClick = { provider.launchOnAppMarket(context = context) },
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                } else null,
                            )
                            VerticalDivider(color = dividerColor, thickness = 3.dp)
                            // Website option
                            SubOption(
                                label = stringResource(id = R.string.website_label),
                                selected = !appSelected,
                                enabled = true,
                                modifier = Modifier.weight(1f),
                                onClick = { forceWebsiteAdapter.onChange(newValue = true) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    endContent: (@Composable () -> Unit)? = null,
) {
    val bgColor = MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = modifier
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
        )
        endContent?.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SponsorDisclaimer(
    sponsor: String,
    modifier: Modifier = Modifier,
    onAcknowledge: () -> Unit,
) {
    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(
                onClick = onAcknowledge,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalTextStyle provides MaterialTheme.typography.bodyLarge,
        ) {
            Text(
                text = stringResource(id = R.string.search_provider_sponsored_description, sponsor),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
            )
        }
    }
}
