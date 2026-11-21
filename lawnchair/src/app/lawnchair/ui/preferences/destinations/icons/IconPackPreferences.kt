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

package app.lawnchair.ui.preferences.destinations.icons

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.invariantDeviceProfile
import app.lawnchair.ui.preferences.components.layout.NestedScrollStretch
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.theme.preferenceGroupColor
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.android.msdl.data.model.MSDLToken

data class IconPackInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
)

@Composable
fun IconPackPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val iconPackAdapter = prefs.iconPackPackage.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.icon_pack),
        modifier = modifier,
        isExpandedScreen = true,
    ) {
        // ── Phone-frame preview ──────────────────────────────────────────────
        HomeScreenMockup(
            iconPackAdapter = iconPackAdapter,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Icon pack selection ──────────────────────────────────────────────
        PreferenceGroup(heading = stringResource(id = R.string.icon_pack)) {
            IconPackGrid(adapter = iconPackAdapter)
        }
    }
}

// ---------------------------------------------------------------------------
// Phone-frame mockup
// ---------------------------------------------------------------------------

/**
 * A centered phone-shaped frame showing the live launcher home screen.
 *
 * Uses a width-only constraint so [DummyLauncherBox]'s internal aspectRatio
 * determines the height — ensuring the border follows the actual content area
 * with no side gaps.
 *
 * 1dp tinted border, no elevation — matching the KernelSU theme-screen look.
 */
@Composable
private fun HomeScreenMockup(
    iconPackAdapter: PreferenceAdapter<String>,
) {
    val primary = MaterialTheme.colorScheme.primary
    val phoneShape = RoundedCornerShape(28.dp)
    val borderColor = primary.copy(alpha = 0.25f)

    val idp = invariantDeviceProfile()

    // DummyLauncherBox internally enforces its own aspectRatio() using the
    // real device profile (dp.deviceProperties.widthPx / heightPx). Any outer
    // container with a different fixed height will mismatch that ratio and
    // produce gaps. The only correct approach is to constrain only the WIDTH
    // and let DummyLauncherBox's internal aspectRatio determine the height —
    // then our clip and border follow whatever size DummyLauncherBox naturally
    // wants to be, with zero mismatch.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthFraction = if (isLandscape) 0.45f else 0.65f

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // WithWallpaper requires ColumnScope as its direct parent.
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction),
        ) {
            WithWallpaper(displayWallpaperButton = false) { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = borderColor, shape = phoneShape)
                        .clip(phoneShape),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    key(iconPackAdapter.state.value) {
                        DummyLauncherLayout(
                            idp = idp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Icon pack grid
// ---------------------------------------------------------------------------

@Composable
fun IconPackGrid(
    adapter: PreferenceAdapter<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val preferenceInteractor = LocalPreferenceInteractor.current
    val iconPacks by preferenceInteractor.iconPacks.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val padding = 12.dp
    val selectedPack = adapter.state.value

    LaunchedEffect(selectedPack) {
        val index = iconPacks.indexOfFirst { it.packageName == selectedPack }
        if (index != -1) lazyListState.scrollToItem(index)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = preferenceGroupColor(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemWidth = getIconPackItemWidth(
                availableWidth = maxWidth.value - padding.value,
                minimumWidth = 80f,
                gutterWidth = padding.value,
            )
            NestedScrollStretch {
                LazyRow(
                    state = lazyListState,
                    horizontalArrangement = Arrangement.spacedBy(padding),
                    contentPadding = PaddingValues(horizontal = padding),
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                ) {
                    items(iconPacks, { it.packageName }) { item ->
                        IconPackItem(
                            item = item,
                            selected = item.packageName == adapter.state.value,
                            modifier = Modifier.width(itemWidth.dp),
                            onClick = {
                                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_HIGH_EMPHASIS)
                                adapter.onChange(item.packageName)
                            },
                        )
                    }
                    item {
                        val toastMessage = stringResource(R.string.error_no_market_or_browser_installed)
                        IconPackItem(
                            label = stringResource(R.string.get_more_icon_packs),
                            icon = { iconModifier ->
                                Box(iconModifier) {
                                    Icon(
                                        imageVector = Icons.Outlined.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp).align(Alignment.Center),
                                    )
                                }
                            },
                            selected = false,
                            modifier = Modifier.width(itemWidth.dp),
                            onClick = {
                                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "market://search?q=icon+pack&c=apps".toUri(),
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                "https://play.google.com/store/search?q=icon+pack&c=apps".toUri(),
                                            ),
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun getIconPackItemWidth(
    availableWidth: Float,
    minimumWidth: Float,
    gutterWidth: Float,
): Float {
    var gutterCount = 2f
    var visibleItemCount = gutterCount + 0.5f
    var iconPackItemWidth = minimumWidth
    while (true) {
        gutterCount += 1f
        visibleItemCount += 1f
        val possibleWidth = (availableWidth - gutterCount * gutterWidth) / visibleItemCount
        if (possibleWidth >= minimumWidth) {
            iconPackItemWidth = possibleWidth
        } else {
            break
        }
    }
    return iconPackItemWidth
}

/**
 * Single icon pack card in the horizontal selector row.
 *
 * Fixed [height] of 96dp ensures every card in the row is the same size
 * regardless of pack name length. The name is capped at one line with
 * ellipsis overflow so a long name can never push a card taller.
 *
 * Unselected items are transparent so they sit flush inside the single
 * outer [PreferenceGroup] card; only the selected pack gets its own
 * [MaterialTheme.colorScheme.primaryContainer] highlight box.
 */
@Composable
fun IconPackItem(
    item: IconPackInfo,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconPackItem(
        label = item.name,
        icon = { iconModifier ->
            Image(
                modifier = iconModifier,
                painter = rememberDrawablePainter(drawable = item.icon),
                contentDescription = null,
            )
        },
        selected = selected,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun IconPackItem(
    label: String,
    icon: @Composable (iconModifier: Modifier) -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier.height(96.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon(
                Modifier
                    .size(40.dp)
                    .padding(bottom = 6.dp),
            )
            Text(
                text = label,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
