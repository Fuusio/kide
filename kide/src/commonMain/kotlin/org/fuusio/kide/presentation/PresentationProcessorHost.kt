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
package org.fuusio.kide.presentation

/**
 * Interface for a [PresentationComponent] that hosts and manages a [PresentationProcessor].
 *
 * A host owns its processor's lifetime: implementations must invoke
 * [PresentationProcessor.close] exactly once when the host itself is destroyed (e.g. on
 * ViewModel clear, Decompose `InstanceKeeper.Instance.onDestroy`, or Voyager
 * `ScreenModel.onDispose`). See `ViewModelHost` (module `kide-navigation`) for the ViewModel-based implementation used
 * by Kide's navigation.
 */
public interface PresentationProcessorHost<P : PresentationProcessor<*, *, *>> : PresentationComponent {
    /**
     * The [PresentationProcessor] instance that is managed by this host to handle presentation
     * logic and state.
     */
    public val processor: P
}