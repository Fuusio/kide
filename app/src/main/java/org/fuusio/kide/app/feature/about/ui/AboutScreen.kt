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
package org.fuusio.kide.app.feature.about.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fuusio.kide.app.feature.about.presentation.OSILicense
import org.fuusio.kide.app.feature.about.presentation.AboutScreenLaunched
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fuusio.kide.navigation.ScreenContext
import org.fuusio.kide.app.feature.about.presentation.AboutProcessor
import androidx.core.net.toUri
import org.fuusio.kide.app.ui.compose.VerticalSpacer
import org.fuusio.kide.app.R
import org.fuusio.kide.app.feature.about.presentation.AboutIntent
import org.fuusio.kide.app.feature.about.presentation.AboutViewState

@Composable
fun OsiLicenseEntry(
    libraryName: String,
    copyright: String,
    license: OSILicense,
    customLicenseText: String? = null,
    githubUrl: String? = null,
) {
    val isExpanded = remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded.value) 180f else 0f
    )
    val context = LocalContext.current

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = libraryName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                VerticalSpacer(8.dp)
                Text(
                    text = copyright,
                    style = MaterialTheme.typography.bodySmall,
                )
                VerticalSpacer(4.dp)
                Text(
                    text = license.displayName,
                    style = MaterialTheme.typography.labelMedium,
                )
                VerticalSpacer(4.dp)
                Text(
                    text = license.url,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, license.url.toUri())
                        context.startActivity(intent)
                    }
                )
                if (githubUrl != null) {
                    VerticalSpacer(8.dp)
                    Text(
                        text = githubUrl,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                            context.startActivity(intent)
                        }
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.keyboard_arrow_down_24px),
                contentDescription = if (isExpanded.value) "Collapse" else "Expand",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotationState)
                    .clickable { isExpanded.value = !isExpanded.value }
            )
        }

        if (isExpanded.value) {
            Text(
                text = customLicenseText ?: license.licenceText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 0.dp, top = 4.dp, end = 0.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
fun AboutScreen(ctx: ScreenContext<AboutProcessor>) {
    val onDispatch = ctx.processor::dispatch
    AboutScreenContent(
        ctx.processor.states.collectAsStateWithLifecycle().value,
        onMenu = ctx.callback(ScreenContext.MENU),
        onDispatch = onDispatch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenContent(
    state: AboutViewState,
    onMenu: () -> Unit,
    onDispatch: (AboutIntent) -> Unit,
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("About") },
                navigationIcon = {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(R.drawable.menu_24px),
                            contentDescription = null,
                        )
                    }
                }
            )
        },
    ) { innerPadding ->

        LaunchedEffect(Unit) {
            onDispatch(AboutScreenLaunched)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier.size(160.dp),
                painter = painterResource(id = R.drawable.logo_kide_278x278),
                contentDescription = "Kide Logo"
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Kide Demo App v${state.version}",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Copyright © Marko Salmela 2025-2026",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "OSI Licenses:",
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    OsiLicenseEntry(
                        libraryName = "Koin",
                        copyright = "Copyright © 2017 Arnaud Giuliani, Laurent Baresse",
                        license = OSILicense.APACHE_2,
                    )
                    HorizontalDivider()
                }
                item {
                    OsiLicenseEntry(
                        libraryName = "kotest",
                        copyright = "Copyright © 2016 sksamuel",
                        license = OSILicense.APACHE_2,
                    )
                }
                item {
                    OsiLicenseEntry(
                        libraryName = "Timber",
                        copyright = "Copyright © 2013 Jake Wharton",
                        license = OSILicense.APACHE_2,
                    )
                }
                item {
                    OsiLicenseEntry(
                        libraryName = "Turbine",
                        copyright = "Copyright © 2020 Jake Wharton",
                        license = OSILicense.APACHE_2,
                    )
                }
            }
        }
    }
}
