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

import androidx.appstate.transform.testing.transformTest
import androidx.compose.runtime.mutableStateOf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * [transformFlow] under `PresentationProcessor.observe`: first with the composition on real
 * `Dispatchers.Default`, as an application runs it, then driven deterministically by
 * AppState's `transformTest` — the recipe for testing a processor that uses it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransformFlowObserveTest : DescribeSpec({

    beforeSpec {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    describe("observe(key, transformFlow { })") {

        it("reduces transform results into the state, each applied transition reported once") {
            val source = mutableStateOf(0)
            val recorder = RecordingInterceptor()
            val processor = TestProcessor(source, interceptors = listOf(recorder))

            processor.dispatch(TestIntent.Watch(multiplier = 2))
            source.value = 3
            within { processor.state.value == 6 } shouldBe true
            source.value = 4
            within { processor.state.value == 8 } shouldBe true

            val transitions = recorder.transitions.toList()
            // Trace fidelity: nothing reported that changed nothing, no gaps, and the
            // trace ends on the state the processor actually holds.
            transitions.all { (old, new) -> old != new } shouldBe true
            transitions.first().first shouldBe TestState()
            transitions.zipWithNext().all { (a, b) -> a.second == b.first } shouldBe true
            transitions.last().second shouldBe processor.state
            processor.close()
        }

        it("reusing the key disposes the previous composition") {
            val source = mutableStateOf(1)
            val processor = TestProcessor(source)

            processor.dispatch(TestIntent.Watch(multiplier = 2))
            within { processor.state.value == 2 } shouldBe true

            processor.dispatch(TestIntent.Watch(multiplier = 10))
            within { processor.disposals.get() == 1 } shouldBe true
            within { processor.state.value == 10 } shouldBe true
            processor.close()
        }

        it("stopObserving and close dispose the composition") {
            val source = mutableStateOf(1)
            val stopped = TestProcessor(source)
            stopped.dispatch(TestIntent.Watch(multiplier = 2))
            within { stopped.state.value == 2 } shouldBe true
            stopped.dispatch(TestIntent.Unwatch)
            within { stopped.disposals.get() == 1 } shouldBe true
            stopped.close()

            val closed = TestProcessor(source)
            closed.dispatch(TestIntent.Watch(multiplier = 2))
            within { closed.state.value == 2 } shouldBe true
            closed.close()
            within { closed.disposals.get() == 1 } shouldBe true
        }

        it("a failing block reaches onObservationError and leaves the intent loop alive") {
            val source = mutableStateOf(1)
            val processor = TestProcessor(source)

            processor.dispatch(TestIntent.WatchFailing)
            within { processor.state.value == 1 } shouldBe true
            source.value = 100
            within { processor.observationErrors.isNotEmpty() } shouldBe true
            processor.observationErrors.first().first shouldBe "failing"
            (processor.observationErrors.first().second is TestFailure) shouldBe true

            processor.dispatch(TestIntent.Watch(multiplier = 3))
            within { processor.state.value == 300 } shouldBe true
            processor.close()
        }
    }

    describe("with transformTest") {

        it("drives a processor observation with advance()") {
            transformTest {
                val source = mutableStateOf(1)
                val processor = TestProcessor(
                    source = source,
                    dispatcher = testDispatcher,
                    scope = CoroutineScope(SupervisorJob() + testDispatcher),
                )

                processor.dispatch(TestIntent.Watch(multiplier = 2))
                advance()
                advance()
                processor.state.value shouldBe 2

                source.value = 4
                advance()
                advance()
                processor.state.value shouldBe 8

                processor.close()
            }
        }
    }
})
