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
package org.fuusio.kide.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.fuusio.kide.app.R
import org.fuusio.kide.app.feature.about.navigation.AboutNavKey
import org.fuusio.kide.app.feature.home.navigation.HomeNavKey
import org.fuusio.kide.app.feature.search.navigation.SearchNavKey
import org.fuusio.kide.app.feature.browser.navigation.BrowserNavKey
import org.fuusio.kide.app.feature.settings.navigation.SettingsNavKey
import org.fuusio.kide.app.ui.theme.FuusioTheme
import org.fuusio.kide.navigation.AppNavigation
import org.fuusio.kide.navigation.ScreenContext
import org.fuusio.kide.navigation.ScreenNavKey
import org.fuusio.kide.navigation.currentNavKey
import org.fuusio.kide.navigation.navigateTo
import org.fuusio.kide.navigation.rememberAppNavBackStack
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.fuusio.kide.di.get
import org.fuusio.kide.app.domain.usecase.GetSettingsUseCase
import org.fuusio.kide.app.domain.entity.DarkMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val getSettingsUseCase: GetSettingsUseCase = get()
        setContent {
            val settingsState by getSettingsUseCase.execute().collectAsState(initial = null)
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (settingsState?.darkMode) {
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
                else -> isSystemDark
            }
            FuusioTheme(darkTheme = useDarkTheme) {
                val showSplashScreen = remember { mutableStateOf(true) }

                if (showSplashScreen.value) {
                    SplashScreen(onTimeout = { showSplashScreen.value = false })
                } else {
                    FuusioApp()
                }
            }
        }
    }
}

private data class DrawerItem(
    val navKey: ScreenNavKey<*>,
    val label: String,
    val iconRes: Int,
)

@Composable
private fun drawerItems(): List<DrawerItem> {
    val resources = LocalResources.current
    return remember {
        listOf(
            DrawerItem(
                HomeNavKey,
                resources.getString(R.string.label_drawer_item_home),
                R.drawable.home_24px,
            ),

            DrawerItem(
                SearchNavKey,
                resources.getString(R.string.label_drawer_item_search),
                R.drawable.search_24px,
            ),

            DrawerItem(
                BrowserNavKey,
                resources.getString(R.string.label_drawer_item_browser),
                R.drawable.database_24px,
            ),

            DrawerItem(
                SettingsNavKey,
                resources.getString(R.string.label_drawer_item_settings),
                R.drawable.settings_24px,
            ),

            DrawerItem(
                AboutNavKey,
                resources.getString(R.string.label_drawer_item_about),
                R.drawable.info_24px,
            ),
        )
    }
}

@Composable
private fun FuusioApp() {
    val backStack = rememberAppNavBackStack(HomeNavKey)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentKey = backStack.currentNavKey()
    val onMenu: () -> Unit = {
        scope.launch {
            if (drawerState.isClosed) {
                drawerState.open()
            } else {
                drawerState.close()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        modifier = Modifier.size(160.dp),
                        painter = painterResource(id = R.drawable.logo_kide_278x278),
                        contentDescription = "FUUSIO Logo"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                drawerItems().forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(painter = painterResource(item.iconRes), contentDescription = null) },
                        selected = currentKey == item.navKey,
                        onClick = {
                            scope.launch { drawerState.close() }
                            backStack.navigateTo(item.navKey)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        AppNavigation(
            backStack = backStack,
            callbacks = mapOf(ScreenContext.MENU to onMenu),
        )
    }
}
