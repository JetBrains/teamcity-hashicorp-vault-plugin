
package org.jetbrains.teamcity.vault.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.teamcity.vault.VaultDevEnvironment;
import org.jetbrains.teamcity.vault.VaultSemiClusterDevContainer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

import static org.assertj.core.api.BDDAssertions.then;

public class VaultClusterConnectorTest extends VaultConnectorTest {
    public static final VaultSemiClusterDevContainer jetty = new VaultSemiClusterDevContainer(VaultConnectorTest.vault);

    @BeforeClass
    public void startContainer(){
        super.startContainer();
        jetty.start();
    }
    @AfterClass
    public void endContainer(){
        super.endContainer();
        jetty.stop();
    }

    @NotNull
    @Override
    protected VaultDevEnvironment getVault() {
        return jetty;
    }

    @AfterMethod
    public void tearDown() {
        then(jetty.getUsed()).overridingErrorMessage("Jetty redirector should be used in tests").isTrue();
    }
}