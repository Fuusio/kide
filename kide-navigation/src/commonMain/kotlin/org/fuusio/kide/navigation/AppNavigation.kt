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

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import kotlin.reflect.KClass
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.fuusio.kide.presentation.PresentationProcessor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.fuusio.kide.log.KideLog

/** Log tag shared by the top-level navigation functions in this file. */
private const val NAV_LOG_TAG = "AppNavigation"

/**
 * Creates and remembers a [NavBackStack] seeded with [initialKeys].
 * Use this when you need to hoist the back stack — e.g. to share it with a navigation drawer.
 */
@Composable
public fun rememberAppNavBackStack(vararg initialKeys: ScreenNavKey<*>): NavBackStack<NavKey> =
    rememberScreenNavBackStack(*initialKeys)

/**
 * Returns the [ScreenNavKey] at the top of [this] back stack, or `null` if the stack is empty.
 */
public fun NavBackStack<NavKey>.currentNavKey(): ScreenNavKey<*>? =
    (lastOrNull() as? NavKeyWrapper)?.screenNavKey

/**
 * Clears the back stack and pushes [navKey] as the single entry.
 */
public fun NavBackStack<NavKey>.navigateTo(navKey: ScreenNavKey<*>) {
    KideLog.d(NAV_LOG_TAG) { "navigateTo: $navKey" }
    clear()
    add(NavKeyWrapper(navKey))
}

/**
 * Pushes [navKey] on top of the existing back stack without clearing it.
 */
public fun NavBackStack<NavKey>.pushTo(navKey: ScreenNavKey<*>) {
    KideLog.d(NAV_LOG_TAG) { "pushTo: $navKey" }
    add(NavKeyWrapper(navKey))
}

/**
 * Renders a [NavDisplay] for the given hoisted [backStack].
 * Pair with [rememberAppNavBackStack] when the back stack is owned outside — e.g. in a drawer host.
 */
@Composable
public fun AppNavigation(
    backStack: NavBackStack<NavKey>,
    callbacks: Map<String, () -> Unit> = emptyMap(),
) {
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = { key ->
            key as NavKeyWrapper
            NavEntry(key) { RenderScreen(key.screenNavKey, backStack, callbacks) }
        }
    )
}

@Composable
@Suppress("UNCHECKED_CAST")
private fun <T : PresentationProcessor<*, *, *>> RenderScreen(
    navKey: ScreenNavKey<T>,
    backStack: NavBackStack<NavKey>,
    callbacks: Map<String, () -> Unit> = emptyMap(),
) {
    KideLog.d(NAV_LOG_TAG) { "Rendering screen for navKey: $navKey" }
    val owner = LocalViewModelStoreOwner.current
    if (owner == null) {
        KideLog.w(NAV_LOG_TAG) { "LocalViewModelStoreOwner is null for RenderScreen" }
        return
    }
    val factory = object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: KClass<V>, extras: CreationExtras): V {
            val processor = navKey.createProcessor()
            val serializer = navKey.stateSerializer
            val savedStateHandle = if (serializer != null) {
                try {
                    extras.createSavedStateHandle()
                } catch (exception: Exception) {
                    KideLog.w(NAV_LOG_TAG, exception) {
                        "SavedStateHandle unavailable for '${navKey.serialKey}'; ViewState persistence disabled"
                    }
                    null
                }
            } else {
                null
            }
            val host = ViewModelHost(processor, savedStateHandle, serializer)
            if (!processor.wasRestored) {
                navKey.setup(processor)
            }
            return host as V
        }
    }
    val host = ViewModelProvider.create(owner, factory)[navKey.serialKey, ViewModelHost::class]
    val processor = host.processor as T
    val ctx = ScreenContext(
        processor = processor,
        backStack = backStack,
        onBack = { navKey.onBack(backStack) },
        callbacks = callbacks,
    )
    navKey.screen(ctx)
}

