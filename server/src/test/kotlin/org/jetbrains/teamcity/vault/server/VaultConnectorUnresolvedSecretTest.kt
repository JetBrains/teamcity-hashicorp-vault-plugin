package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.impl.projects.ProjectCredentialsStorage
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import org.jetbrains.teamcity.vault.Auth
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.testng.Assert.assertTrue
import org.testng.Assert.expectThrows
import org.testng.annotations.Test

/**
 * TW-93821: a login attempt with an unresolved `credentialsJSON:` secure value token must fail fast
 * before any HTTP call to Vault — otherwise a few such logins trigger Vault's user lockout for the
 * whole role. No Vault instance is required here: the URL points to a closed port, so any attempt
 * to actually perform the login request would fail with a connection error instead of the expected
 * [IllegalStateException].
 */
class VaultConnectorUnresolvedSecretTest {
    private val trustStoreProvider = SSLTrustStoreProvider { null }
    private val unresolvedToken = ProjectCredentialsStorage.STORAGE_TYPE + ":00000000-0000-0000-0000-000000000000"

    @Test
    fun doRequestWrappedTokenFailsFastOnUnresolvedSecretId() {
        val settings = VaultFeatureSettings("ns", "http://localhost:1", "", "approle", "role-id", unresolvedToken)

        val ex = expectThrows(IllegalStateException::class.java) {
            VaultConnector.doRequestWrappedToken(settings, trustStoreProvider)
        }

        assertTrue(ex.message!!.contains("unresolved TeamCity secure value token"))
    }

    @Test
    fun doRequestTokenFailsFastOnUnresolvedLdapPassword() {
        val settings = VaultFeatureSettings("ns", "http://localhost:1", "", Auth.LdapServer("user", unresolvedToken, "path"))

        val ex = expectThrows(IllegalStateException::class.java) {
            VaultConnector.doRequestToken(settings, trustStoreProvider)
        }

        assertTrue(ex.message!!.contains("unresolved TeamCity secure value token"))
    }
}
