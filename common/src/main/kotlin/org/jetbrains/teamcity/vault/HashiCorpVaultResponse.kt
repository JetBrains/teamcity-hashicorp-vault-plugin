package org.jetbrains.teamcity.vault

import org.springframework.vault.support.VaultResponse

sealed class HashiCorpVaultResponse<out L : Throwable, out R : VaultResponse>

data class Error(val value: Exception) : HashiCorpVaultResponse<Exception, Nothing>(){
    constructor(errorMessage: String): this(IllegalStateException(errorMessage))
}
data class Response(val value: VaultResponse) : HashiCorpVaultResponse<Nothing, VaultResponse>()