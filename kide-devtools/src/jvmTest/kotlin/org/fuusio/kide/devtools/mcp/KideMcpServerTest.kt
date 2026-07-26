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

package org.fuusio.kide.devtools.mcp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/*
 * Startup robustness for the agent port.
 *
 * KideMcpServer.start is called from Application.onCreate. Until 2.0.0 it constructed its
 * ServerSocket unguarded, so a BindException took the whole application down before it drew a
 * frame — and the trigger is mundane: reinstalling over a running build, or a previous
 * process whose listening socket is still in TIME_WAIT. A debugging tool that can kill the
 * application it exists to debug is worse than no debugging tool.
 */

/** Binds the loopback address on an ephemeral port and returns the socket, still listening. */
private fun occupyPort(): ServerSocket =
    ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)
    }

/** An ephemeral port that was free a moment ago. */
private fun freePort(): Int = ServerSocket().use { socket ->
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)
    socket.localPort
}

class KideMcpServerTest : DescribeSpec({

    // The server is a global object; leaving it listening would leak into later specs.
    afterTest { KideMcpServer.stop() }

    describe("KideMcpServer.start") {

        it("starts on a free port") {
            KideMcpServer.start(freePort()) shouldBe true
        }

        // The regression. Before 2.0.0 this threw BindException out of Application.onCreate.
        it("returns false instead of throwing when the port is already held") {
            occupyPort().use { blocker ->
                KideMcpServer.start(blocker.localPort) shouldBe false
            }
        }

        it("leaves the server stopped after a failed bind, so a later start can succeed") {
            occupyPort().use { blocker ->
                KideMcpServer.start(blocker.localPort) shouldBe false
            }

            KideMcpServer.start(freePort()) shouldBe true
        }

        it("is idempotent while running") {
            val port = freePort()

            KideMcpServer.start(port) shouldBe true
            KideMcpServer.start(port) shouldBe true
        }

        it("can be restarted on the same port after stopping") {
            val port = freePort()
            KideMcpServer.start(port) shouldBe true

            KideMcpServer.stop()

            // SO_REUSEADDR is what makes this work: without it the just-closed listening socket
            // lingers in TIME_WAIT and the rebind fails.
            KideMcpServer.start(port) shouldBe true
        }
    }
})
