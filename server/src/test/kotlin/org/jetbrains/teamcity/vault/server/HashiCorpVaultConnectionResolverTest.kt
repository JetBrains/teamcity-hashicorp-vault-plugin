
package org.jetbrains.teamcity.vault.server

import assertk.assertions.containsAll
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase
import jetbrains.buildServer.serverSide.oauth.OAuthConstants
import org.jetbrains.teamcity.vault.VaultConstants
import org.jetbrains.teamcity.vault.VaultFeatureSettings
import org.jetbrains.teamcity.vault.server.HashiCorpVaultConnectionResolver.ParameterNamespaceCollisionException
import org.mockito.Mockito
import org.mockito.testng.MockitoTestNGListener
import org.testng.Assert.expectThrows
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(MockitoTestNGListener::class)
class HashiCorpVaultConnectionResolverTest : BaseServerTestCase() {
    private lateinit var connectionResolver: HashiCorpVaultConnectionResolver

    @BeforeMethod
    fun beforeEach() {
        connectionResolver = HashiCorpVaultConnectionResolver(Mockito.mock(VaultConnector::class.java))
    }

    @Test
    fun getVaultConnectionsTestNoVaultConnections() {
        val project = buildTestProjectHierarchy(
                ProjectAndNamespaces("some_project") // no vault connections
        )
        assertTrue(connectionResolver.getVaultConnections(project).isEmpty())
        assertNull(connectionResolver.getVaultConnection(project, "non_existing_namespace"))
    }

    @Test
    fun getVaultConnectionsTestSingleVaultConnection() {
        val projectName = "some_project"
        val namespace = "some_namespace"

        val project = buildTestProjectHierarchy(
                ProjectAndNamespaces(projectName, namespace) // single vault connection
        )

        val allConnections = connectionResolver.getVaultConnections(project)
        val connection = connectionResolver.getVaultConnection(project, namespace)

        assertNotNull(connection)
        assertEquals(connection, allConnections.single())

        assertEquals(namespace, connection?.id)
        assertEquals(projectName, connection.extractTestProjectName())
    }

    @Test
    fun getVaultConnectionsTestVaultConnectionWithDefaultNamespaceAndCustomId() {
        val settings = VaultFeatureSettings(
            url = "http://testurl.com",
            vaultNamespace = "vaultNamespace",
        )

        myProject.addFeature("customID", OAuthConstants.FEATURE_TYPE, buildMap {
            putAll(settings.toFeatureProperties())
            putIfAbsent(OAuthConstants.OAUTH_TYPE_PARAM, VaultConstants.FeatureSettings.FEATURE_TYPE)
        })

        myProject.addFeature("PROJECT_EXT_50", OAuthConstants.FEATURE_TYPE, buildMap {
            putAll(settings.toFeatureProperties())
            putIfAbsent(OAuthConstants.OAUTH_TYPE_PARAM, VaultConstants.FeatureSettings.FEATURE_TYPE)
            putIfAbsent(VaultConstants.FeatureSettings.ID, "") // Old connections with default namespace have the parameter present with empty string
        })

        val allConnections = connectionResolver.getVaultConnections(myProject)
        val settingsWithCustomId = settings.copy(
            id = "customID"
        )
        assertk.assertThat(allConnections).containsAll(settings, settingsWithCustomId)
    }

    @Test
    fun getVaultConnectionsTestMultipleVaultConnections() {
        val parentProjectName = "parent_project"
        val subProjectName = "sub_project"

        val parentProjectConnectionNamespaces = arrayOf("parent_project_ns1", "parent_project_ns2")
        val subProjectConnectionNamespaces = arrayOf("subproject_ns1", "subproject_ns2")

        val project = buildTestProjectHierarchy(
                ProjectAndNamespaces(parentProjectName, *parentProjectConnectionNamespaces),
                ProjectAndNamespaces(subProjectName, *subProjectConnectionNamespaces)
        )

        subProjectConnectionNamespaces.forEach { namespace ->
            val connection = connectionResolver.getVaultConnection(project, namespace)
            assertEquals(connection?.id, namespace)
            assertEquals(connection.extractTestProjectName(), subProjectName)
        }

        parentProjectConnectionNamespaces.forEach { namespace ->
            val connection = connectionResolver.getVaultConnection(project, namespace)
            assertEquals(connection?.id, namespace)
            assertEquals(connection.extractTestProjectName(), parentProjectName)
        }

        val allConnections = connectionResolver.getVaultConnections(project)
        assertEquals(allConnections.size, subProjectConnectionNamespaces.size + parentProjectConnectionNamespaces.size)

        val allConnectionsGroupedByProject = allConnections
                .groupBy(
                        keySelector = { settings -> settings.extractTestProjectName() },
                        valueTransform = { settings -> settings.id }
                )
        assertEquals(subProjectConnectionNamespaces.toSet(), checkNotNull(allConnectionsGroupedByProject[subProjectName]).toSet())
        assertEquals(parentProjectConnectionNamespaces.toSet(), checkNotNull(allConnectionsGroupedByProject[parentProjectName]).toSet())
    }

    @Test
    fun getVaultConnectionsTestConnectionInheritedFromParentProject() {
        val parentProjectName = "parent_project"
        val subProjectName = "subproject"

        val namespace = "parent_project_namespace"

        val project = buildTestProjectHierarchy(
                ProjectAndNamespaces(parentProjectName, namespace), // connection declared in parent
                ProjectAndNamespaces(subProjectName) // no connections in subproject
        )

        val allConnections = connectionResolver.getVaultConnections(project)
        val connection = connectionResolver.getVaultConnection(project, namespace)

        assertNotNull(connection)
        assertEquals(allConnections.single(), connection)

        assertEquals(namespace, connection?.id)
        assertEquals(parentProjectName, connection.extractTestProjectName())
    }

