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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.content.edit
import app.lawnchair.smartspace.provider.OnboardingProvider
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.google.accompanist.adaptive.calculateDisplayFeatures
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import com.google.android.material.R as MaterialR

class PreferenceActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm the scaffold layout on a background thread so the first
        // navigation to any destination doesn't pay the cold LayoutInflater + JIT
        // cost on the main thread. The inflated view is discarded — we only care
        // about populating the LayoutInflater's view class cache and triggering JIT
        // compilation of AppBarLayout, CollapsingToolbarLayout, etc.
        // This runs while the user sees the dashboard, so by the time they tap any
        // entry the factory completes in <5 ms instead of 50–100 ms.
        val appContext = applicationContext
        Executors.newSingleThreadExecutor().execute {
            try {
                val themedCtx = ContextThemeWrapper(
                    appContext,
                    MaterialR.style.Theme_Material3_DayNight_NoActionBar,
                )
                LayoutInflater.from(themedCtx)
                    .inflate(R.layout.lawnchair_preference_scaffold, null, false)
            } catch (_: Exception) {
                // Pre-warm is best-effort — any failure is silent and harmless.
            }
        }

        val initialRoute: PreferenceRoute? = intent.getStringExtra(EXTRA_DESTINATION_ROUTE)?.let { routeString ->
            try {
                Json.decodeFromString<PreferenceRoute>(routeString)
            } catch (e: Exception) {
                null // Fallback to default if deserialization fails
            }
        }

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                Preferences(
                    windowSizeClass = calculateWindowSizeClass(this),
                    displayFeatures = calculateDisplayFeatures(this),
                    startDestination = initialRoute,
                    intent = intent,
                )
            }
        }
        LauncherPrefs.getPrefs(this).edit {
            putBoolean(
                OnboardingProvider.PREF_HAS_OPENED_SETTINGS,
                true,
            )
        }
    }

    companion object {

        private const val EXTRA_DESTINATION_ROUTE = "app.lawnchair.ui.preferences.DESTINATION_ROUTE"

        fun createIntent(context: Context, destination: PreferenceRoute): Intent {
            val intent = Intent(context, PreferenceActivity::class.java)
            val routeString = Json.encodeToString(destination)
            intent.putExtra(EXTRA_DESTINATION_ROUTE, routeString)
            return intent
        }
    }
}
