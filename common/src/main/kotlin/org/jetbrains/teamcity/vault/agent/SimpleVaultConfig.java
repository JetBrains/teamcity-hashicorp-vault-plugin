package org.jetbrains.teamcity.vault.agent;

import com.bettercloud.vault.EnvironmentLoader;
import com.bettercloud.vault.VaultConfig;

public class SimpleVaultConfig extends VaultConfig {
    public SimpleVaultConfig() {
        environmentLoader(new EnvironmentLoader() {
            @Override
            public String loadVariable(String name) {
                return null;
            }
        });
    }
}
