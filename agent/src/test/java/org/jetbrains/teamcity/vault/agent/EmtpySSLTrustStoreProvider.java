
package org.jetbrains.teamcity.vault.agent;

import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.jetbrains.annotations.Nullable;

import java.security.KeyStore;

class EmtpySSLTrustStoreProvider implements SSLTrustStoreProvider {
    @Nullable
    @Override
    public KeyStore getTrustStore() {
        return null;
    }
}