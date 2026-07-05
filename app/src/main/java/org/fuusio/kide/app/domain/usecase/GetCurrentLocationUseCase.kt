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
package org.fuusio.kide.app.domain.usecase

import org.fuusio.kide.domain.usecase.UseCaseFunction

/**
 * Use case to retrieve the current device location as latitude and longitude coordinates.
 * Returns a [Result] containing a [Pair] where the first element is latitude and the second is longitude.
 */
fun interface GetCurrentLocationUseCase : suspend () -> Result<Pair<Double, Double>>, UseCaseFunction
