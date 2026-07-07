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
package org.fuusio.kide.feature

import android.content.Context
import org.koin.core.module.Module

/**
 * Interface for application features that integrate with the Koin dependency injection framework.
 *
 * Extends the base [Feature] interface to allow modular components to provide their own
 * [Module] definitions, facilitating decentralized and feature-specific dependency management.
 */
public interface KoinFeature : Feature {

    /**
     * Provides the Koin [Module] for this feature, containing its dependency injection definitions.
     *
     * @param applicationContext The Android [Context] used to resolve dependencies that require it.
     * @return The KOIN [Module] specific to this feature.
     */
    public fun koinModule(applicationContext: Context): Module
}