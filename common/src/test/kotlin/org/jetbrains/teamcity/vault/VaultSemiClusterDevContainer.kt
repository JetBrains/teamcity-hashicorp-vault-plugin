/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault

import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.junit.runner.Description
import org.springframework.util.SocketUtils
import org.testcontainers.containers.FailureDetectingExternalResource
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

open class VaultSemiClusterDevContainer(val vault: VaultDevEnvironment)
    : FailureDetectingExternalResource(), AutoCloseable, VaultDevEnvironment {

    override val token: String
        get() = vault.token

    override val url: String
        get() = "http://localhost:$jetty_port"

    val used: Boolean
        get() = _used

    private var _used: Boolean = false

    private val jetty_port: Int by lazy {
        SocketUtils.findAvailableTcpPort(8222)
    }

    private var jetty_server: Server? = null

    override fun starting(description: Description?) {
        super.starting(description)
        start()
    }

    override fun finished(description: Description?) {
        super.finished(description)
        stop()
    }

    override fun close() {
        stop()
    }

    private fun start() {
        val server = Server(jetty_port)
        jetty_server = server
        server.handler = object : AbstractHandler() {
            override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
                /*
                    HTTP/1.1 307 Temporary Redirect

                    Cache-Control: no-store
                    Location: https://vault-node-2.internal:8200/v1/auth/approle/login
                    Date: Mon, 27 Nov 2017 15:16:51 GMT
                    Content-Length: 0
                    Content-Type: text/plain; charset=utf-8
                 */
                response.status = HttpServletResponse.SC_TEMPORARY_REDIRECT
                response.addHeader(HttpHeader.LOCATION.toString(), vault.url + target)
                response.addHeader(HttpHeader.CACHE_CONTROL.toString(), "no-store")
                response.setContentLength(0)
                response.contentType = "text/plain; charset=utf-8"
                baseRequest.isHandled = true
                _used = true
            }
        }
        server.start()
    }

    private fun stop() {
        jetty_server?.stop()
    }
}