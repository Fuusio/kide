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

package org.fuusio.kide.appstate

import androidx.compose.runtime.mutableStateOf
import io.kotest.core.spec.style.DescribeSpec
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Not a test: a probe of what happens when a [transformFlow] block breaks its contract and
 * writes snapshot state. `snapshotFlow` runs its block in a read-only snapshot and fails
 * such a write with an `IllegalStateException`; [transformFlow] runs a composition instead,
 * and this records what that does. Nothing here asserts; each case prints a `[transform]`
 * line. Whatever it prints, the rule stands: the block must not write state.
 *
 * Observed with AppState 1.0.0-alpha02 (2026-11-16): no failure in either case. A write to
 * an unrelated state took effect on every run of the block; a write to a state the block
 * also reads took effect and made the block recompose itself, six runs to count to five.
 */
@OptIn(ExperimentalKideAppStateApi::class)
class TransformFlowWriteProbe : DescribeSpec({

    /** Describes how collecting the first value matching [predicate] ended. */
    suspend fun <T> firstOutcome(flow: kotlinx.coroutines.flow.Flow<T>, predicate: (T) -> Boolean): String {
        val result = withTimeoutOrNull(3.seconds) { runCatching { flow.first(predicate) } }
        val failure = result?.exceptionOrNull()
        return when {
            result == null || failure is TimeoutCancellationException -> "nothing within 3s"
            failure != null -> "failed with $failure"
            else -> "emitted ${result.getOrNull()}"
        }
    }

    describe("a block that writes snapshot state") {

        it("writing a state the block does not read") {
            val source = mutableStateOf(1)
            val other = mutableStateOf(0)
            val runs = AtomicInteger()
            val flow = transformFlow {
                runs.incrementAndGet()
                other.value = source.value * 10
                source.value
            }

            println("[transform] write to an unrelated state, first collection: ${firstOutcome(flow) { true }}; other = ${other.value}; block ran ${runs.get()} time(s)")

            source.value = 2
            println("[transform] write to an unrelated state, after the input changed: ${firstOutcome(flow) { it == 2 }}; other = ${other.value}")
        }

        it("writing a state the block also reads") {
            val counter = mutableStateOf(0)
            val runs = AtomicInteger()
            val flow = transformFlow {
                runs.incrementAndGet()
                // Bounded, so a write that does trigger recomposition cannot loop forever.
                if (counter.value < 5) counter.value = counter.value + 1
                counter.value
            }

            println("[transform] write to a state the block reads: ${firstOutcome(flow) { it >= 5 }}; counter = ${counter.value}; block ran ${runs.get()} time(s)")
        }
    }
})
