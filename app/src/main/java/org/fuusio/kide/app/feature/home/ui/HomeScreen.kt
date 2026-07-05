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

package org.fuusio.kide.app.feature.home.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fuusio.kide.navigation.ScreenContext
import org.fuusio.kide.app.feature.home.presentation.HomeProcessor
import org.fuusio.kide.app.feature.home.presentation.HomeIntent
import org.fuusio.kide.app.feature.home.presentation.HomeViewState
import org.fuusio.kide.app.feature.home.presentation.OnLaunched

@Composable
fun HomeScreen(ctx: ScreenContext<HomeProcessor>) {
    val state by ctx.processor.states.collectAsStateWithLifecycle()
    val onDispatch = ctx.processor::dispatch
    HomeScreenContext(
        state = state,
        onMenu = ctx.callback(ScreenContext.MENU),
        onDispatch = onDispatch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContext(
    state: HomeViewState,
    onMenu: (() -> Unit),
    onDispatch: (HomeIntent) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        onDispatch(OnLaunched)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kide Home", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(org.fuusio.kide.app.R.drawable.menu_24px),
                            contentDescription = "Menu",
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to Kide",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "A modern, lightweight framework designed for Kotlin Multiplatform and Android development, implementing MVI architecture and Clean Architecture principles.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Core Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FeatureItem(
                title = "✦ Core MVI (Model-View-Intent)",
                description = "Strictly unidirectional data flows implemented in a coroutines-only core module. Provides DSL-level ergonomics (`reduce`/`async`/`sideEffect`) with strict execution guarantees (lossless intents, dispatch-ordered reducers, exactly-once buffered effects). Features native coroutine cancellation keys (`async(cancellationKey = \"...\") {}`) to restart operations, and a uniquely testable intent-to-action mapping step (`map()`) letting you assert state transitions without executing side effects."
            )

            FeatureItem(
                title = "✦ Resilient Error Handling",
                description = "Exceptions thrown while mapping intents or executing actions never kill the processor: errors are logged, reported to interceptors and to an overridable `onError` hook, and the intent loop keeps running. Cancellation is respected and error state or side effects can be produced in response."
            )

            FeatureItem(
                title = "✦ Observability Built In",
                description = "`KideInterceptor` hooks into the full MVI lifecycle (intents, mapped actions, state changes, side effects, errors) for logging, analytics, or debugging. The zero-dependency `KideLog` facade adds severity levels, automatic class-based tagging, and lazy message evaluation, and plugs into any backend such as Timber."
            )

            FeatureItem(
                title = "✦ ViewState Persistence",
                description = "Opt-in persistence of a screen's `ViewState` across process death using plain kotlinx-serialization — expose a serializer on the navigation key and mark transient fields `@Transient`. State is snapshotted lazily (never per emission) and restored before first composition. This very app persists the Search screen's query and filters."
            )

            FeatureItem(
                title = "✦ Clean Architecture (Optional)",
                description = "An optional extension module (`kide-clean-architecture`) defining standardized boundaries using repository contracts, stateless use cases, and stateful logic controllers. The core library is completely independent of it."
            )

            FeatureItem(
                title = "✦ Type-Safe Navigation (Optional)",
                description = "An optional extension module (`kide-navigation`) based on Navigation 3 providing type-safe navigation keys that support parameter serialization across screen transitions, process death, and state restoration. The core library has no dependency on it."
            )

            FeatureItem(
                title = "✦ Koin Integration (Optional)",
                description = "An optional extension module (`kide-koin`) facilitating dependency injection registration and lazy resolution across modules. The core library does not depend on it."
            )

            FeatureItem(
                title = "✦ Decompose & Voyager Support (Optional)",
                description = "Provides separate host adapters (`kide-decompose` and `kide-voyager`) to retain processors inside Decompose `InstanceKeeper` component trees or Voyager `ScreenModel` scopes, making Kide easily adaptable to other popular retention ecosystems. The Decompose host supports the same `ViewState` persistence via Essenty's `StateKeeper`."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Interested in exploring the source code or contributing?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { uriHandler.openUri("https://github.com/Fuusio/kide") }
                    ) {
                        Text("View Kide on GitHub ↗")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatWithCodeSpans(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Renders `backtick`-delimited spans of [text] in a monospace code style, leaving the
 * rest as regular body text.
 */
@Composable
private fun formatWithCodeSpans(text: String): AnnotatedString {
    val codeColor = MaterialTheme.colorScheme.primary
    return remember(text, codeColor) {
        buildAnnotatedString {
            text.split('`').forEachIndexed { index, part ->
                if (index % 2 == 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = codeColor,
                        )
                    ) {
                        append(part)
                    }
                } else {
                    append(part)
                }
            }
        }
    }
}
