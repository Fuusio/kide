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
package org.fuusio.kide.app.feature.search.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fuusio.kide.app.feature.about.presentation.OSILicense
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.app.feature.search.presentation.*
import org.fuusio.kide.app.R
import org.fuusio.kide.navigation.ScreenContext

@Composable
fun SearchScreen(ctx: ScreenContext<SearchProcessor>) {
    val state by ctx.processor.states.collectAsStateWithLifecycle()
    val onDispatch = ctx.processor::dispatch
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(ctx.processor.sideEffects) {
        ctx.processor.sideEffects.collect { effect ->
            when (effect) {
                is ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is NavigateToDetails -> {
                    // Details navigation
                }
            }
        }
    }

    SearchScreenContent(
        state = state,
        onMenu = ctx.callback(ScreenContext.MENU),
        onDispatch = onDispatch,
        onOpenLink = { url -> uriHandler.openUri(url) },
        onShareLink = { url ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreenContent(
    state: SearchViewState,
    onMenu: () -> Unit,
    onDispatch: (SearchIntent) -> Unit,
    onOpenLink: (String) -> Unit,
    onShareLink: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val triggerSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDispatch(TriggerSearch)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Projects", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(R.drawable.menu_24px),
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
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onDispatch(UpdateQuery(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search GitHub repositories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { triggerSearch() })
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var langExpanded by remember { mutableStateOf(false) }
                val languages = listOf("Kotlin", "Java", "Swift", "Rust", "Python", "Go")
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { langExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.languageFilter ?: "Language",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any Language") },
                            onClick = {
                                onDispatch(UpdateLanguageFilter(null))
                                langExpanded = false
                            }
                        )
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = {
                                    onDispatch(UpdateLanguageFilter(lang))
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }

                var licenseExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { licenseExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.licenseFilter ?: "License",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = licenseExpanded,
                        onDismissRequest = { licenseExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any License") },
                            onClick = {
                                onDispatch(UpdateLicenseFilter(null))
                                licenseExpanded = false
                            }
                        )
                        OSILicense.entries.forEach { license ->
                            DropdownMenuItem(
                                text = { Text(license.displayName) },
                                onClick = {
                                    onDispatch(UpdateLicenseFilter(license.spdxId))
                                    licenseExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { triggerSearch() },
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("Search")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (state.results.isEmpty()) {
                    Text(
                        text = "No results. Try searching for something!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.results) { project ->
                            ProjectItem(
                                project = project,
                                onSaveToggle = { onDispatch(ToggleSave(project)) },
                                onOpenLink = { onOpenLink(project.htmlUrl) },
                                onShareLink = { onShareLink(project.htmlUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectItem(
    project: Project,
    onSaveToggle: () -> Unit,
    onOpenLink: () -> Unit,
    onShareLink: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onShareLink) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = onSaveToggle) {
                        Icon(
                            imageVector = if (project.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Save",
                            tint = if (project.isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            }

            if (!project.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    project.language?.let { lang ->
                        Text(
                            text = "✦ $lang",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "★ ${project.starsCount}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(onClick = onOpenLink) {
                    Text("GitHub ↗")
                }
            }
        }
    }
}
