package org.jetbrains.teamcity.vault.server

import com.bettercloud.vault.SslConfig
import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.util.EventDispatcher
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.agent.SimpleVaultConfig
import java.util.concurrent.TimeUnit

class VaultConnector(dispatcher: EventDispatcher<BuildServerListener>) {
    init {
        dispatcher.addListener(object : BuildServerAdapter() {
            override fun buildFinished(build: SRunningBuild) {
                val info = myBuildsTokens.remove(build.buildId) ?: return
                myPendingRemoval.add(info)
                revoke(info)
            }
        })
    }

    private fun revoke(info: LeasedWrappedTokenInfo) {
        val settings = info.connection
        val config: VaultConfig
        try {
            config = SimpleVaultConfig()
                    .address(settings.url)
                    .sslConfig(SslConfig().verify(settings.verifySsl))
                    .build()
        } catch(e: VaultException) {
            throw e
        }

        val vault = Vault(config).withRetries(3, TimeUnit.MINUTES.toMillis(3).toInt())
        try {
            config.token(info.wrapped) // TODO: find way without storing token on server
            vault.logical().write("/auth/token/revoke-accessor", mapOf("accessor" to info.accessor))
            myPendingRemoval.remove(info)
        } catch(e: VaultException) {
            throw e
        }
    }

    // TODO: Support server restart
    private val myBuildsTokens: MutableMap<Long, LeasedWrappedTokenInfo> = HashMap()
    private val myPendingRemoval: MutableSet<LeasedWrappedTokenInfo> = HashSet()

    fun requestWrappedToken(build: SBuild, settings: VaultFeatureSettings): String? {
        val info = myBuildsTokens[build.buildId]
        if (info != null) return info.wrapped

        val config: VaultConfig
        try {
            config = SimpleVaultConfig()
                    .address(settings.url)
                    .sslConfig(SslConfig().verify(settings.verifySsl))
                    .build()
        } catch(e: VaultException) {
            throw e
        }

        val vault = Vault(config).withRetries(3, TimeUnit.MINUTES.toMillis(3).toInt())
        try {
            val response = vault.auth().loginByAppRole("approle", settings.roleId, settings.secretId)
            myBuildsTokens[build.buildId] = LeasedWrappedTokenInfo(response.authClientToken, "accessor", settings)
            return response.authClientToken
        } catch(e: VaultException) {
            throw e
        }
    }
}

data class LeasedWrappedTokenInfo(val wrapped: String, val accessor: String, val connection: VaultFeatureSettings)
