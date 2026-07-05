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

package org.fuusio.kide.di

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.ParametersHolder
import kotlin.reflect.KClass

/**
 * Retrieves an instance of type [T] from the Koin container.
 *
 * This is an inline extension function for [KClass] that simplifies retrieving
 * dependencies from the Koin container without explicitly creating a [KoinComponent].
 * It internally creates a temporary [KoinComponent] instance to leverage its `get()` function.
 *
 * **Usage:**
 *
 * ```kotlin
 * // Assuming you have registered a dependency of type MyDependency in your Koin module
 * val myDependency: MyDependency = MyDependency::class.get()
 *
 * // You can use it directly with a class reference:
 * val stringInstance : String = String::class.get()
 * ```
 *
 * **Important Notes:**
 *
 * - Requires your application to be configured with Koin.
 * - This function is reified, allowing you to retrieve instances based on their class type at
 *   compile time.
 * - It leverages the [KoinComponent.get] function for dependency resolution.
 * - This is a convenient shorthand, primarily useful in scenarios where direct `KoinComponent`
 *   implementation is cumbersome.
 * - Prefer using `inject()` if the dependency is to be injected into another class. This is best
 *   used in companion objects, for example.
 *
 * @param T The type of the dependency to retrieve. Must be a non-nullable type.
 * @return An instance of type [T] from the Koin container.
 * @see KoinComponent
 * @see KoinComponent.get
 */
public inline fun <reified T : Any> get(): T = object : KoinComponent {}.get<T>()

/**
 * Retrieves a dependency of type [T] from the Koin container.
 *
 * This function provides a concise way to retrieve a dependency registered within the Koin dependency
 * injection framework without needing to explicitly implement the `KoinComponent` interface. It leverages
 * an anonymous object that implements `KoinComponent` internally to access the `get()` function.
 *
 * @param params An optional lambda expression that provides parameters for the dependency.
 *                   If the dependency's creation requires parameters, this lambda should return a
 *                   `ParametersHolder` containing the necessary arguments. If no parameters are
 *                   required, this parameter can be omitted or set to `null`.
 * @return An instance of the requested dependency [T].
 *
 * @sample
 * ```
 * // Assuming a dependency of type MyService is registered in the Koin container
 * val myService: MyService = get()
 *
 * // Injecting a dependency with parameters
 * val myServiceWithParam: MyService = get { parametersOf("someParameter") }
 *
 * //Injecting without param:
 * val myServiceWithoutParam: MyService = get()
 * ```
 */
public inline fun <reified T : Any> get(noinline params: (() -> ParametersHolder)? = null): T =
    object : KoinComponent {}.get<T>(parameters = params)

public inline fun <reified T : Any> KClass<T>.get(): T = object : KoinComponent {}.get<T>()

public inline fun <reified T : Any> get(vararg params: Any): T {
    val paramsList: MutableList<Any?> = params.toMutableList()
    val paramsHolder: (() -> ParametersHolder)? =
        if (paramsList.isEmpty()) null else { { ParametersHolder(paramsList) } }
    return object : KoinComponent {}.get<T>(parameters = paramsHolder )
}

/**
 * `Injector` is a singleton object that provides a convenient way to inject dependencies
 * using Koin's dependency injection framework. It acts as a simplified wrapper around
 * Koin's `get()` function, offering various ways to retrieve dependencies.
 *
 * It simplifies the syntax for dependency injection by providing three different access methods:
 * - `inject()`: For direct injection.
 * - `get(KClass)`: Using the class type as the key.
 * - `invoke()`: Using function invocation for a more concise syntax.
 *
 * All these methods ultimately delegate to Koin's `get<T>()` function.
 *
 * **Usage Examples:**
 *
 * ```kotlin
 * // Assume you have defined a dependency in your Koin modules like:
 * // single<MyService> { MyServiceImpl() }
 *
 * // 1. Using inject():
 * val myService: MyService = Injector.inject()
 *
 * // 2. Using get(KClass):
 * val myService: MyService = Injector[MyService::class]
 *
 * // 3. Using invoke():
 * val myService: MyService = Injector()
 * ```
 *
 * All these examples will inject an instance of `MyService` into `myService`.
 */
public object Injector : KoinComponent {

    public inline fun <reified T : Any> inject(): T = get<T>()

    public inline operator fun <reified T : Any> get(type: KClass<T>): T = get<T>()

    public inline operator fun <reified T : Any> invoke(): T = get<T>()
}

/**
 * Build a [ParametersHolder]
 *
 * Use indexed value or fallback to first value of given type if needed
 *
 * @see params
 * return [ParametersHolder]
 */
public fun params(vararg params: Any?): ParametersHolder = ParametersHolder(params.toMutableList())