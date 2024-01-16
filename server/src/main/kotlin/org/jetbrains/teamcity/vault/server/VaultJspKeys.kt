
package org.jetbrains.teamcity.vault.server

import org.jetbrains.teamcity.vault.VaultConstants

@Suppress("PropertyName")
class VaultJspKeys {
    val NAMESPACE = VaultConstants.FeatureSettings.ID
    val URL = VaultConstants.FeatureSettings.URL
    val VAULT_NAMESPACE = VaultConstants.FeatureSettings.VAULT_NAMESPACE

    val ENDPOINT = VaultConstants.FeatureSettings.ENDPOINT

    val ROLE_ID = VaultConstants.FeatureSettings.ROLE_ID
    val SECRET_ID = VaultConstants.FeatureSettings.SECRET_ID

    val USERNAME = VaultConstants.FeatureSettings.USERNAME
    val PASSWORD = VaultConstants.FeatureSettings.PASSWORD
    val PATH = VaultConstants.FeatureSettings.PATH

    val AUTH_METHOD = VaultConstants.FeatureSettings.AUTH_METHOD
    val AUTH_METHOD_APPROLE = VaultConstants.FeatureSettings.AUTH_METHOD_APPROLE
    val AUTH_METHOD_LDAP = VaultConstants.FeatureSettings.AUTH_METHOD_LDAP

    val FAIL_ON_ERROR = VaultConstants.FeatureSettings.FAIL_ON_ERROR

    val DISPLAY_NAME = VaultConstants.FeatureSettings.DISPLAY_NAME
}