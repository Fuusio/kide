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

package org.fuusio.kide.adapter

import android.content.Context

/**
 * An interface for components that provide access to an Android [Context].
 *
 * This interface is part of the application's Adapter layer and facilitates access to Android's
 * system services and resources. It abstracts the source of the context, allowing different
 * implementations to provide different types of contexts (e.g., application context, activity
 * context) based on the specific requirements.
 *
 * By using this interface, domain and presentation layer components can request access to a context
 * without directly depending on Android-specific classes, which improves testability and adheres
 * to clean architecture principles by maintaining proper dependency direction.
 *
 * Implementations of this interface should ensure they provide the appropriate type of context
 * for their use case, considering concerns such as lifecycle and memory leaks.
 */
interface ContextProvider {

    /**
     * The Android [Context] provided by this implementation.
     * 
     * Depending on the implementation, this could be an application, activity,
     * or another type of [Context]. Clients should not make assumptions about the specific type
     * of [Context] provided unless documented by the specific implementation.
     */
    val context: Context
}