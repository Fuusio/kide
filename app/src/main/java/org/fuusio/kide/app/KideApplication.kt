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
package org.fuusio.kide.app

import android.app.Application
import org.fuusio.kide.app.feature.about.AboutFeature
import org.fuusio.kide.app.feature.home.HomeFeature
import org.fuusio.kide.app.feature.search.SearchFeature
import org.fuusio.kide.app.feature.browser.BrowserFeature
import org.fuusio.kide.app.feature.details.DetailsFeature
import org.fuusio.kide.app.feature.settings.SettingsFeature
import org.fuusio.kide.app.koin.dispatchersModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.fuusio.kide.devtools.mcp.KideMcpServer
import org.fuusio.kide.devtools.mcp.start
import org.fuusio.kide.log.KideLog
import org.fuusio.kide.log.KideLogger
import org.fuusio.kide.log.LogLevel
import org.koin.core.module.Module
import timber.log.Timber

class KideApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
            
            KideLog.minLevel = LogLevel.Debug
            KideLog.logger = KideLogger { level, tag, message, throwable ->
                val tree = Timber.tag(tag)
                when (level) {
                    LogLevel.Verbose -> tree.v(throwable, message)
                    LogLevel.Debug -> tree.d(throwable, message)
                    LogLevel.Info -> tree.i(throwable, message)
                    LogLevel.Warning -> tree.w(throwable, message)
                    LogLevel.Error -> tree.e(throwable, message)
                    LogLevel.None -> Unit
                }
            }
            // Kide agent port: exposes the app's MVI machinery to AI coding agents via MCP.
            // The guarded variant refuses to start unless the app is debuggable.
            // Connect with: adb forward tcp:8765 tcp:8765
            //               claude mcp add --transport http kide http://localhost:8765/mcp
            KideMcpServer.start(this)
        } else {
            KideLog.minLevel = LogLevel.None
        }

        startKoin {
            androidContext(this@KideApplication.applicationContext)
            modules(getModules())
        }

        initializeFeatures()
    }


    private fun getModules(): List<Module> = listOf(
        dispatchersModule,
        HomeFeature.koinModule(applicationContext),
        AboutFeature.koinModule(applicationContext),
        SearchFeature.koinModule(applicationContext),
        BrowserFeature.koinModule(applicationContext),
        DetailsFeature.koinModule(applicationContext),
        SettingsFeature.koinModule(applicationContext),
    )

    private fun initializeFeatures() {
        HomeFeature.initialize()
        AboutFeature.initialize()
        SearchFeature.initialize()
        BrowserFeature.initialize()
        DetailsFeature.initialize()
        SettingsFeature.initialize()
    }
}

