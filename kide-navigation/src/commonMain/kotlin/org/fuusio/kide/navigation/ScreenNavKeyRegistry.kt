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
package org.fuusio.kide.navigation

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A global registry providing decentralized lookup for [ScreenNavKey] instances.
 *
 * This registry allows modules to register their navigation keys independently, enabling
 * the retrieval of keys via their unique [ScreenNavKey.serialKey] during navigation or
 * state restoration processes.
 *
 * The registry is thread-safe. All keys must be registered before the navigation back stack
 * can be restored — register them during application startup (for example, from
 * `Feature.initialize` (module `kide-clean-architecture`) invoked in `Application.onCreate`), before
 * any composition begins.
 */
@OptIn(ExperimentalAtomicApi::class)
public object ScreenNavKeyRegistry {
    private val registry = AtomicReference<Map<String, ScreenNavKey<*>>>(emptyMap())

    /**
     * Registers a [ScreenNavKey] in the registry.
     *
     * The key is stored using its [ScreenNavKey.serialKey], enabling global lookup
     * via [get] during navigation or state restoration.
     *
     * @param key The navigation key to be registered.
     */
    public fun register(key: ScreenNavKey<*>) {
        while (true) {
            val current = registry.load()
            val updated = current + (key.serialKey to key)
            if (registry.compareAndSet(current, updated)) return
        }
    }

    /**
     * Retrieves a registered [ScreenNavKey] associated with the given [serialKey].
     *
     * @param serialKey The unique string identifier of the navigation key to retrieve.
     * @return The registered [ScreenNavKey] instance.
     * @throws IllegalStateException If no navigation key has been registered for the provided [serialKey].
     */
    public fun get(serialKey: String): ScreenNavKey<*> =
        registry.load()[serialKey] ?:
            error("NavKey for $serialKey was not registered. Ensure the module is initialized.")

}