/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault

import org.springframework.vault.authentication.AppRoleAuthenticationOptions

@Suppress("MayBeConstant")
object VaultConstants {
    val FEATURE_SUPPORTED_AGENT_PARAMETER = "teamcity.vault.supported"

    const val PARAMETER_PREFIX = "teamcity.vault"
    const val TOKEN_REFRESH_TIMEOUT_PROPERTY_SUFFIX = ".token.refresh.timeout"
    const val LEGACY_REFERENCES_USED_SUFFIX = ".legacy.references.used"

    const val PARAMETER_TYPE = "hashicorp-vault"


    @JvmField val VAULT_PARAMETER_PREFIX = "vault:"

    object AgentEnvironment {
        val VAULT_TOKEN = "VAULT_TOKEN"
        val VAULT_ADDR = "VAULT_ADDR"
    }

    object FeatureSettings {
        @JvmField val FEATURE_TYPE = "teamcity-vault"

        // Feature settings
        @JvmField val NAMESPACE = "namespace"
        @JvmField val DEFAULT_PARAMETER_NAMESPACE = ""

        @JvmField val VAULT_NAMESPACE = "vault-namespace"
        @JvmField val DEFAULT_VAULT_NAMESPACE = ""

        @JvmField val URL = "url"

        @JvmField val ENDPOINT = "endpoint"
        @JvmField val DEFAULT_ENDPOINT_PATH = AppRoleAuthenticationOptions.DEFAULT_APPROLE_AUTHENTICATION_PATH

        @JvmField val ROLE_ID = "role-id"
        @JvmField val SECRET_ID = "secure:secret-id"

        @JvmField val USERNAME = "username"
        @JvmField val PASSWORD = "secure:password"
        @JvmField val PATH = "path"

        @JvmField val AUTH_METHOD = "auth-method"
        @JvmField val AUTH_METHOD_IAM = AuthMethod.AWS_IAM.id
        @JvmField val AUTH_METHOD_APPROLE = AuthMethod.APPROLE.id
        @JvmField val AUTH_METHOD_LDAP = AuthMethod.LDAP.id
        @JvmField val DEFAULT_AUTH_METHOD = AuthMethod.APPROLE.id
        @JvmField val WRAPPED_TOKEN = "wrapped-token"

        @JvmField val FAIL_ON_ERROR = "fail-on-error"

        @JvmField val AGENT_SUPPORT_REQUIREMENT = "teamcity.vault.requirement"
        @JvmField val AGENT_SUPPORT_REQUIREMENT_VALUE = "%$FEATURE_SUPPORTED_AGENT_PARAMETER%"

        @JvmField val CONNECTIONS_DETAIL = "teamcity.vault.connection"
    }

    object BehaviourParameters {
        val ExposeEnvSuffix = ".set.env"
    }

    object ParameterSettings{
        const val DEFAULT_UI_PARAMETER_NAMESPACE = "teamcity-default-hashicorp-default-value"
        const val NAMESPACE = "teamcity_hashicorp_namespace"
        const val VAULT_QUERY = "teamcity_hashicorp_vaultQuery"
    }

    object ControllerSettings {
        const val URL = "hashicorp-vault/connection"
        const val WRAP_TOKEN_PATH = "/token/v1"
    }


    // Special values
    val SPECIAL_FAILED_TO_FETCH = "FAILED_TO_FETCH"
    val SPECIAL_VALUES = setOf<String>(SPECIAL_FAILED_TO_FETCH)
}

