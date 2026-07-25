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
     * Registering a key equal to the one already stored under the same
     * [ScreenNavKey.serialKey] is a no-op, so a feature whose `initialize()` runs twice is
     * harmless. Registering a *different* destination under an already-used `serialKey` fails
     * fast: silently overwriting would mean the saved back stack resolves to whichever feature
     * happened to initialise last, sending the user to the wrong screen after process death —
     * or throwing `ClassCastException` from inside a composable if the two destinations use
     * different processor types.
     *
     * @param key The navigation key to be registered.
     * @throws IllegalStateException if a different key is already registered under the same
     * [ScreenNavKey.serialKey].
     */
    public fun register(key: ScreenNavKey<*>) {
        while (true) {
            val current = registry.load()
            val existing = current[key.serialKey]
            if (existing != null) {
                if (existing == key) return
                error(
                    "Duplicate serialKey '${key.serialKey}': it is already registered by " +
                        "${existing::class.simpleName}, so ${key::class.simpleName} cannot use " +
                        "it. serialKey values must be unique across the whole application — " +
                        "they are the identity of a destination in saved navigation state.",
                )
            }
            val updated = current + (key.serialKey to key)
            if (registry.compareAndSet(current, updated)) return
        }
    }

    /**
     * Returns the [ScreenNavKey] registered for [serialKey], or `null` if there is none.
     *
     * Unlike [get], this does not throw — use it when an unresolvable key is a condition to be
     * handled rather than a programming error, as it is when restoring a back stack saved by an
     * earlier release of the application.
     */
    public fun find(serialKey: String): ScreenNavKey<*>? = registry.load()[serialKey]

    /**
     * Removes all registered keys.
     *
     * Intended for tests: the registry is global process state, so without a reset between
     * cases the keys registered by one test leak into every later one and specs become
     * order-dependent. Application code should never call this.
     */
    public fun clear() {
        registry.store(emptyMap())
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