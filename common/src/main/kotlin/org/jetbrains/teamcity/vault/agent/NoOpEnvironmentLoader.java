package org.jetbrains.teamcity.vault.agent;

import com.bettercloud.vault.EnvironmentLoader;

public class NoOpEnvironmentLoader extends EnvironmentLoader {
    @Override
    public String loadVariable(String name) {
        return null;
    }
}
