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

package org.fuusio.kide.domain.usecase

import org.fuusio.kide.KideComponent
import org.fuusio.kide.domain.DomainComponent

/**
 * A base marker interface for all Use Case components in the Kide Clean Architecture
 * framework.
 *
 * It can be also used to mark use cases defined as function interface.
 * For instance:
 * ```
 *  fun interface GetUserUseCase : UseCaseComponent {
 *     suspend fun getUsers(): List<User>
 *  }
 * ```
 * @see KideComponent
 */
public interface UseCaseComponent : DomainComponent
