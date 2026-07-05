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

package org.fuusio.kide.decompose

import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import org.fuusio.kide.presentation.Action
import org.fuusio.kide.presentation.PresentationProcessor
import org.fuusio.kide.presentation.SideEffect
import org.fuusio.kide.presentation.ViewIntent
import org.fuusio.kide.presentation.ViewState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class TestState(val value: Int = 0) : ViewState
private sealed interface TestIntent : ViewIntent
private sealed interface TestEffect : SideEffect

private class TestProcessor : PresentationProcessor<TestIntent, TestState, TestEffect>(
    initialState = TestState(),
    processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    override suspend fun map(intent: TestIntent): Action<TestState, TestEffect>? = null
}

class InstanceKeeperHostTest {

    @Test
    fun retainedProcessorReturnsSameInstanceForSameKey() {
        val keeper = InstanceKeeperDispatcher()
        val first = keeper.retainedProcessor("home") { TestProcessor() }
        val second = keeper.retainedProcessor("home") { TestProcessor() }
        assertSame(first, second)
        keeper.destroy()
    }

    @Test
    fun retainedProcessorCreatesDistinctInstancesForDistinctKeys() {
        val keeper = InstanceKeeperDispatcher()
        val first = keeper.retainedProcessor("a") { TestProcessor() }
        val second = keeper.retainedProcessor("b") { TestProcessor() }
        assertTrue(first !== second)
        keeper.destroy()
    }

    @Test
    fun destroyClosesRetainedProcessor() {
        val keeper = InstanceKeeperDispatcher()
        val processor = keeper.retainedProcessor("home") { TestProcessor() }
        assertTrue(processor.processorScope.isActive)
        keeper.destroy()
        assertFalse(processor.processorScope.isActive)
    }

    @Test
    fun hostExposesProcessorAndClosesItOnDestroy() {
        val processor = TestProcessor()
        val host = InstanceKeeperHost(processor)
        assertSame(processor, host.processor)
        host.onDestroy()
        assertFalse(processor.processorScope.isActive)
        host.onDestroy() // idempotent
    }
}
