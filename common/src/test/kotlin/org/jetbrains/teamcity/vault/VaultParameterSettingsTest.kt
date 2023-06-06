package org.jetbrains.teamcity.vault

import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings
import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings.VAULT_QUERY
import org.testng.Assert
import org.testng.annotations.Test
import java.lang.RuntimeException

class VaultParameterSettingsTest {
    @Test
    fun testCreatePojo() {
        val settings = VaultParameterSettings(getVaultParametersMap(VAULT_QUERY, NAMESPACE))
        Assert.assertEquals(VAULT_QUERY, settings.vaultQuery)
        Assert.assertEquals(NAMESPACE, settings.getNamespace())
    }

    @Test(expectedExceptions = arrayOf(IllegalArgumentException::class))
    fun testCreatePojo_MissingVaultQuery() {
        VaultParameterSettings(getVaultParametersMap("", NAMESPACE))
    }

    @Test(expectedExceptions = arrayOf(IllegalArgumentException::class))
    fun testCreatePojo_MissingNamespace() {
        VaultParameterSettings(getVaultParametersMap(VAULT_QUERY, ""))
    }

    @Test(expectedExceptions = arrayOf(IllegalArgumentException::class))
    fun testCreatePojo_DefaultNamespace() {
        VaultParameterSettings(getVaultParametersMap(VAULT_QUERY, ""))
        val settings = VaultParameterSettings(getVaultParametersMap(VAULT_QUERY, ParameterSettings.DEFAULT_UI_PARAMETER_NAMESPACE))
        Assert.assertEquals(VAULT_QUERY, settings.vaultQuery)
        Assert.assertEquals(NAMESPACE, settings.getNamespace())
    }

    private fun getVaultParametersMap(vaultQuery: String, namespace: String) = mapOf(
        ParameterSettings.VAULT_QUERY to vaultQuery,
        ParameterSettings.NAMESPACE to namespace
    )


    companion object {
        const val VAULT_QUERY = "vault/query!/value"
        const val NAMESPACE = "namespace"
    }
}