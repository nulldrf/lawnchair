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

package app.lawnchair.ui.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import app.lawnchair.smartspace.provider.OnboardingProvider
import app.lawnchair.ui.preferences.components.layout.CollapsingToolbarCallbacks
import app.lawnchair.ui.preferences.components.layout.LocalCollapsingToolbar
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.google.accompanist.adaptive.calculateDisplayFeatures
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.serialization.json.Json

class PreferenceActivity : FragmentActivity() {

    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var actionsFrame: FrameLayout

    // Tracks current onSurfaceColor so we can tint nav icon after it's set
    private var currentOnSurfaceColor: Int = android.graphics.Color.BLACK

    private var actionsContent by mutableStateOf<(@Composable () -> Unit)>({})

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themedCtx = ContextThemeWrapper(
            this,
            MaterialR.style.Theme_Material3_DayNight_NoActionBar,
        )

        val root = LayoutInflater.from(themedCtx)
            .inflate(R.layout.lawnchair_preference_activity, null, false)

        setContentView(root)

        appBarLayout = root.findViewById(R.id.preference_appbar)
        collapsingToolbar = root.findViewById(R.id.preference_collapsing_toolbar)
        toolbar = root.findViewById(R.id.preference_toolbar)
        actionsFrame = root.findViewById(R.id.preference_toolbar_actions)
        val contentFrame = root.findViewById<FrameLayout>(R.id.preference_content_frame)

        // Apply bottom inset
        ViewCompat.setOnApplyWindowInsetsListener(contentFrame) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // Actions ComposeView in toolbar
        val actionsComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            setContent {
                LawnchairTheme {
                    actionsContent()
                }
            }
        }
        actionsFrame.addView(actionsComposeView)

        val initialRoute: PreferenceRoute? =
            intent.getStringExtra(EXTRA_DESTINATION_ROUTE)?.let { routeString ->
                try {
                    Json.decodeFromString<PreferenceRoute>(routeString)
                } catch (e: Exception) {
                    null
                }
            }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setContent {
                LawnchairTheme {
                    ApplyToolbarColors()

                    val callbacks = CollapsingToolbarCallbacks(
                        setTitle = { title ->
                            collapsingToolbar.title = title
                            // Expand toolbar on each new screen
                            appBarLayout.setExpanded(true, true)
                        },
                        setBackArrowVisible = { visible ->
                            if (visible) {
                                toolbar.setNavigationIcon(R.drawable.ic_back)
                                // Apply tint immediately after setting icon
                                toolbar.navigationIcon?.setTint(currentOnSurfaceColor)
                                toolbar.setNavigationOnClickListener {
                                    onBackPressedDispatcher.onBackPressed()
                                }
                            } else {
                                toolbar.navigationIcon = null
                            }
                        },
                        setActions = { actionsContent = it },
                    )

                    CompositionLocalProvider(LocalCollapsingToolbar provides callbacks) {
                        Preferences(
                            windowSizeClass = calculateWindowSizeClass(this@PreferenceActivity),
                            displayFeatures = calculateDisplayFeatures(this@PreferenceActivity),
                            startDestination = initialRoute,
                            intent = intent,
                        )
                    }
                }
            }
        }
        contentFrame.addView(composeView)

        LauncherPrefs.getPrefs(this).edit {
            putBoolean(OnboardingProvider.PREF_HAS_OPENED_SETTINGS, true)
        }
    }

    @Composable
    private fun ApplyToolbarColors() {
        val surfaceColor =
            androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
        val surfaceContainerColor =
            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.toArgb()
        val onSurfaceColor =
            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()

        // Store so setBackArrowVisible can apply it after setting the icon
        currentOnSurfaceColor = onSurfaceColor

        appBarLayout.setBackgroundColor(surfaceColor)
        collapsingToolbar.setContentScrimColor(surfaceContainerColor)
        collapsingToolbar.setCollapsedTitleTextColor(onSurfaceColor)
        collapsingToolbar.setExpandedTitleColor(onSurfaceColor)
        toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Tint existing nav icon if present
        toolbar.navigationIcon?.setTint(onSurfaceColor)
    }

    companion object {
        private const val EXTRA_DESTINATION_ROUTE =
            "app.lawnchair.ui.preferences.DESTINATION_ROUTE"

        fun createIntent(context: Context, destination: PreferenceRoute): Intent {
            val intent = Intent(context, PreferenceActivity::class.java)
            val routeString = Json.encodeToString(destination)
            intent.putExtra(EXTRA_DESTINATION_ROUTE, routeString)
            return intent
        }
    }
}
