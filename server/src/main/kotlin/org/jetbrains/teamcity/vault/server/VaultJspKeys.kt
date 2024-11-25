
package org.jetbrains.teamcity.vault.server

import org.jetbrains.teamcity.vault.VaultConstants

@Suppress("PropertyName")
class VaultJspKeys {
    val CONNECTION_ID = VaultConstants.FeatureSettings.USER_DEFINED_ID_PARAM
    val NAMESPACE = VaultConstants.FeatureSettings.ID
    val URL = VaultConstants.FeatureSettings.URL
    val VAULT_NAMESPACE = VaultConstants.FeatureSettings.VAULT_NAMESPACE

    val ENDPOINT = VaultConstants.FeatureSettings.ENDPOINT

    val ROLE_ID = VaultConstants.FeatureSettings.ROLE_ID
    val SECRET_ID = VaultConstants.FeatureSettings.SECRET_ID

    val USERNAME = VaultConstants.FeatureSettings.USERNAME
    val PASSWORD = VaultConstants.FeatureSettings.PASSWORD
    val PATH = VaultConstants.FeatureSettings.PATH

    val GCP_ROLE = VaultConstants.FeatureSettings.GCP_ROLE
    val GCP_SERVICE_ACCOUNT = VaultConstants.FeatureSettings.GCP_SERVICE_ACCOUNT
    val GCP_ENDPOINT_PATH = VaultConstants.FeatureSettings.GCP_ENDOINT_PATH

    val AUTH_METHOD = VaultConstants.FeatureSettings.AUTH_METHOD
    val AUTH_METHOD_APPROLE = VaultConstants.FeatureSettings.AUTH_METHOD_APPROLE
    val AUTH_METHOD_LDAP = VaultConstants.FeatureSettings.AUTH_METHOD_LDAP
    val AUTH_METHOD_GCP_IAM = VaultConstants.FeatureSettings.AUTH_METHOD_GCP_IAM

    val DISPLAY_NAME = VaultConstants.FeatureSettings.DISPLAY_NAME

    val EMPTY_NAMESPACE = VaultConstants.FeatureSettings.EMPTY_NAMESPACE
}