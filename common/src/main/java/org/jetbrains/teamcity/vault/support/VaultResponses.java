/*
 * Copyright 2017 the original author or authors.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Utility methods to unwrap Vault responses and build {@link VaultException}.
 *
 * @author Mark Paluch
 */
public abstract class VaultResponses {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
			OBJECT_MAPPER);

	/**
	 * Build a {@link VaultException} given {@link HttpStatusCodeException}.
	 * @param e must not be {@literal null}.
	 * @return the {@link VaultException}.
	 */
	public static VaultException buildException(HttpStatusCodeException e) {

		Assert.notNull(e, "HttpStatusCodeException must not be null");

		String message = VaultResponses.getError(e);

		if (StringUtils.hasText(message)) {
			return new VaultException(String.format("Status %s: %s", e.getStatusCode(),
					message), e);
		}

		return new VaultException(String.format("Status %s", e.getStatusCode()), e);
	}

	/**
	 * Build a {@link VaultException} given {@link HttpStatusCodeException} and request
	 * {@code path}.
	 * @param e must not be {@literal null}.
	 * @param path
	 * @return the {@link VaultException}.
	 */
	public static VaultException buildException(HttpStatusCodeException e, String path) {

		Assert.notNull(e, "HttpStatusCodeException must not be null");

		String message = VaultResponses.getError(e);

		if (StringUtils.hasText(message)) {
			return new VaultException(String.format("Status %s %s: %s",
					e.getStatusCode(), path, message), e);
		}

		return new VaultException(String.format("Status %s %s", e.getStatusCode(), path), e);
	}

	/**
	 * Create a {@link ParameterizedTypeReference} for {@code responseType}.
	 * @param responseType must not be {@literal null}.
	 * @return the {@link ParameterizedTypeReference} for {@code responseType}.
	 */
	public static <T> ParameterizedTypeReference<VaultResponseSupport<T>> getTypeReference(
			final Class<T> responseType) {

		Assert.notNull(responseType, "Response type must not be null");

		final Type supportType = new ParameterizedType() {

			@Override
			public Type[] getActualTypeArguments() {
				return new Type[] { responseType };
			}

			@Override
			public Type getRawType() {
				return VaultResponseSupport.class;
			}

			@Override
			public Type getOwnerType() {
				return VaultResponseSupport.class;
			}
		};

		return new ParameterizedTypeReference<VaultResponseSupport<T>>() {
			@Override
			public Type getType() {
				return supportType;
			}
		};
	}

	/**
	 * Obtain the error message from a JSON response.
	 *
	 * @param e must not be {@literal null}.
	 * @return
	 */
	public static String getError(@NotNull HttpStatusCodeException e) {
		String body = e.getResponseBodyAsString();
		MediaType contentType;
		try {
			HttpHeaders headers = e.getResponseHeaders();
			contentType = headers != null ? headers.getContentType() : null;
		} catch (Exception ignored) {
			return body;
		}

		if (MediaType.APPLICATION_JSON.includes(contentType)) {
			try {
				Map<String, Object> map = OBJECT_MAPPER.readValue(body.getBytes(),
						new TypeReference<Map<String, Object>>() {
						});
				if (map.containsKey("errors")) {
					//noinspection unchecked
					Collection<String> errors = (Collection<String>) map.get("errors");
					if (errors.size() == 1) {
						return errors.iterator().next();
					}
					return errors.toString();
				}
			} catch (IOException ignored) {
				// ignore
			}
		}
		return body;
	}

	/**
	 * Unwrap a wrapped response created by Vault Response Wrapping
	 *
	 * @param wrappedResponse the wrapped response , must not be empty or {@literal null}.
	 * @param responseType the type of the return value.
	 * @return the unwrapped response.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T unwrap(final String wrappedResponse, Class<T> responseType) {

		Assert.hasText(wrappedResponse, "Wrapped response must not be empty");

		try {
			return (T) converter.read(responseType, new HttpInputMessage() {
				@Override
				public InputStream getBody() throws IOException {
					return new ByteArrayInputStream(wrappedResponse.getBytes());
				}

				@Override
				public HttpHeaders getHeaders() {
					return new HttpHeaders();
				}
			});
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
