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

package org.fuusio.kide.decompose

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.InstanceKeeperOwner
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.statekeeper.StateKeeper
import kotlinx.serialization.KSerializer
import org.fuusio.kide.log.logW
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.PresentationProcessorHost
import org.fuusio.kide.presentation.ViewState

/**
 * A Decompose/Essenty [InstanceKeeper]-based implementation of [PresentationProcessorHost].
 *
 * [PresentationProcessor] is framework-independent; this adapter gives it Decompose retention
 * semantics: the processor is retained by an [InstanceKeeper] (surviving Android configuration
 * changes) and is [closed][PresentationProcessor.close] exactly once when the keeper's scope is
 * destroyed.
 *
 * Prefer the [retainedProcessor] extension functions over instantiating this class directly.
 *
 * @param P The type of the hosted [PresentationProcessor].
 * @property processor The hosted processor.
 */
public class InstanceKeeperHost<P : PresentationProcessor<*, *, *>>(
    override val processor: P,
) : PresentationProcessorHost<P>, InstanceKeeper.Instance {

    override fun onDestroy() {
        processor.close()
    }
}

/**
 * Returns the [PresentationProcessor] retained in this [InstanceKeeper] under [key], creating
 * it with [factory] on first access.
 *
 * The processor is closed automatically when the [InstanceKeeper] is destroyed.
 */
public fun <P : PresentationProcessor<*, *, *>> InstanceKeeper.retainedProcessor(
    key: Any,
    factory: () -> P,
): P = getOrCreate(key) { InstanceKeeperHost(factory()) }.processor

/**
 * Returns the [PresentationProcessor] retained in this [InstanceKeeper] under [key], creating
 * it with [factory] on first access, with `ViewState` persistence across process death.
 *
 * On first access after process death, a state previously saved via [stateKeeper] is decoded
 * with [stateSerializer] and applied through [PresentationProcessor.restoreState] before the
 * processor is returned. A **lazy** supplier serializing
 * [PresentationProcessor.stateToSave] is (re-)registered on the [stateKeeper], so the state
 * is encoded only at the moment Essenty snapshots state. A failed restore is logged and the
 * processor starts from its initial state.
 *
 * When the processor was retained (configuration change), the saved state is ignored — the
 * in-memory state is newer — but the supplier is still re-registered on the new [stateKeeper].
 */
@Suppress("UNCHECKED_CAST")
public fun <P : PresentationProcessor<*, *, *>> InstanceKeeper.retainedProcessor(
    key: Any,
    stateKeeper: StateKeeper,
    stateSerializer: KSerializer<out ViewState>,
    factory: () -> P,
): P {
    var created = false
    val processor = getOrCreate(key) {
        created = true
        InstanceKeeperHost(factory())
    }.processor

    val serializer = stateSerializer as KSerializer<ViewState>
    val restorable = processor as PresentationProcessor<*, ViewState, *>
    val stateKey = "org.fuusio.kide.view_state.$key"

    if (created) {
        try {
            stateKeeper.consume(stateKey, serializer)?.let(restorable::restoreState)
        } catch (exception: Exception) {
            processor.logW(exception) { "Failed to restore ViewState; starting from initial state" }
        }
    }
    if (!stateKeeper.isRegistered(stateKey)) {
        stateKeeper.register(stateKey, serializer) { restorable.stateToSave() }
    }
    return processor
}

/**
 * Returns the [PresentationProcessor] retained by this [InstanceKeeperOwner] (for example, a
 * Decompose `ComponentContext`) under [key], creating it with [factory] on first access.
 *
 * ### Example
 * ```kotlin
 * class HomeComponent(ctx: ComponentContext) : ComponentContext by ctx {
 *     val processor: HomeProcessor =
 *         retainedProcessor("home") { HomeProcessor(HomeViewState()) }
 * }
 * ```
 */
public fun <P : PresentationProcessor<*, *, *>> InstanceKeeperOwner.retainedProcessor(
    key: Any,
    factory: () -> P,
): P = instanceKeeper.retainedProcessor(key, factory)

/**
 * Returns the [PresentationProcessor] retained by this [InstanceKeeperOwner] under [key] with
 * `ViewState` persistence across process death.
 *
 * ### Example
 * ```kotlin
 * class HomeComponent(ctx: ComponentContext) : ComponentContext by ctx {
 *     val processor: HomeProcessor = retainedProcessor(
 *         key = "home",
 *         stateKeeper = stateKeeper,
 *         stateSerializer = HomeViewState.serializer(),
 *     ) { HomeProcessor(HomeViewState()) }
 * }
 * ```
 */
public fun <P : PresentationProcessor<*, *, *>> InstanceKeeperOwner.retainedProcessor(
    key: Any,
    stateKeeper: StateKeeper,
    stateSerializer: KSerializer<out ViewState>,
    factory: () -> P,
): P = instanceKeeper.retainedProcessor(key, stateKeeper, stateSerializer, factory)
