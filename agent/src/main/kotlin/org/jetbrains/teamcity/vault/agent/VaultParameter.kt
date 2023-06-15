package org.jetbrains.teamcity.vault.agent

import org.jetbrains.teamcity.vault.VaultParameterSettings

data class VaultParameter(val parameterKey: String, val vaultParameterSettings: VaultParameterSettings)