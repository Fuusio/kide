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

package org.fuusio.kide.voyager

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.PresentationProcessorHost

/**
 * A Voyager [ScreenModel]-based implementation of [PresentationProcessorHost].
 *
 * [PresentationProcessor] is framework-independent; this adapter gives it Voyager retention
 * semantics: the processor is retained by the screen's `ScreenModelStore` (surviving Android
 * configuration changes) and is [closed][PresentationProcessor.close] exactly once when the
 * screen leaves the navigation stack.
 *
 * Prefer the [rememberProcessor] extension over instantiating this class directly.
 *
 * @param P The type of the hosted [PresentationProcessor].
 * @property processor The hosted processor.
 */
public class ScreenModelHost<P : PresentationProcessor<*, *, *>>(
    override val processor: P,
) : PresentationProcessorHost<P>, ScreenModel {

    override fun onDispose() {
        processor.close()
    }
}

/**
 * Returns the [PresentationProcessor] retained for this [Screen], creating it with [factory]
 * on first access. The processor is closed automatically when the screen is disposed.
 *
 * @param tag An optional distinguishing tag, needed when a screen retains multiple processors
 * of the same type.
 *
 * ### Example
 * ```kotlin
 * class HomeVoyagerScreen : Screen {
 *     @Composable
 *     override fun Content() {
 *         val processor = rememberProcessor { HomeProcessor(HomeViewState()) }
 *         val state by processor.states.collectAsState()
 *         // ...
 *     }
 * }
 * ```
 */
@Composable
public fun <P : PresentationProcessor<*, *, *>> Screen.rememberProcessor(
    tag: String? = null,
    factory: () -> P,
): P = rememberScreenModel(tag) { ScreenModelHost(factory()) }.processor
