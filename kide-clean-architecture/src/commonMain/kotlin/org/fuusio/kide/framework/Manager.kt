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

package org.fuusio.kide.framework

/**
 * A marker interface for manager components in the application's Framework layer.
 *
 * This interface is part of the application's clean architecture pattern, representing the lowest
 * level of framework management components. Managers are responsible for handling the technical
 * details of interacting with various resources or settings in the platform, such as:
 * - System settings
 * - Device drivers
 * - Device sensors
 *
 * Managers are typically used by service components ([org.fuusio.kide.adapter.Service]
 * implementations), which coordinate and abstract the platform management operations.
 */
public interface Manager : FrameworkComponent