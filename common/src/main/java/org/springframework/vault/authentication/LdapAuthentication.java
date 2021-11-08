/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.authentication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import static java.util.Collections.singletonMap;

/**
 * LDAP implementation of {@link ClientAuthentication}.
 *
 * @author Mikhael Sokolov
 * @see LdapAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/ldap">LDAP</a>
 * @since 2.4
 */
public class LdapAuthentication implements ClientAuthentication {

    private static final Log logger = LogFactory.getLog(LdapAuthentication.class);

    private final LdapAuthenticationOptions options;

    private final RestOperations restOperations;

    public LdapAuthentication(LdapAuthenticationOptions options, RestOperations restOperations) {
        Assert.notNull(options, "LdapAuthenticationOptions must not be null");
        Assert.notNull(restOperations, "RestOperations must not be null");

        this.options = options;
        this.restOperations = restOperations;
    }

    @Override
    public VaultToken login() throws VaultException {
        return createTokenUsingLdapAuthentication();
    }

    private VaultToken createTokenUsingLdapAuthentication() {
        try {
            VaultResponse response = restOperations.postForObject(String.format("auth/%s/login/%s", options.getPath(), options.getUsername()), singletonMap("password", options.getPassword()), VaultResponse.class);

            logger.debug("Login successful using LDAP credentials");

            return LoginTokenUtil.from(response.getAuth());
        } catch (HttpStatusCodeException e) {
            throw new VaultException(String.format("Cannot login using LDAP: %s", VaultResponses.getError(e.getResponseBodyAsString())), e);
        }
    }
}