
package org.jetbrains.teamcity.vault

import jetbrains.buildServer.serverSide.TeamCityProperties
import java.util.Objects
import kotlin.collections.HashMap

data class VaultQuery(val vaultPath: String, val jsonPath: String? = null, val isWriteEngine: Boolean? = false, private val params: String? = null) {
    companion object {
        const val WRITE_PREFIX: String = "write:";
        const val SECRET_KEY_PREFIX: String = "!/";
        const val PARAMS_PREFIX: String = "?";

        @JvmStatic
        fun extract(path: String, isWriteEngineEnabled: Boolean? = false): VaultQuery {
            val isWriteEngine = isWriteEngineEnabled == true && path.startsWith(WRITE_PREFIX)
            val split = path.substringAfter(WRITE_PREFIX).split(SECRET_KEY_PREFIX, PARAMS_PREFIX, limit = 3)
            val secretKey = if (path.contains(SECRET_KEY_PREFIX)) split[1] else null
            val params = if (isWriteEngine && path.contains(PARAMS_PREFIX)) split.last() else null
            return VaultQuery(split[0], secretKey, isWriteEngine, params)
        }
    }

    val shorten: Shorten = Shorten(vaultPath, params)

    val full: String get() {
        var sb = StringBuilder()
        sb.append(vaultPath)
        jsonPath?.let { sb.append(SECRET_KEY_PREFIX + it) }
        params?.let { sb.append(PARAMS_PREFIX + it) }
        return sb.toString().ensureHasPrefix("/")
    }

    data class Shorten(val path: String, private val params: String? = null) {
        val pathWithParams: String get() = (if (params != null) path + PARAMS_PREFIX + params else path)

        val extractedParams: Map<String, String>? get() = params?.let {
            it.split("&")
                .map { it.split("=")}
                .filter { it.size == 2 }
                .associateTo(HashMap()) { it[0] to it[1] }
        }
    }
}