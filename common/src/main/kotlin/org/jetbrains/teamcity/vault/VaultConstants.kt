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

    val PARAMETER_PREFIX = "teamcity.vault"
    val URL_PROPERTY_SUFFIX = ".url"
    val VAULT_NAMESPACE_PROPERTY_SUFFIX = ".vault.namespace"
    val WRAPPED_TOKEN_PROPERTY_SUFFIX = ".wrapped.token"
    val TOKEN_REFRESH_TIMEOUT_PROPERTY_SUFFIX = ".token.refresh.timeout"
    val FAIL_ON_ERROR_PROPERTY_SUFFIX = ".failOnError"
    val VAULT_AUTH_PROPERTY_SUFFIX = ".authMethod"

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

        @JvmField val AUTH_METHOD = "auth-method"
        @JvmField val AUTH_METHOD_IAM = AuthMethod.AWS_IAM.id
        @JvmField val AUTH_METHOD_APPROLE = AuthMethod.APPROLE.id
        @JvmField val DEFAULT_AUTH_METHOD = AuthMethod.APPROLE.id

        @JvmField val FAIL_ON_ERROR = "fail-on-error"

        @JvmField val AGENT_SUPPORT_REQUIREMENT = "teamcity.vault.requirement"
        @JvmField val AGENT_SUPPORT_REQUIREMENT_VALUE = "%$FEATURE_SUPPORTED_AGENT_PARAMETER%"
    }

    object BehaviourParameters {
        val ExposeEnvSuffix = ".set.env"
    }


    // Special values
    val SPECIAL_FAILED_TO_FETCH = "FAILED_TO_FETCH"
    val SPECIAL_VALUES = setOf<String>(SPECIAL_FAILED_TO_FETCH)
}

