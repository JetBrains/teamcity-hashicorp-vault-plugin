/*
 * Copyright 2016-2018 the original author or authors.
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
package org.jetbrains.teamcity.vault.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.springframework.vault.support.VaultHealth;

@JsonIgnoreProperties(ignoreUnknown = true)
class VaultHealthImpl implements VaultHealth {

	private final boolean initialized;
	private final boolean sealed;
	private final boolean standby;
	private final int serverTimeUtc;
	private final boolean performanceStandby;
	private final boolean recoveryReplicationSecondary;

	@Nullable
	private final String version;

	private VaultHealthImpl(@JsonProperty("initialized") boolean initialized,
							@JsonProperty("sealed") boolean sealed,
							@JsonProperty("standby") boolean standby,
							@JsonProperty("server_time_utc") int serverTimeUtc,
							@Nullable @JsonProperty("version") String version,
							@JsonProperty("performance_standby") boolean performanceStandby,
							@JsonProperty("recovery_replication_secondary") boolean recoveryReplicationSecondary
							) {

		this.initialized = initialized;
		this.sealed = sealed;
		this.standby = standby;
		this.serverTimeUtc = serverTimeUtc;
		this.version = version;
		this.performanceStandby = performanceStandby;
		this.recoveryReplicationSecondary = recoveryReplicationSecondary;
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public boolean isSealed() {
		return sealed;
	}

	@Override
	public boolean isStandby() {
		return standby;
	}

	@Override
	public boolean isPerformanceStandby() {
		return performanceStandby;
	}

	@Override
	public boolean isRecoveryReplicationSecondary() {
		return recoveryReplicationSecondary;
	}

	@Override
	public int getServerTimeUtc() {
		return serverTimeUtc;
	}

	@Override
	@Nullable
	public String getVersion() {
		return version;
	}
}
