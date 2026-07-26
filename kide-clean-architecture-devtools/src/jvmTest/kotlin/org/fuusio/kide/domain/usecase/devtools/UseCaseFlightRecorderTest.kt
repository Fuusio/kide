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

package org.fuusio.kide.domain.usecase.devtools

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.fuusio.kide.devtools.FlightRecorder
import org.fuusio.kide.devtools.TraceBuffer
import org.fuusio.kide.devtools.TraceEventSource
import org.fuusio.kide.devtools.TraceEventType
import org.fuusio.kide.domain.entity.State
import org.fuusio.kide.domain.usecase.AbstractUseCaseProcessor
import org.fuusio.kide.domain.usecase.UseCaseIntent
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import org.fuusio.kide.presentation.reduce
import org.fuusio.kide.presentation.useCase

/*
 * The end this whole design was aimed at: one buffer holding both layers, so that asking
 * "why didn't the list update?" is answerable from a single trace.
 *
 * The dispatcher is left unconfined here — unlike UseCaseTracingTest, which deliberately shifts
 * to Dispatchers.Default to prove the correlation survives it. This spec is about what lands in
 * the buffer and in what order, so determinism is worth more than realism.
 */

// ── Domain ─────────────────────────────────────────────────────────────────────

private data class ProjectsState(val saved: Int = 0) : State

private sealed interface ProjectsIntent : UseCaseIntent<ProjectsState> {
    data object Save : ProjectsIntent
    data object Fail : ProjectsIntent
}

private class ProjectsUseCase(
    recorder: UseCaseFlightRecorder<ProjectsState, ProjectsIntent>,
) : AbstractUseCaseProcessor<ProjectsState, ProjectsIntent>(ProjectsState(), listOf(recorder)) {

    override suspend fun map(intent: ProjectsIntent) {
        when (intent) {
            ProjectsIntent.Save -> reduce { it.copy(saved = it.saved + 1) }
            ProjectsIntent.Fail -> throw IllegalStateException("repository unavailable")
        }
    }
}

// ── Presentation ───────────────────────────────────────────────────────────────

// Counts rather than flags: a reduction back to a value the state already holds is not a
// transition and is deliberately not reported, so a flag that starts false and is set to false
// would make the presentation half of the trace vanish.
private data class ScreenState(val savedCount: Int = 0) : ViewState

private sealed interface ScreenIntent : ViewIntent {
    data object SaveProject : ScreenIntent
    data object SaveFailing : ScreenIntent
    data object NoOpReduce : ScreenIntent
}

private sealed interface ScreenEffect : SideEffect

private class ScreenProcessor(
    private val projects: ProjectsUseCase,
    recorder: FlightRecorder<ScreenIntent, ScreenState, ScreenEffect>,
) : PresentationProcessor<ScreenIntent, ScreenState, ScreenEffect>(
    ScreenState(),
    interceptors = listOf(recorder),
) {
    override suspend fun map(intent: ScreenIntent): Action<ScreenState, ScreenEffect> =
        when (intent) {
            ScreenIntent.SaveProject -> useCase {
                projects.dispatch(ProjectsIntent.Save)
                reduce { copy(savedCount = savedCount + 1) }
            }
            ScreenIntent.SaveFailing -> useCase {
                projects.dispatch(ProjectsIntent.Fail)
            }
            ScreenIntent.NoOpReduce -> reduce { copy(savedCount = savedCount) }
        }
}

private fun wire(): Triple<ScreenProcessor, ProjectsUseCase, TraceBuffer> {
    val buffer = TraceBuffer()
    val projects = ProjectsUseCase(UseCaseFlightRecorder(buffer))
    val screen = ScreenProcessor(projects, FlightRecorder(buffer))
    return Triple(screen, projects, buffer)
}

// ── Tests ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseFlightRecorderTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("a shared TraceBuffer across both layers") {

        it("interleaves presentation and domain events into one sequence") {
            val (screen, _, buffer) = wire()

            screen.dispatch(ScreenIntent.SaveProject)

            buffer.events.map { it.source to it.type } shouldContainExactly listOf(
                TraceEventSource.Presentation to TraceEventType.Intent,
                TraceEventSource.Presentation to TraceEventType.ActionMapped,
                TraceEventSource.Presentation to TraceEventType.ActionExecuting,
                TraceEventSource.Domain to TraceEventType.Intent,
                TraceEventSource.Domain to TraceEventType.StateChanged,
                TraceEventSource.Presentation to TraceEventType.StateChanged,
            )
        }

        it("gives every event of one interaction the same correlation id") {
            val (screen, _, buffer) = wire()

            screen.dispatch(ScreenIntent.SaveProject)

            buffer.events.map { it.correlationId }.toSet() shouldBe setOf(0L)
        }

        it("keeps one continuous sequence across the layers") {
            val (screen, _, buffer) = wire()

            screen.dispatch(ScreenIntent.SaveProject)

            val seqs = buffer.events.map { it.seq }
            seqs shouldContainExactly (0L until seqs.size.toLong()).toList()
        }

        it("records the domain state change with its previous value for diffing") {
            val (screen, _, buffer) = wire()

            screen.dispatch(ScreenIntent.SaveProject)

            val domainChange = buffer.events.single {
                it.source == TraceEventSource.Domain && it.type == TraceEventType.StateChanged
            }
            domainChange.previousState shouldBe "ProjectsState(saved=0)"
            domainChange.payload shouldBe "ProjectsState(saved=1)"
        }

        // A domain failure surfaces twice by design — what failed, and what it interrupted.
        // Both are worth having, and both carry the same correlation id.
        it("records a domain failure from both layers under one correlation id") {
            val (screen, _, buffer) = wire()

            screen.dispatch(ScreenIntent.SaveFailing)

            val errors = buffer.events.filter { it.type == TraceEventType.Error }
            errors.map { it.source } shouldContainExactly listOf(
                TraceEventSource.Domain,
                TraceEventSource.Presentation,
            )
            errors.map { it.correlationId }.toSet() shouldBe setOf(0L)
        }

        // The presentation reduction above increments a counter rather than resetting a flag,
        // because a reduction to a value the state already holds is not a transition. Worth an
        // explicit case: a no-op reduction must leave no trace in either layer.
        it("records nothing for a reduction that changed nothing") {
            val buffer = TraceBuffer()
            val projects = ProjectsUseCase(UseCaseFlightRecorder(buffer))
            val screen = ScreenProcessor(projects, FlightRecorder(buffer))

            screen.dispatch(ScreenIntent.NoOpReduce)

            buffer.events.none { it.type == TraceEventType.StateChanged } shouldBe true
        }

        it("separates interactions by correlation id") {
            val (screen, _, buffer) = wire()

            screen.dispatch(ScreenIntent.SaveProject)
            screen.dispatch(ScreenIntent.SaveProject)

            buffer.events
                .filter { it.source == TraceEventSource.Domain }
                .map { it.correlationId } shouldContainExactly listOf(0L, 0L, 1L, 1L)
        }
    }

    describe("a trace without domain recording") {

        it("defaults every event to the presentation source") {
            val buffer = TraceBuffer()
            val projects = ProjectsUseCase(UseCaseFlightRecorder(TraceBuffer()))
            ScreenProcessor(projects, FlightRecorder(buffer)).dispatch(ScreenIntent.SaveProject)

            buffer.events.map { it.source }.toSet() shouldBe setOf(TraceEventSource.Presentation)
        }
    }
})
