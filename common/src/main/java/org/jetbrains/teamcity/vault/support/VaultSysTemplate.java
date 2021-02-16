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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.support.*;
import org.springframework.vault.support.VaultMount.VaultMountBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import java.util.*;

/**
 * Default implementation of {@link VaultSysOperations}.
 *
 * @author Mark Paluch
 */
public class VaultSysTemplate {

	private static final GetMounts GET_AUTH_MOUNTS = new GetMounts("sys/auth");

	private static final Health HEALTH = new Health();

	private final VaultTemplate vaultOperations;

	/**
	 * Create a new {@link VaultSysTemplate} with the given {@link VaultOperations}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultSysTemplate(VaultTemplate vaultOperations) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");

		this.vaultOperations = vaultOperations;
	}

	public void authMount(final String path, final VaultMount vaultMount)
			throws VaultException {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");

		vaultOperations.write(String.format("sys/auth/%s", path), vaultMount);
	}

	public Map<String, VaultMount> getAuthMounts() throws VaultException {
		return requireResponse(vaultOperations.doWithSession(GET_AUTH_MOUNTS));
	}



	public VaultHealth health() {
		return requireResponse(vaultOperations.doWithVault(HEALTH));
	}

	private static <T> T requireResponse(@Nullable T response) {

		Assert.state(response != null, "Response must not be null");

		return response;
	}

	private static class GetMounts implements
			RestOperationsCallback<Map<String, VaultMount>> {

		private static final ParameterizedTypeReference<VaultMountsResponse> MOUNT_TYPE_REF = new ParameterizedTypeReference<VaultMountsResponse>() {
		};

		private final String path;

		GetMounts(String path) {
			this.path = path;
		}

		@Override
		public Map<String, VaultMount> doWithRestOperations(RestOperations restOperations) {

			ResponseEntity<VaultMountsResponse> exchange = restOperations.exchange(path,
					HttpMethod.GET, null, MOUNT_TYPE_REF, Collections.emptyMap());

			VaultMountsResponse body = exchange.getBody();

			Assert.state(body != null, "Get mounts response must not be null");

			if (body.getData() != null) {
				return body.getData();
			}

			return body.getTopLevelMounts();
		}

		private static class VaultMountsResponse extends
				VaultResponseSupport<Map<String, VaultMount>> {

			private Map<String, VaultMount> topLevelMounts = new HashMap<String, VaultMount>();

			@JsonIgnore
			public Map<String, VaultMount> getTopLevelMounts() {
				return topLevelMounts;
			}

			@SuppressWarnings("unchecked")
			@JsonAnySetter
			public void set(String name, Object value) {

				if (!(value instanceof Map)) {
					return;
				}

				//noinspection rawtypes
				Map<String, Object> map = (Map) value;

				if (map.containsKey("type")) {

					VaultMountBuilder builder = VaultMount.builder() //
							.type((String) map.get("type")) //
							.description((String) map.get("description"));// ;

					if (map.containsKey("config")) {
						//noinspection rawtypes
						builder.config((Map) map.get("config"));
					}

					VaultMount vaultMount = builder.build();

					topLevelMounts.put(name, vaultMount);
				}
			}
		}

	}

	private static class Health implements RestOperationsCallback<VaultHealth> {

		@Override
		public VaultHealth doWithRestOperations(RestOperations restOperations) {

			try {
				ResponseEntity<VaultHealthImpl> healthResponse = restOperations.exchange(
						"sys/health", HttpMethod.GET, null, VaultHealthImpl.class);
				return healthResponse.getBody();
			} catch (HttpStatusCodeException responseError) {

				try {
					ObjectMapper mapper = new ObjectMapper();
					return mapper.readValue(responseError.getResponseBodyAsString(),
							VaultHealthImpl.class);
				} catch (Exception ignored) {
					throw responseError;
				}
			}
		}
	}


}
