
package org.jetbrains.teamcity.vault

import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.springframework.util.SocketUtils
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

open class VaultSemiClusterDevContainer(val vault: VaultDevEnvironment) : AutoCloseable, VaultDevEnvironment {

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

    override fun close() {
        stop()
    }

    fun start() {
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

    fun stop() {
        jetty_server?.stop()
    }
}