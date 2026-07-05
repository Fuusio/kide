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
package org.fuusio.kide.app.feature.browser.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.app.feature.browser.presentation.*
import org.fuusio.kide.app.feature.details.navigation.DetailsNavKey
import org.fuusio.kide.navigation.ScreenContext

@Composable
fun BrowserScreen(ctx: ScreenContext<BrowserProcessor>) {
    val state by ctx.processor.states.collectAsStateWithLifecycle()
    val onDispatch = ctx.processor::dispatch
    val context = LocalContext.current

    LaunchedEffect(ctx.processor.sideEffects) {
        ctx.processor.sideEffects.collect { effect ->
            when (effect) {
                is ShowBrowserToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is NavigateToLocalDetails -> {
                    ctx.navigateTo(DetailsNavKey(effect.projectId))
                }
            }
        }
    }

    BrowserScreenContent(
        state = state,
        onMenu = ctx.callback(ScreenContext.MENU),
        onDispatch = onDispatch,
        onNavigateToDetails = { projectId ->
            ctx.navigateTo(DetailsNavKey(projectId))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreenContent(
    state: BrowserViewState,
    onMenu: () -> Unit,
    onDispatch: (BrowserIntent) -> Unit,
    onNavigateToDetails: (Long) -> Unit
) {
    LaunchedEffect(Unit) {
        onDispatch(LoadSaved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Libraries", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onDispatch(UpdateLocalSearchQuery(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search saved libraries...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedLabel == null,
                        onClick = { onDispatch(SelectLocalLabel(null)) },
                        label = { Text("All") }
                    )
                }
                items(state.labels) { label ->
                    FilterChip(
                        selected = state.selectedLabel?.name == label.name,
                        onClick = { onDispatch(SelectLocalLabel(label)) },
                        label = { Text(label.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (state.filteredProjects.isEmpty()) {
                    Text(
                        text = if (state.projects.isEmpty()) "No saved libraries yet. Go search and save some!"
                        else "No results match your filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filteredProjects) { project ->
                            SavedProjectItem(
                                project = project,
                                onClick = { onNavigateToDetails(project.id) },
                                onDelete = { onDispatch(RemoveLocalProject(project)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedProjectItem(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
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

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!project.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (project.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    project.labels.forEach { label ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(label.name) }
                        )
                    }
                }
            }
        }
    }
}
