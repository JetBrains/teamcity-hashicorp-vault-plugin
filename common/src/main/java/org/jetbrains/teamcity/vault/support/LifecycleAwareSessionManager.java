/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.teamcity.vault.support;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LoginToken;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Simplified version of org.springframework.vault.authentication.LifecycleAwareSessionManager
 * with ability to override and manipulate everything and improved logging
 */
@SuppressWarnings("LocalVariableHidesMemberVariable")
public class LifecycleAwareSessionManager implements SessionManager, DisposableBean {
    protected final static Logger LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + "VaultLifecycleAwareSessionManager");
    protected final BuildProgressLogger logger;

    protected final ClientAuthentication clientAuthentication;

    protected final RestOperations restOperations;

    protected final TaskScheduler taskScheduler;

    protected final FixedTimeoutRefreshTrigger refreshTrigger;

    protected final Object lock = new Object();

    private volatile VaultToken token;

    public LifecycleAwareSessionManager(@NotNull ClientAuthentication clientAuthentication,
                                        @NotNull TaskScheduler taskScheduler,
                                        @NotNull RestOperations restOperations,
                                        @NotNull FixedTimeoutRefreshTrigger refreshTrigger,
                                        @NotNull BuildProgressLogger logger) {
        this.clientAuthentication = clientAuthentication;
        this.restOperations = restOperations;
        this.taskScheduler = taskScheduler;
        this.refreshTrigger = refreshTrigger;
        this.logger = logger;
    }

    @Override
    public void destroy() {
        VaultToken token = this.token;
        this.token = null;

        if (token instanceof LoginToken) {
            revoke(token);
        }
    }

    protected void revoke(VaultToken token) {
        try {
            restOperations.postForObject("auth/token/revoke-self",
                    new HttpEntity<Object>(VaultHttpHeaders.from(token)), Map.class);
        } catch (HttpStatusCodeException e) {
            String message = "Cannot revoke HashiCorp Vault token: " + VaultResponses.getError(e.getResponseBodyAsString());
            LOG.warn(message, e);
            logger.warning(message);
        } catch (RuntimeException e) {
            String message = "Cannot revoke HashiCorp Vault token";
            LOG.warn(message, e);
            logger.warning(message + ": " + e.getMessage());
        }
    }

    /**
     * Performs a token refresh. Create a new token if no token was obtained before. If a
     * token was obtained before, it uses self-renewal to renew the current token.
     * Client-side errors (like permission denied) indicate the token cannot be renewed
     * because it's expired or simply not found.
     *
     * @return {@literal true} if the refresh was successful. {@literal false} if a new
     * token was obtained or refresh failed.
     */
    protected boolean renewToken() {
        LOG.info("Renewing HashiCorp Vault token");

        VaultToken token = this.token;
        if (token == null) {
            return false;
        }

        try {
            VaultResponse vaultResponse = restOperations.postForObject(
                    "auth/token/renew-self",
                    new HttpEntity<Object>(VaultHttpHeaders.from(token)),
                    VaultResponse.class);
            LoginToken renewed = from(vaultResponse.getAuth());
            LOG.info(String.format("Received token: LoginToken(renewable=%b, lease_duration=%d):", renewed.isRenewable(), renewed.getLeaseDuration()));

            long validTtlThreshold = TimeUnit.MILLISECONDS.toSeconds(refreshTrigger.getValidTtlThreshold());
            if (renewed.getLeaseDuration() <= validTtlThreshold) {
                LOG.warn(String.format("Token TTL (%s) exceeded validity TTL threshold (%s). Dropping token.",
                        renewed.getLeaseDuration(), validTtlThreshold));
                logger.warning("HashiCorp Vault token exceed validity TTL threshold and would be dropped.");
                this.token = null;
                return false;
            }

            this.token = renewed;
            LOG.info("Renewed HashiCorp Vault token successfully");
            return true;
        } catch (HttpStatusCodeException e) {
            logger.warning("Cannot renew HashiCorp Vault token, resetting token and performing re-login: " + e.getStatusCode() + " " + VaultResponses.getError(e.getResponseBodyAsString()));
            LOG.warn("Cannot renew HashiCorp Vault token, resetting token and performing re-login: " + e.getStatusCode() + " " + VaultResponses.getError(e.getResponseBodyAsString()), e);
            this.token = null;
            return false;
        } catch (RuntimeException e) {
            logger.warning("Cannot renew HashiCorp Vault token, resetting token and performing re-login: " + e.getMessage());
            LOG.warn("Cannot renew HashiCorp Vault token, resetting token and performing re-login: " + e.getMessage());
            this.token = null;
            return false;
        }
    }

    @Override
    public VaultToken getSessionToken() {
        if (token == null) {
            synchronized (lock) {
                if (token == null) {
                    token = login();

                    if (isTokenRenewable()) {
                        scheduleRenewal();
                    }
                }
            }
        }

        return token;
    }

    @SuppressWarnings("VariableNotUsedInsideIf")
    protected VaultToken login() {
        VaultToken token = clientAuthentication.login();
        if (token instanceof LoginToken) {
            LOG.info(String.format("Logged in with token: LoginToken(renewable=%b, lease_duration=%d):", ((LoginToken) token).isRenewable(), ((LoginToken) token).getLeaseDuration()));
        } else if (token != null) {
            LOG.info("Logged in with token: regular VaultToken");
        } else {
            LOG.info("Logged in with token: null");
        }
        return token;
    }

    protected boolean isTokenRenewable() {
        VaultToken token = this.token;
        if (token instanceof LoginToken) {
            LoginToken loginToken = (LoginToken) token;
            return loginToken.getLeaseDuration() > 0L && loginToken.isRenewable();
        }
        return false;
    }

    protected void scheduleRenewal() {
        VaultToken token = this.token;
        if (token instanceof LoginToken) {
            final Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (isTokenRenewable()) {
                            boolean mayRenewAgainLater = renewToken();
                            if (mayRenewAgainLater) {
                                scheduleRenewal();
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Cannot renew HashiCorp Vault token: " + e.getMessage());
                        LOG.error("Cannot renew HashiCorp Vault token", e);
                    }
                }
            };
            Date startTime = refreshTrigger.nextExecutionTime((LoginToken) token);
            LOG.info("Scheduling HashiCorp Vault token refresh to " + startTime);
            taskScheduler.schedule(task, startTime);
        }
    }

    public static class FixedTimeoutRefreshTrigger {
        protected final long duration;
        protected final long validTtlThreshold;

        protected final TimeUnit timeUnit;

        public FixedTimeoutRefreshTrigger(long timeout, TimeUnit timeUnit) {
            Assert.isTrue(timeout >= 0L,
                    "Timeout duration must be greater or equal to zero");
            Assert.notNull(timeUnit, "TimeUnit must not be null");

            this.duration = timeout;
            this.validTtlThreshold = timeUnit.toMillis(duration) + 2000L;
            this.timeUnit = timeUnit;
        }

        public Date nextExecutionTime(LoginToken loginToken) {
            long milliseconds = Math.max(
                    TimeUnit.SECONDS.toMillis(1L),
                    TimeUnit.SECONDS.toMillis(loginToken.getLeaseDuration())
                            - timeUnit.toMillis(duration));

            return new Date(System.currentTimeMillis() + milliseconds);
        }

        public long getValidTtlThreshold() {
            return validTtlThreshold;
        }
    }

    protected static LoginToken from(Map<String, Object> auth) {
        String token = (String) auth.get("client_token");
        Boolean renewable = (Boolean) auth.get("renewable");
        Number leaseDuration = (Number) auth.get("lease_duration");

        if (renewable != null && renewable) {
            return LoginToken.renewable(token, leaseDuration.longValue());
        }

        if (leaseDuration != null) {
            return LoginToken.of(token, leaseDuration.longValue());
        }

        return LoginToken.of(token);
    }

}
