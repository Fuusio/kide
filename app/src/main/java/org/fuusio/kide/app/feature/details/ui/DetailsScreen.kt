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
package org.fuusio.kide.app.feature.details.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fuusio.kide.app.feature.about.presentation.OSILicense
import org.fuusio.kide.app.feature.details.presentation.*
import org.fuusio.kide.navigation.ScreenContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(ctx: ScreenContext<DetailsProcessor>) {
    val state by ctx.processor.states.collectAsStateWithLifecycle()
    val onDispatch = ctx.processor::dispatch
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(ctx.processor.sideEffects) {
        ctx.processor.sideEffects.collect { effect ->
            when (effect) {
                is ShowDetailsToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is NavigateBack -> {
                    ctx.onBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = ctx.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.project?.let { project ->
                        IconButton(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, project.htmlUrl)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.project == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Repository details not found.")
            }
        } else {
            val project = state.project!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = project.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    project.language?.let { lang ->
                        SuggestionChip(onClick = {}, label = { Text("✦ $lang") })
                    }
                    SuggestionChip(onClick = {}, label = { Text("★ ${project.starsCount}") })
                    SuggestionChip(onClick = {}, label = { Text("⑂ ${project.forksCount}") })
                }

                Spacer(modifier = Modifier.height(16.dp))

                project.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                project.licenseSpdxId?.let { spdx ->
                    Text(
                        text = "License: $spdx",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val licenseUrl = OSILicense.entries.find { it.spdxId == spdx }?.url
                            uriHandler.openUri(licenseUrl ?: "https://opensource.org/licenses")
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = { uriHandler.openUri(project.htmlUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View on GitHub ↗")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Labels & Categorization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val keyboardController = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current

                var newLabelText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newLabelText,
                        onValueChange = { newLabelText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("New label name") }
                    )
                    Button(
                        onClick = {
                            if (newLabelText.isNotBlank()) {
                                onDispatch(AddLabelToProject(newLabelText.trim()))
                                newLabelText = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    project.labels.forEach { label ->
                        InputChip(
                            selected = true,
                            onClick = { onDispatch(RemoveLabelFromProject(label.name)) },
                            label = { Text(label.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove label",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Personal Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.noteInput,
                    onValueChange = { onDispatch(UpdateNotesText(it)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Write personal notes or annotations about this repository here...") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onDispatch(SaveNotes)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Notes")
                }
            }
        }
    }
}
