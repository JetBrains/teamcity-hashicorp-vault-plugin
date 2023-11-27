package org.jetbrains.teamcity.vault

import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings
import org.jetbrains.teamcity.vault.VaultConstants.ParameterSettings
import org.testng.Assert
import org.testng.annotations.Test

class VaultParameterSettingsTest {
    @Test
    fun testCreatePojo() {
        val settings = VaultParameterSettings(getVaultParametersMap(TEST_VAULT_QUERY, TEST_NAMESPACE))
        Assert.assertEquals(TEST_VAULT_QUERY, settings.vaultQuery)
        Assert.assertEquals(TEST_NAMESPACE, settings.namespace)
    }

    @Test(expectedExceptions = [IllegalArgumentException::class])
    fun testCreatePojo_NullVaultQuery() {
        VaultParameterSettings(getVaultParametersMap(null, TEST_NAMESPACE))
    }

    @Test(expectedExceptions = [IllegalArgumentException::class])
    fun testCreatePojo_EmptyVaultQuery() {
        VaultParameterSettings(getVaultParametersMap("", TEST_NAMESPACE))
    }

    @Test(expectedExceptions = [IllegalArgumentException::class])
    fun testCreatePojo_MissingNamespace() {
        VaultParameterSettings(getVaultParametersMap(TEST_VAULT_QUERY, ParameterSettings.NAMESPACE_NOT_SELECTED_VALUE))
    }

    @Test
    fun testCreatePojo_DefaultNamespace() {
        val settings = VaultParameterSettings(getVaultParametersMap(TEST_VAULT_QUERY, FeatureSettings.DEFAULT_ID))
        Assert.assertEquals(TEST_VAULT_QUERY, settings.vaultQuery)
        Assert.assertEquals(FeatureSettings.DEFAULT_ID, settings.namespace)
    }

    private fun getVaultParametersMap(vaultQuery: String?, id: String?) = buildMap {
        if (vaultQuery != null) put(ParameterSettings.VAULT_QUERY, vaultQuery)
        if (id != null) put(ParameterSettings.VAULT_ID, id)
    }


    companion object {
        const val TEST_VAULT_QUERY = "vault/query!/value"
        const val TEST_NAMESPACE = "namespace"
    }
}