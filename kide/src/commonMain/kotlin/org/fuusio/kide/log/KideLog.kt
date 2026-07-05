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

package org.fuusio.kide.log

/**
 * Severity levels for log entries, ordered from least to most severe.
 *
 * [None] is not a loggable level; it is used as a [KideLog.minLevel] value
 * to disable logging entirely.
 */
public enum class LogLevel {
    Verbose,
    Debug,
    Info,
    Warning,
    Error,
    None,
}

/**
 * Interface defining the logging operations for the Kide framework.
 */
public fun interface KideLogger {
    public fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

/**
 * [KideLog] provides a global logging facade for the Kide framework.
 *
 * It forwards logging calls to an underlying [KideLogger] implementation.
 * To enable logging, a concrete implementation of [KideLogger] must be assigned to the [logger]
 * property. If no logger is assigned, all logging calls are silently ignored.
 *
 * Entries below [minLevel] are filtered out before reaching the logger. Message lambdas of the
 * lazy logging functions ([v], [d], [i], [w], [e], and the `Any.log*` extensions) are only
 * evaluated when the entry is actually logged.
 */
public object KideLog {

    public var logger: KideLogger? = null

    /** The minimum [LogLevel] that is logged. Set to [LogLevel.None] to disable logging. */
    public var minLevel: LogLevel = LogLevel.Verbose

    public fun isLoggable(level: LogLevel): Boolean =
        level != LogLevel.None && level >= minLevel && logger != null

    public fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (isLoggable(level)) {
            logger?.log(level, tag, message, throwable)
        }
    }

    public inline fun log(level: LogLevel, tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isLoggable(level)) {
            log(level, tag, message(), throwable)
        }
    }

    public inline fun v(tag: String, message: () -> String): Unit =
        log(LogLevel.Verbose, tag, null, message)

    public inline fun d(tag: String, message: () -> String): Unit =
        log(LogLevel.Debug, tag, null, message)

    public inline fun i(tag: String, message: () -> String): Unit =
        log(LogLevel.Info, tag, null, message)

    public inline fun w(tag: String, throwable: Throwable? = null, message: () -> String): Unit =
        log(LogLevel.Warning, tag, throwable, message)

    public inline fun e(tag: String, throwable: Throwable? = null, message: () -> String): Unit =
        log(LogLevel.Error, tag, throwable, message)
}

/**
 * The log tag automatically derived for this instance from its class name.
 */
public val Any.logTag: String
    get() = this::class.simpleName ?: "Anonymous"

/**
 * Class-tagged lazy logging extensions. The tag is derived automatically from the receiver's
 * class name, and the message lambda is evaluated only if the entry passes level filtering.
 *
 * ```kotlin
 * class FooProcessor {
 *     fun process(intent: Intent) {
 *         logD { "Processing intent: $intent" } // tag: "FooProcessor"
 *     }
 * }
 * ```
 */
public inline fun Any.logV(message: () -> String): Unit =
    KideLog.log(LogLevel.Verbose, logTag, null, message)

public inline fun Any.logD(message: () -> String): Unit =
    KideLog.log(LogLevel.Debug, logTag, null, message)

public inline fun Any.logI(message: () -> String): Unit =
    KideLog.log(LogLevel.Info, logTag, null, message)

public inline fun Any.logW(throwable: Throwable? = null, message: () -> String): Unit =
    KideLog.log(LogLevel.Warning, logTag, throwable, message)

public inline fun Any.logE(throwable: Throwable? = null, message: () -> String): Unit =
    KideLog.log(LogLevel.Error, logTag, throwable, message)