    @Test
    fun getVaultConnectionsTestConnectionOverrides() {
        val parentProjectName = "parent_project"
        val subProjectName = "subproject"

        val namespace = "namespace1"

        val project = buildTestProjectHierarchy(
                ProjectAndNamespaces(parentProjectName, namespace), // connection declared in parent
                ProjectAndNamespaces(subProjectName, namespace) // connection is overridden in subproject
        )

        val allConnections = connectionResolver.getVaultConnections(project)
        val connection = connectionResolver.getVaultConnection(project, namespace)

        assertNotNull(connection)
        assertEquals(allConnections.single(), connection)

        assertEquals(namespace, connection?.id)
        assertEquals(subProjectName, connection.extractTestProjectName())
    }

    @Test
    fun getVaultConnectionsTestNamespaceCollisionDetectionSingleProject() {
        val projectManager = myServer.projectManager

        val projectName = "parent_project"

        val namespaceWithCollision = "namespace_with_collision"
        val namespaceNoCollision = "namespace_no_collision"

        val project = buildTestProjectHierarchy(
                // two connections with the same parameter namespace -> collisions
                ProjectAndNamespaces(projectName, namespaceWithCollision, namespaceNoCollision, namespaceWithCollision)
        )

        // mustn't throw anything, passed namespace has no collisions
        connectionResolver.getVaultConnection(project, namespaceNoCollision)

        var ex = expectThrows(ParameterNamespaceCollisionException::class.java) {
            connectionResolver.getVaultConnections(project)
        }
        assertEquals(projectName, projectManager.findProjectById(ex.projectId)?.name)
        assertEquals(namespaceWithCollision, ex.namespace)

        ex = expectThrows(ParameterNamespaceCollisionException::class.java) {
            connectionResolver.getVaultConnection(project, namespaceWithCollision)
        }
        assertEquals(projectName, projectManager.findProjectById(ex.projectId)?.name)
        assertEquals(namespaceWithCollision, ex.namespace)
    }

    @Test
    fun getVaultConnectionsTestNamespaceCollisionDetectionMultipleProjects() {
        val projectManager = myServer.projectManager

        val parentProjectName = "parent_project"
        val subProjectName = "subproject"

        val parentProjectNamespace = "parent_project_namespace"
        val subProjectNamespace = "subproject_namespace"

        val project = buildTestProjectHierarchy(
                ProjectAndNamespaces(parentProjectName, parentProjectNamespace, parentProjectNamespace), // collision
                ProjectAndNamespaces(subProjectName, subProjectNamespace) // no collision here
        )

        // mustn't throw anything, passed namespace has no collisions
        connectionResolver.getVaultConnection(project, subProjectNamespace)

        var ex = expectThrows(ParameterNamespaceCollisionException::class.java) {
            connectionResolver.getVaultConnections(project)
        }
        assertEquals(parentProjectName, projectManager.findProjectById(ex.projectId)?.name)
        assertEquals(parentProjectNamespace, ex.namespace)

        ex = expectThrows(ParameterNamespaceCollisionException::class.java) {
            connectionResolver.getVaultConnection(project, parentProjectNamespace)
        }
        assertEquals(parentProjectName, projectManager.findProjectById(ex.projectId)?.name)
        assertEquals(parentProjectNamespace, ex.namespace)
    }

    private data class ProjectAndNamespaces(val projectName: String, val vaultParameterNamespaces: List<String>) {
        constructor(projectName: String, vararg vaultParameterNamespaces: String) : this(projectName, vaultParameterNamespaces.toList())
    }

    /**
     * To extract projectName from [VaultFeatureSettings] in tests use [VaultFeatureSettings.extractTestProjectName].
     *
     * @param projectsAndNamespaces Root project should be passed first, subproject last.
     * @return The deepest project in hierarchy (the most distant from the root project)
     */
    private fun buildTestProjectHierarchy(vararg projectsAndNamespaces: ProjectAndNamespaces): SProject {
        require(projectsAndNamespaces.isNotEmpty())

        var currentProject = myProject
        projectsAndNamespaces.forEach { projectAndNamespaces ->
            currentProject = currentProject.createProject(projectAndNamespaces.projectName, projectAndNamespaces.projectName)

            projectAndNamespaces.vaultParameterNamespaces.forEach { namespace ->
                val settings = VaultFeatureSettings(
                        namespace = namespace,
                        url = "test_project_${projectAndNamespaces.projectName}", // save project name inside settings metadata to extract it in test
                        vaultNamespace = "vaultNamespace",
                        endpoint = "endpoint",
                        roleId = "roleId",
                        secretId = "secretId",
                        failOnError = true
                )

                currentProject.addFeature(OAuthConstants.FEATURE_TYPE, buildMap {
                    putAll(settings.toFeatureProperties())
                    putIfAbsent(OAuthConstants.OAUTH_TYPE_PARAM, VaultConstants.FeatureSettings.FEATURE_TYPE)
                    putIfAbsent(VaultConstants.FeatureSettings.ID, settings.id)
                })
            }
        }
        return currentProject
    }

    private fun VaultFeatureSettings?.extractTestProjectName() = this?.url?.substringAfter("test_project_")
}