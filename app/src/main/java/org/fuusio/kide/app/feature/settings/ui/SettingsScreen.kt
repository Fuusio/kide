/* Kide
 *
 * Copyright 2025 - 2026 Marko Salmela.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fuusio.kide.app.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fuusio.kide.app.domain.entity.DarkMode
import org.fuusio.kide.app.feature.settings.presentation.*
import org.fuusio.kide.navigation.ScreenContext

@Composable
fun SettingsScreen(ctx: ScreenContext<SettingsProcessor>) {
    val state by ctx.processor.states.collectAsStateWithLifecycle()
    val onDispatch = ctx.processor::dispatch

    SettingsScreenContent(
        state = state,
        onMenu = ctx.callback(ScreenContext.MENU),
        onDispatch = onDispatch
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    state: SettingsViewState,
    onMenu: () -> Unit,
    onDispatch: (SettingsIntent) -> Unit
) {
    LaunchedEffect(Unit) {
        onDispatch(LoadSettings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(org.fuusio.kide.app.R.drawable.menu_24px),
                            contentDescription = "Menu"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Appearance Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(Modifier.selectableGroup()) {
                DarkMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (state.darkMode == mode),
                                onClick = { onDispatch(UpdateDarkMode(mode)) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (state.darkMode == mode),
                            onClick = null
                        )
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Search Defaults",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Default Programming Language",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.defaultLanguage,
                onValueChange = { onDispatch(UpdateDefaultLanguage(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. Kotlin, Java, Rust") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "GitHub Search Result Limit: ${state.resultsLimit}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = state.resultsLimit.toFloat(),
                onValueChange = { onDispatch(UpdateResultsLimit(it.toInt())) },
                valueRange = 10f..100f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