/**
 * Serializes a [NavKeyWrapper] as its [ScreenNavKey.serialKey] plus optional arguments.
 *
 * [fallbackKey] decides what happens when a persisted `serialKey` cannot be resolved against
 * [ScreenNavKeyRegistry] — which is what an application upgrade looks like from here: a user
 * backgrounds version 1 with a destination on the stack, version 2 removes or renames it, the
 * process is killed, and the user comes back.
 *
 * - Non-null: the entry is replaced by the fallback and a warning is logged. The application
 *   opens on a valid screen instead of crashing. Note that this can leave the same destination
 *   twice in a row on the restored stack.
 * - Null: restoration throws, which is the right behaviour when a caller wants an unresolvable
 *   key treated as a programming error rather than an upgrade.
 *
 * [NavKeyWrapperSerializer] is the strict, no-argument form used by the `@Serializable`
 * annotation; [rememberScreenNavBackStack] installs an instance carrying the application's first
 * initial key as the fallback.
 */
internal open class NavKeyWrapperSerializerImpl(
    private val fallbackKey: ScreenNavKey<*>?,
) : KSerializer<NavKeyWrapper> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NavKeyWrapper") {
        element<String>("serialKey")
        element<String>("args", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: NavKeyWrapper) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.screenNavKey.serialKey)
            value.screenNavKey.saveArgs()?.let { args ->
                encodeStringElement(descriptor, 1, args)
            }
        }
    }

    override fun deserialize(decoder: Decoder): NavKeyWrapper =
        decoder.decodeStructure(descriptor) {
            var serialKey: String? = null
            var args: String? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> serialKey = decodeStringElement(descriptor, 0)
                    1 -> args = decodeStringElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            val key = checkNotNull(serialKey) { "Missing serialKey in saved navigation state" }
            val navKey = ScreenNavKeyRegistry.find(key) ?: resolveUnregistered(key)
            NavKeyWrapper(restoreArguments(navKey, args))
        }

    private fun resolveUnregistered(serialKey: String): ScreenNavKey<*> {
        val fallback = fallbackKey
            ?: error("NavKey for $serialKey was not registered. Ensure the module is initialized.")
        KideLog.w(NAV_LOG_TAG) {
            "Saved navigation state references unregistered destination '$serialKey'; " +
                "falling back to '${fallback.serialKey}'. This is expected when a release " +
                "removes or renames a destination that an older saved back stack still refers " +
                "to; it is a bug if the destination still exists and its feature simply has " +
                "not been initialized yet."
        }
        return fallback
    }

    private fun restoreArguments(navKey: ScreenNavKey<*>, args: String?): ScreenNavKey<*> {
        if (args == null) return navKey
        val restored = navKey.restoreArgs(args)
        if (restored === navKey) {
            // saveArgs() produced these arguments, so restoreArgs() was expected to consume
            // them. The default implementation returns the receiver unchanged, which silently
            // reopens the destination with default arguments — a failure that only appears
            // after process death, on a screen that looks otherwise fine.
            KideLog.w(NAV_LOG_TAG) {
                "Destination '${navKey.serialKey}' saved navigation arguments but its " +
                    "restoreArgs() returned the key unchanged, so the arguments were dropped. " +
                    "Override restoreArgs() to return a new key carrying them."
            }
        }
        return restored
    }
}

/**
 * The strict [NavKeyWrapperSerializerImpl]: an unresolvable `serialKey` throws.
 *
 * Used by the `@Serializable` annotation on [NavKeyWrapper]. Back-stack restoration goes
 * through [rememberScreenNavBackStack], which installs a fallback-carrying instance instead.
 */
internal object NavKeyWrapperSerializer : NavKeyWrapperSerializerImpl(fallbackKey = null)

@Serializable(with = NavKeyWrapperSerializer::class)
internal data class NavKeyWrapper(val screenNavKey: ScreenNavKey<*>) : NavKey

@Composable
private fun rememberScreenNavBackStack(
    vararg initialKeys: ScreenNavKey<*>
): NavBackStack<NavKey> {
    val wrappedKeys = initialKeys.map { NavKeyWrapper(it) }.toTypedArray()

    val configuration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                // Restoration must not be able to crash the application at startup: a back stack
                // saved by an earlier release can name destinations this build no longer has,
                // and the saved state survives restarts, so throwing here would keep throwing.
                // Unresolvable entries resolve to the first initial key instead.
                subclass(
                    NavKeyWrapper::class,
                    NavKeyWrapperSerializerImpl(fallbackKey = initialKeys.firstOrNull()),
                )
            }
        }
    }

    return rememberNavBackStack(
        configuration = configuration,
        elements = wrappedKeys
    )
}