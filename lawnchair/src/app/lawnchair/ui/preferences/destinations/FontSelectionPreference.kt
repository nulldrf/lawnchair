package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.ui.AndroidText
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import com.patrykmichalik.opto.core.PreferenceImpl

private const val GOOGLE_SANS_FLEX_FAMILY = "Google Sans Flex"
private val ExposedCorner = 16.dp
private val SeamCorner = 4.dp

private enum class ContentType {
    SPECIMEN,
    FILTERS,
    HEADING,
    FONT,
    EMPTY,
}

private enum class FontSourceFilter {
    ALL,
    CUSTOM,
    BUILTIN,
    GOOGLE,
}

@Composable
fun FontSelection(
    fontPref: PreferenceImpl<FontCache.Font, String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val customFonts by remember { FontCache.INSTANCE.get(context).customFonts }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val builtInFonts = remember(context) {
        val googleSansFlexVariants = HashMap<String, FontCache.Font>()
        googleSansFlexVariants["regular"] = FontCache.ResourceFont(
            context,
            R.font.googlesansflex_variable,
            "$GOOGLE_SANS_FLEX_FAMILY ${context.getString(R.string.font_weight_regular)}",
        )
        googleSansFlexVariants["500"] = FontCache.ResourceFont(
            context,
            R.font.googlesansflex_variable,
            "$GOOGLE_SANS_FLEX_FAMILY ${context.getString(R.string.font_weight_medium)}",
        )
        googleSansFlexVariants["600"] = FontCache.ResourceFont(
            context,
            R.font.googlesansflex_variable,
            "$GOOGLE_SANS_FLEX_FAMILY ${context.getString(R.string.font_weight_semi_bold)}",
        )
        googleSansFlexVariants["700"] = FontCache.ResourceFont(
            context,
            R.font.googlesansflex_variable,
            "$GOOGLE_SANS_FLEX_FAMILY ${context.getString(R.string.font_weight_bold)}",
        )
        listOf(
            FontCache.Family(GOOGLE_SANS_FLEX_FAMILY, googleSansFlexVariants),
            FontCache.Family(FontCache.SystemFont("sans-serif")),
            FontCache.Family(FontCache.SystemFont("sans-serif-medium")),
            FontCache.Family(FontCache.SystemFont("sans-serif-condensed")),
        )
    }

    val googleFonts by produceState(initialValue = emptyList<FontCache.Family>()) {
        value = GoogleFontsListing.INSTANCE.get(context).getFonts().map { font ->
            val variantsMap = HashMap<String, FontCache.Font>()
            val variants = font.variants.toTypedArray()
            font.variants.forEach { variant ->
                variantsMap[variant] = FontCache.GoogleFont(context, font.family, variant, variants)
            }
            FontCache.Family(font.family, variantsMap)
        }
    }

    val adapter = fontPref.getAdapter()
    val selectedFont = adapter.state.value
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(FontSourceFilter.ALL) }

    val trimmedQuery by remember { derivedStateOf { searchQuery.trim().lowercase() } }
    val hasSearch by remember { derivedStateOf { trimmedQuery.isNotEmpty() } }

    val filteredCustom by remember {
        derivedStateOf {
            if (activeFilter != FontSourceFilter.ALL && activeFilter != FontSourceFilter.CUSTOM) {
                emptyList()
            } else if (hasSearch) {
                customFonts.filter { it.matchesQuery(trimmedQuery) }
            } else {
                customFonts
            }
        }
    }
    val filteredBuiltIn by remember {
        derivedStateOf {
            if (activeFilter != FontSourceFilter.ALL && activeFilter != FontSourceFilter.BUILTIN) {
                emptyList()
            } else if (hasSearch) {
                builtInFonts.filter { it.matchesQuery(trimmedQuery) }
            } else {
                builtInFonts
            }
        }
    }
    val filteredGoogle by remember {
        derivedStateOf {
            if (activeFilter != FontSourceFilter.ALL && activeFilter != FontSourceFilter.GOOGLE) {
                emptyList()
            } else if (hasSearch) {
                googleFonts.filter { it.matchesQuery(trimmedQuery) }
            } else {
                googleFonts
            }
        }
    }

    val activeFamily by remember(selectedFont, customFonts, builtInFonts, googleFonts) {
        derivedStateOf {
            customFonts.firstOrNull { it.containsFont(selectedFont) }
                ?: builtInFonts.firstOrNull { it.containsFont(selectedFont) }
                ?: googleFonts.firstOrNull { it.containsFont(selectedFont) }
        }
    }

    val activeSourceLabel = when {
        selectedFont is FontCache.TTFFont -> stringResource(id = R.string.pref_fonts_custom_tag)
        selectedFont is FontCache.GoogleFont -> stringResource(id = R.string.pref_fonts_section_google)
        else -> stringResource(id = R.string.pref_fonts_section_builtin)
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        try {
            FontCache.INSTANCE.get(context).addCustomFont(uri)
        } catch (e: FontCache.AddFontException) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchAddFont() {
        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        request.launch(intent)
    }

    val screenTitle = stringResource(id = fontPrefTitleRes(fontPref.key.name))
    val lazyListState = rememberLazyListState()

    val customHeading = stringResource(id = R.string.pref_fonts_section_custom)
    val builtInHeading = stringResource(id = R.string.pref_fonts_section_builtin)
    val googleHeading = stringResource(id = R.string.pref_fonts_section_google)

    PreferenceScaffold(
        label = screenTitle,
        isExpandedScreen = LocalIsExpandedScreen.current,
        modifier = modifier,
        actions = {
            ResetFontActionButton(
                onClick = {
                    mMSDLPlayerWrapper.playToken(MSDLToken.SUCCESS)
                    adapter.onChange(fontPref.defaultValue)
                },
            )
        },
        bottomBar = {
            BottomFontToolbar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onAddFontClick = ::launchAddFont,
            )
        },
    ) { padding ->
        PreferenceLazyColumn(
            contentPadding = padding,
            state = lazyListState,
        ) {
            if (!hasSearch) {
                item(key = "active_specimen", contentType = ContentType.SPECIMEN) {
                    ActiveFontSpecimenCard(
                        selectedFont = selectedFont,
                        activeFamily = activeFamily,
                        sourceLabel = activeSourceLabel,
                        onSelectVariant = { variant ->
                            adapter.onChange(variant)
                            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            item(key = "source_filters", contentType = ContentType.FILTERS) {
                FontSourceFilterRow(
                    activeFilter = activeFilter,
                    customCount = customFonts.size,
                    onFilterSelect = { filter ->
                        activeFilter = filter
                        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            if (filteredCustom.isNotEmpty()) {
                fontSectionItems(
                    sectionKey = "custom",
                    heading = customHeading,
                    families = filteredCustom,
                    adapter = adapter,
                    onDeleteFactory = { family ->
                        {
                            if (family.containsFont(adapter.state.value)) {
                                adapter.onChange(fontPref.defaultValue)
                            }
                            (family.default as? FontCache.TTFFont)?.delete()
                        }
                    },
                )
            }

            if (filteredBuiltIn.isNotEmpty()) {
                fontSectionItems(
                    sectionKey = "builtin",
                    heading = builtInHeading,
                    families = filteredBuiltIn,
                    adapter = adapter,
                )
            }

            if (filteredGoogle.isNotEmpty()) {
                fontSectionItems(
                    sectionKey = "google",
                    heading = googleHeading,
                    families = filteredGoogle,
                    adapter = adapter,
                )
            }

            val isEmpty = filteredCustom.isEmpty() && filteredBuiltIn.isEmpty() && filteredGoogle.isEmpty()
            if (isEmpty && (hasSearch || activeFilter == FontSourceFilter.CUSTOM)) {
                item(key = "empty_state", contentType = ContentType.EMPTY) {
                    EmptyFontsCard(
                        isCustomFilter = !hasSearch && activeFilter == FontSourceFilter.CUSTOM,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            item(key = "bottom_breathing") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun LazyListScope.fontSectionItems(
    sectionKey: String,
    heading: String,
    families: List<FontCache.Family>,
    adapter: PreferenceAdapter<FontCache.Font>,
    onDeleteFactory: ((FontCache.Family) -> (() -> Unit))? = null,
) {
    item(key = "heading_$sectionKey", contentType = ContentType.HEADING) {
        PreferenceGroupHeading(heading = heading)
    }
    itemsIndexed(
        items = families,
        key = { index, family -> "${sectionKey}_${family.displayName}_$index" },
        contentType = { _, _ -> ContentType.FONT },
    ) { index, family ->
        if (index > 0) {
            Spacer(modifier = Modifier.height(ListItemDefaults.SegmentedGap))
        }
        FontSelectionRow(
            adapter = adapter,
            family = family,
            cutTop = index > 0,
            cutBottom = index < families.lastIndex,
            onDelete = onDeleteFactory?.invoke(family),
        )
    }
}

@Composable
private fun ActiveFontSpecimenCard(
    selectedFont: FontCache.Font,
    activeFamily: FontCache.Family?,
    sourceLabel: String,
    onSelectVariant: (FontCache.Font) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    DisposableEffect(activeFamily) {
        if (activeFamily != null && activeFamily.variants.size > 1) {
            val fontCache = FontCache.INSTANCE.get(context)
            activeFamily.variants.values.forEach { fontCache.preloadFont(it) }
        }
        onDispose { }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(id = R.string.pref_fonts_active_font),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = activeFamily?.let { formatFamilyTitle(it) }
                        ?: cleanFontFileName(selectedFont.fullDisplayName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = selectedFont.composeFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(id = R.string.pref_fonts_preview_sample),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = selectedFont.composeFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (activeFamily != null && activeFamily.variants.size > 1) {
                val selectedVariantLabel = variantDisplayLabel(activeFamily, selectedFont)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = activeFamily.sortedVariants,
                        key = { it.fullDisplayName },
                    ) { variant ->
                        val label = variantDisplayLabel(activeFamily, variant)
                        val isVariantSelected = variant == selectedFont ||
                            (selectedFont !in activeFamily.variants.values && label == selectedVariantLabel)
                        val bgColor by animateColorAsState(
                            targetValue = if (isVariantSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            label = "weightChipBg",
                        )
                        val fgColor by animateColorAsState(
                            targetValue = if (isVariantSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = "weightChipFg",
                        )
                        Surface(
                            onClick = { onSelectVariant(variant) },
                            shape = CircleShape,
                            color = bgColor,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = variant.composeFontFamily,
                                color = fgColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontSourceFilterRow(
    activeFilter: FontSourceFilter,
    customCount: Int,
    onFilterSelect: (FontSourceFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val customLabel = if (customCount > 0) {
        "${stringResource(id = R.string.pref_fonts_custom_tag)} ($customCount)"
    } else {
        stringResource(id = R.string.pref_fonts_custom_tag)
    }
    val filters = listOf(
        FontSourceFilter.ALL to stringResource(id = R.string.pref_fonts_filter_all),
        FontSourceFilter.CUSTOM to customLabel,
        FontSourceFilter.BUILTIN to stringResource(id = R.string.pref_fonts_section_builtin),
        FontSourceFilter.GOOGLE to stringResource(id = R.string.pref_fonts_section_google),
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.first.name }) { (filter, label) ->
            val selected = activeFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelect(filter) },
                label = { Text(text = label) },
                shape = RoundedCornerShape(12.dp),
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun FontSelectionRow(
    adapter: PreferenceAdapter<FontCache.Font>,
    family: FontCache.Family,
    cutTop: Boolean,
    cutBottom: Boolean,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val selectedFont = adapter.state.value
    val selected = family.containsFont(selectedFont)
    val previewFont = if (selected) selectedFont else family.default

    var pressed by remember { mutableStateOf(false) }

    val restingTop = if (selected || !cutTop) ExposedCorner else SeamCorner
    val restingBottom = if (selected || !cutBottom) ExposedCorner else SeamCorner

    val topCorner by animateDpAsState(
        targetValue = if (pressed) ExposedCorner else restingTop,
        label = "fontRowTopCorner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (pressed) ExposedCorner else restingBottom,
        label = "fontRowBottomCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "fontRowContainerColor",
    )
    val badgeBg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "fontRowBadgeBg",
    )
    val badgeFg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "fontRowBadgeFg",
    )

    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val subtitle = buildFontSubtitle(
        family = family,
        selected = selected,
        selectedFont = selectedFont,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
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
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    adapter.onChange(family.default)
                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = previewFont.composeFontFamily,
                    color = badgeFg,
                    maxLines = 1,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = formatFamilyTitle(family),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = previewFont.composeFontFamily,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selected && family.variants.size > 1) {
                    VariantDropdown(adapter = adapter, family = family)
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else if (onDelete == null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                shape = CircleShape,
                            ),
                    )
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = {
                            onDelete()
                            mMSDLPlayerWrapper.playToken(MSDLToken.SUCCESS)
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(id = R.string.action_delete),
                            tint = subtitleColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomFontToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onAddFontClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.pref_fonts_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() },
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = { onQueryChange("") },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(id = android.R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddFontClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(id = R.string.pref_fonts_add_fonts),
            )
        }
    }
}

@Composable
private fun EmptyFontsCard(
    isCustomFilter: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(id = R.string.pref_fonts_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    id = if (isCustomFilter) {
                        R.string.pref_fonts_empty_custom_summary
                    } else {
                        R.string.pref_fonts_empty_search_summary
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetFontActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resetLabel = stringResource(id = R.string.action_reset)
    val tooltipState = rememberTooltipState()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = resetLabel)
            }
        },
        state = tooltipState,
        modifier = modifier,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = resetLabel,
            )
        }
    }
}

@Composable
private fun VariantDropdown(
    adapter: PreferenceAdapter<FontCache.Font>,
    family: FontCache.Family,
    modifier: Modifier = Modifier,
) {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    Box(modifier = modifier.wrapContentWidth()) {
        val selectedFont = adapter.state.value
        val selectedVariantLabel = variantDisplayLabel(family, selectedFont)
        var showVariants by remember { mutableStateOf(false) }

        val context = LocalContext.current
        DisposableEffect(family) {
            val fontCache = FontCache.INSTANCE.get(context)
            family.variants.values.forEach { fontCache.preloadFont(it) }
            onDispose { }
        }

        Surface(
            onClick = {
                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                showVariants = true
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AndroidText(
                    modifier = Modifier.wrapContentWidth(),
                    update = {
                        it.text = selectedVariantLabel
                        it.setFont(selectedFont)
                    },
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        val sortedVariants = family.sortedVariants
        DropdownMenuPopup(
            expanded = showVariants,
            onDismissRequest = { showVariants = false },
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                containerColor = MenuDefaults.groupStandardContainerColor,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                sortedVariants.forEachIndexed { index, font ->
                    val label = variantDisplayLabel(family, font)
                    val isSelected = font == selectedFont ||
                        (selectedFont !in family.variants.values && label == selectedVariantLabel)
                    SelectableDropdownMenuItem(
                        selected = isSelected,
                        onClick = {
                            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                            adapter.onChange(font)
                            showVariants = false
                        },
                        text = {
                            Text(
                                text = label,
                                fontFamily = font.composeFontFamily,
                            )
                        },
                        shapes = MenuDefaults.itemShape(index, sortedVariants.size),
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
}

@Composable
private fun buildFontSubtitle(
    family: FontCache.Family,
    selected: Boolean,
    selectedFont: FontCache.Font,
): String {
    val defaultFont = family.default
    return when {
        defaultFont is FontCache.TTFFont -> {
            val ext = defaultFont.fullDisplayName.substringAfterLast('.', "").uppercase()
            val customTag = stringResource(id = R.string.pref_fonts_custom_tag)
            if (ext == "TTF" || ext == "OTF") "$ext · $customTag" else customTag
        }

        family.variants.size > 1 -> {
            val stylesText = stringResource(id = R.string.pref_fonts_style_count, family.variants.size)
            if (selected) {
                val activeVariant = variantDisplayLabel(family, selectedFont)
                if (activeVariant.isNotEmpty()) "$activeVariant · $stylesText" else stylesText
            } else {
                stylesText
            }
        }

        defaultFont is FontCache.SystemFont -> defaultFont.family

        else -> stringResource(id = R.string.pref_fonts_system_tag)
    }
}

private fun FontCache.Family.containsFont(font: FontCache.Font): Boolean {
    if (variants.values.any { it == font }) return true
    return font is FontCache.ResourceFont &&
        displayName == GOOGLE_SANS_FLEX_FAMILY &&
        font.fullDisplayName.startsWith(GOOGLE_SANS_FLEX_FAMILY)
}

private fun FontCache.Family.matchesQuery(lowerCaseQuery: String): Boolean {
    if (displayName.lowercase().contains(lowerCaseQuery)) return true
    return formatFamilyTitle(this).lowercase().contains(lowerCaseQuery)
}

private fun formatFamilyTitle(family: FontCache.Family): String {
    val defaultFont = family.default
    return when (defaultFont) {
        is FontCache.TTFFont -> cleanFontFileName(family.displayName)

        is FontCache.SystemFont -> when (defaultFont.family) {
            "sans-serif" -> "Sans Serif"
            "sans-serif-medium" -> "Sans Serif Medium"
            "sans-serif-condensed" -> "Sans Serif Condensed"
            else -> family.displayName
        }

        else -> family.displayName
    }
}

private fun cleanFontFileName(name: String): String {
    return name
        .removeSuffix(".ttf")
        .removeSuffix(".TTF")
        .removeSuffix(".otf")
        .removeSuffix(".OTF")
}

private fun variantDisplayLabel(
    family: FontCache.Family,
    font: FontCache.Font,
): String {
    val candidate = font.displayName.removePrefix(family.displayName).trim()
    return candidate.ifEmpty {
        font.fullDisplayName.removePrefix(family.displayName).trim()
    }
}

private fun fontPrefTitleRes(prefKey: String): Int = when (prefKey) {
    "font_workspace" -> R.string.fontWorkspace
    "font_heading" -> R.string.fontHeading
    "font_heading_medium" -> R.string.fontHeadingMedium
    "font_body" -> R.string.fontBody
    "font_body_medium" -> R.string.fontBodyMedium
    else -> R.string.font_picker_label
}
