package org.jetbrains.teamcity.vault.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.identifiers.BaseExternalIdGenerator
import jetbrains.buildServer.serverSide.identifiers.BaseExternalIdGenerator.generateNewId
import jetbrains.buildServer.serverSide.impl.IdGeneratorRegistry
import jetbrains.buildServer.serverSide.oauth.identifiers.OAuthConnectionsIdGenerator
import jetbrains.buildServer.util.CachingTypedIdGenerator
import jetbrains.buildServer.util.IdentifiersGenerator
import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings

class HashicorpVaultConnectionIdGenerator(
    oAuthConnectionsIdGenerator: OAuthConnectionsIdGenerator,
    idGeneratorRegistry: IdGeneratorRegistry,
    private val hashiCorpVaultConnectionResolver: HashiCorpVaultConnectionResolver
) : CachingTypedIdGenerator {

    val ID_GENERATOR_TYPE: String = FeatureSettings.FEATURE_TYPE

    val VAULT_CONNECTION_ID_PREFIX = "hashicorpVaultConnection"

    val INITIAL_VAULT_CONNECTION_ID = 0

    val LOG: Logger = Logger.getInstance(HashicorpVaultConnectionIdGenerator::class.java.getName())
    val myIdGenerator: IdentifiersGenerator

    init {
        oAuthConnectionsIdGenerator.registerProviderTypeGenerator(ID_GENERATOR_TYPE, this)
        myIdGenerator = idGeneratorRegistry.acquireIdGenerator(VAULT_CONNECTION_ID_PREFIX)
    }

    fun formatId(connectionId: String, newIdNumber: Int): String {
        return String.format("%s_%s", connectionId, newIdNumber.toString())
    }

    public override fun newId(props: MutableMap<String?, String?>): String {
        val userDefinedConnId = props.get(FeatureSettings.USER_DEFINED_ID_PARAM)

        // try to remove it from the properties map to avoid it being persisted
        runCatching {
            props.remove(FeatureSettings.USER_DEFINED_ID_PARAM)
        }

        return if (userDefinedConnId == null) {
            myIdGenerator.newId()
        } else {
            userDefinedConnId
        }
    }

    public override fun showNextId(props: Map<String?, String?>) = myIdGenerator.showNextId()

    public override fun addGeneratedId(id: String, props: Map<String?, String?>) {
        myIdGenerator.addGeneratedId(id)
    }

}