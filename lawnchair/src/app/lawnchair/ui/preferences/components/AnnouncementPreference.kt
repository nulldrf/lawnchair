package app.lawnchair.ui.preferences.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import com.patrykmichalik.opto.core.firstBlocking
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.data.liveinfo.model.Announcement
import app.lawnchair.ui.util.addIf
import com.android.launcher3.R
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun AnnouncementPreference() {
    val liveInformationManager = liveInformationManager()

    val coroutineScope = rememberCoroutineScope()
    val enabled by liveInformationManager.enabled.asState()
    val showAnnouncements by liveInformationManager.showAnnouncements.asState()
    val dismissedAnnouncementIds by liveInformationManager.dismissedAnnouncementIds.asState()
    val liveInformation by liveInformationManager.liveInformation.asState()

    val announcements = remember(liveInformation, dismissedAnnouncementIds) {
        liveInformation.announcements.filter { it.id !in dismissedAnnouncementIds }
    }

    if (enabled && showAnnouncements) {
        AnnouncementPreference(
            announcements = announcements,
            onDismiss = { announcement ->
                val dismissed = dismissedAnnouncementIds.toMutableSet().apply { add(announcement.id) }
                coroutineScope.launch { liveInformationManager.dismissedAnnouncementIds.set(dismissed) }
            },
        )
    }
}

@Composable
fun AnnouncementPreference(
    announcements: List<Announcement>,
    onDismiss: (Announcement) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        announcements.forEachIndexed { index, announcement ->
            var dismissed by rememberSaveable { mutableStateOf(false) }
            val visible = announcement.shouldBeVisible && !dismissed

            AnnouncementItem(visible, announcement) {
                onDismiss(announcement)
                dismissed = true
            }
        }
    }
}

@Composable
private fun AnnouncementItem(
    visible: Boolean,
    announcement: Announcement,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    ExpandAndShrink(
        modifier = modifier,
        visible = visible,
    ) {
        AnnouncementItemContent(
            text = announcement.text,
            url = announcement.url,
            icon = announcement.iconVector,
            onClose = onClose,
        )
    }
}

@Composable
private fun AnnouncementItemContent(
    text: String,
    url: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val prefs2 = preferenceManager2()
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    val itemWidth = remember { mutableStateOf(0) }

    // Fade starts at 20% of width travelled, reaches 0 at 100%
    val alpha = remember(offsetX.value, itemWidth.value) {
        val width = itemWidth.value.toFloat().coerceAtLeast(1f)
        val normalized = abs(offsetX.value) / width
        when {
            normalized < 0.2f -> 1f
            normalized >= 1f -> 0f
            else -> 1f - (normalized - 0.2f) / 0.8f
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { itemWidth.value = it.width }
            .graphicsLayer {
                translationX = offsetX.value
                this.alpha = alpha
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val width = itemWidth.value.toFloat()
                        if (abs(offsetX.value) > width * 0.35f) {
                            coroutineScope.launch {
                                val target = if (offsetX.value > 0) width else -width
                                offsetX.animateTo(
                                    targetValue = target,
                                    animationSpec = tween(durationMillis = 180),
                                )
                                if (prefs2.hapticFeedback.firstBlocking()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                                onClose()
                            }
                        } else {
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                )
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                )
            },
    ) {
        AnnouncementPreferenceItemContent(
            text = text,
            url = url,
            icon = icon,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnnouncementPreferenceItemContent(
    text: String,
    url: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasLink = !url.isNullOrBlank()

    PreferenceTemplate(
        modifier = modifier
            .fillMaxWidth()
            .addIf(hasLink) {
                clickable {
                    val webpage = Uri.parse(url)
                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
            },
        shapes = ListItemDefaults.shapes().copy(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        title = {},
        description = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                color = MaterialTheme.colorScheme.background,
            )
        },
        startWidget = {
            Icon(
                imageVector = icon,
                tint = MaterialTheme.colorScheme.background,
                contentDescription = null,
            )
        },
    )
}
