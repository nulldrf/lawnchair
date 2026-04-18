/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
fun PreferenceScaffold(
    label: String,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier,
    backArrowVisible: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = { BottomSpacer() },
    content: @Composable (PaddingValues) -> Unit,
) {
    val toolbar = LocalCollapsingToolbar.current

    // LaunchedEffect(key) runs every time the key changes — guaranteed to fire
    // on every navigation even if the composable doesn't fully recompose.
    // SideEffect was unreliable for cross-screen navigation.
    LaunchedEffect(label) {
        toolbar?.setTitle(label)
    }
    LaunchedEffect(backArrowVisible) {
        toolbar?.setBackArrowVisible(backArrowVisible)
    }
    // actions is a lambda — use Unit as key so it always updates on composition
    LaunchedEffect(Unit) {
        toolbar?.setActions { Row { actions() } }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content(PaddingValues())
    }
}
