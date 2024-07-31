package org.jetbrains.teamcity.vault.server

import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SProjectFeatureDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.assertj.core.api.BDDAssertions
import org.jetbrains.teamcity.vault.VaultConstants.FeatureSettings
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(MockitoTestNGListener::class)
class VaultConnectionUtilsTest {
    @Mock
    private lateinit var project: SProject

    @Test
    fun testGetFeaturePairs() {
        val settings1 = VaultFeatureSettings(VaultFeatureSettings.getDefaultParameters())
        val settings2 = VaultFeatureSettings("http://localhost:8200", FeatureSettings.DEFAULT_VAULT_NAMESPACE)
        val descriptor1 =
            getFeatureDescriptor(PROJECT_ID_1, settings1)
        val descriptor2 =
            getFeatureDescriptor(PROJECT_ID_2, settings2)

        Mockito.`when`(project.getAvailableFeaturesOfType(OAuthConstants.FEATURE_TYPE)).thenReturn(listOf(descriptor1, descriptor2))

        val featurePairs = VaultConnectionUtils.getFeaturePairs(project)
        Assert.assertEquals(settings1, featurePairs.find { it.first == PROJECT_ID_1 }?.second)
        Assert.assertEquals(settings2, featurePairs.find { it.first == PROJECT_ID_2 }?.second)

        val groupFeatures = VaultConnectionUtils.groupFeatures(featurePairs)
        BDDAssertions.then(groupFeatures).hasSize(1)

        Assert.assertEquals(groupFeatures, VaultConnectionUtils.getFeatures(project))
    }

    private fun getFeatureDescriptor(projectId: String, settings: VaultFeatureSettings) = object : SProjectFeatureDescriptor {
        override fun getId(): String =
            settings.id


        override fun getType(): String {
            throw NotImplementedError()
        }

        override fun getParameters(): MutableMap<String, String> {
            val parameters = settings.toFeatureProperties().toMutableMap()
            parameters.putIfAbsent(OAuthConstants.OAUTH_TYPE_PARAM, FeatureSettings.FEATURE_TYPE)
            return parameters
        }

        override fun getProjectId(): String = projectId
    }

    companion object {
        const val PROJECT_ID_1 = "projectId1"
        const val PROJECT_ID_2 = "projectId2"
    }
}