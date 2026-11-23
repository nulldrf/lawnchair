package app.lawnchair.ui.preferences.destinations

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.android.msdl.data.model.MSDLToken

private val OuterCorner = 20.dp
private val InnerCorner = 6.dp

private fun segmentedItemShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(OuterCorner)

    index == 0 -> RoundedCornerShape(
        topStart = OuterCorner,
        topEnd = OuterCorner,
        bottomStart = InnerCorner,
        bottomEnd = InnerCorner,
    )

    index == count - 1 -> RoundedCornerShape(
        topStart = InnerCorner,
        topEnd = InnerCorner,
        bottomStart = OuterCorner,
        bottomEnd = OuterCorner,
    )

    else -> RoundedCornerShape(InnerCorner)
}

@Composable
fun SearchProviderPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val msdlPlayer = remember(context) { MSDLPlayerWrapper.INSTANCE.get(context) }
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val adapter = preferenceManager2().hotseatQsbProvider.getAdapter()
    val forceWebsiteAdapter = preferenceManager2().hotseatQsbForceWebsite.getAdapter()
    val currentProvider = adapter.state.value

    val allProviders = remember { QsbSearchProvider.values() }
    var installedMap by remember(context) {
        mutableStateOf(allProviders.associateWith { it.isDownloaded(context) })
    }
    LifecycleResumeEffect(context) {
        installedMap = allProviders.associateWith { it.isDownloaded(context) }
        onPauseOrDispose {}
    }
    val (availableProviders, unavailableProviders) = remember(allProviders, installedMap, currentProvider) {
        allProviders.partition { provider ->
            provider.type != QsbSearchProviderType.APP ||
                installedMap[provider] == true ||
                provider == currentProvider
        }
    }
    var showUnavailable by remember { mutableStateOf(false) }

    PreferenceLayout(
        label = stringResource(R.string.search_provider),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Available providers (local, installed apps, and web-capable providers)
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                availableProviders.forEachIndexed { index, provider ->
                    val appInstalled = installedMap[provider] == true
                    val selected = currentProvider == provider
                    val enabled = provider.type != QsbSearchProviderType.APP || appInstalled
                    val hasAppAndWebsite = provider.type == QsbSearchProviderType.APP_AND_WEBSITE
                    val title = stringResource(id = provider.name)

                    AvailableProviderRow(
                        title = title,
                        iconRes = provider.themedIcon,
                        selected = selected,
                        enabled = enabled,
                        hasAppAndWebsite = hasAppAndWebsite,
                        appInstalled = appInstalled,
                        appSelected = !forceWebsiteAdapter.state.value && appInstalled,
                        sponsored = provider.sponsored,
                        shape = segmentedItemShape(index, availableProviders.size),
                        onClick = {
                            msdlPlayer.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                            adapter.onChange(newValue = provider)
                        },
                        onSelectApp = { forceWebsiteAdapter.onChange(newValue = false) },
                        onSelectWebsite = { forceWebsiteAdapter.onChange(newValue = true) },
                        onInstallApp = { provider.launchOnAppMarket(context = context) },
                        onSponsorClick = {
                            bottomSheetHandler.show {
                                SponsorDisclaimer(title) { bottomSheetHandler.hide() }
                            }
                        },
                        msdlPlayer = msdlPlayer,
                    )
                }
            }

            // Collapsible section for uninstalled app-only providers
            if (unavailableProviders.isNotEmpty()) {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (showUnavailable) 180f else 0f,
                    animationSpec = tween(durationMillis = 200),
                    label = "unavailableChevron",
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(OuterCorner))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable {
                                msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                                showUnavailable = !showUnavailable
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.qsb_search_provider_app_required)} (${unavailableProviders.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                        )
                    }

                    ExpandAndShrink(visible = showUnavailable) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            unavailableProviders.forEachIndexed { index, provider ->
                                UnavailableProviderRow(
                                    title = stringResource(id = provider.name),
                                    iconRes = provider.themedIcon,
                                    shape = segmentedItemShape(index, unavailableProviders.size),
                                    onDownloadClick = {
                                        msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                                        provider.launchOnAppMarket(context = context)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableProviderRow(
    title: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    enabled: Boolean,
    hasAppAndWebsite: Boolean,
    appInstalled: Boolean,
    appSelected: Boolean,
    sponsored: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onSelectApp: () -> Unit,
    onSelectWebsite: () -> Unit,
    onInstallApp: () -> Unit,
    onSponsorClick: () -> Unit,
    msdlPlayer: MSDLPlayerWrapper,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 180),
        label = "providerRowContainer",
    )
    val avatarContainerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 180),
        label = "providerAvatarContainer",
    )
    val avatarIconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "providerAvatarIcon",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProviderIconAvatar(
            resId = iconRes,
            containerColor = avatarContainerColor,
            iconColor = avatarIconColor,
        )

        Text(
            text = title,
            style = if (selected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (sponsored) {
            ClickableIcon(
                painter = painterResource(id = R.drawable.ic_about),
                onClick = onSponsorClick,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (selected && hasAppAndWebsite) {
            ProviderModePill(
                appInstalled = appInstalled,
                appSelected = appSelected,
                onSelectApp = onSelectApp,
                onSelectWebsite = onSelectWebsite,
                onInstallApp = onInstallApp,
                msdlPlayer = msdlPlayer,
            )
        } else if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderModePill(
    appInstalled: Boolean,
    appSelected: Boolean,
    onSelectApp: () -> Unit,
    onSelectWebsite: () -> Unit,
    onInstallApp: () -> Unit,
    msdlPlayer: MSDLPlayerWrapper,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "modePillChevron",
    )

    Box {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                    menuExpanded = true
                }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(id = if (appSelected) R.string.app_label else R.string.website_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
            )
        }

        DropdownMenuPopup(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                containerColor = MenuDefaults.groupStandardContainerColor,
            ) {
                SelectableDropdownMenuItem(
                    selected = appSelected,
                    onClick = {
                        msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        menuExpanded = false
                        if (appInstalled) {
                            onSelectApp()
                        } else {
                            onInstallApp()
                        }
                    },
                    text = { Text(text = stringResource(id = R.string.app_label)) },
                    supportingText = if (!appInstalled) {
                        { Text(text = stringResource(id = R.string.qsb_search_provider_app_required)) }
                    } else {
                        null
                    },
                    shapes = MenuDefaults.itemShape(0, 2),
                    colors = MenuDefaults.selectableItemColors(),
                    leadingIcon = if (!appInstalled) {
                        {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        }
                    } else {
                        null
                    },
                    selectedLeadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
                SelectableDropdownMenuItem(
                    selected = !appSelected,
                    onClick = {
                        msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        menuExpanded = false
                        onSelectWebsite()
                    },
                    text = { Text(text = stringResource(id = R.string.website_label)) },
                    shapes = MenuDefaults.itemShape(1, 2),
                    colors = MenuDefaults.selectableItemColors(),
                    selectedLeadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun UnavailableProviderRow(
    title: String,
    @DrawableRes iconRes: Int,
    shape: Shape,
    onDownloadClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProviderIconAvatar(
            resId = iconRes,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ClickableIcon(
            painter = painterResource(id = R.drawable.ic_download),
            onClick = onDownloadClick,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ProviderIconAvatar(
    @DrawableRes resId: Int,
    containerColor: Color,
    iconColor: Color,
) {
    val context = LocalContext.current
    val drawable = remember(context, resId, iconColor) {
        ResourcesCompat.getDrawable(context.resources, resId, context.theme)
            ?.mutate()
            ?.apply { setTint(iconColor.toArgb()) }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (drawable != null) {
            Image(
                painter = rememberDrawablePainter(drawable),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
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
