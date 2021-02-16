/*
 * Copyright 2002-2016 the original author or authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

/**
 * Implementation of {@link org.springframework.http.converter.HttpMessageConverter} that can read and
 * write JSON using <a href="https://github.com/FasterXML/jackson-docs/wiki/JacksonHome">Jackson 2.x's</a> {@link ObjectMapper}.
 *
 * <p>This converter can be used to bind to typed beans, or untyped {@code HashMap} instances.
 *
 * <p>By default, this converter supports {@code application/json} and {@code application/*+json}
 * with {@code UTF-8} character set. This can be overridden by setting the
 * {@link #setSupportedMediaTypes supportedMediaTypes} property.
 *
 * <p>The default constructor uses the default configuration provided by {@link Jackson2ObjectMapperBuilder}.
 *
 * <p>Compatible with Jackson 2.1 to 2.6.
 *
 * @author Arjen Poutsma
 * @author Keith Donald
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 3.1.2
 */
public class MappingJackson2HttpMessageConverter extends AbstractJackson2HttpMessageConverter {

	/**
	 * Construct a new {@link MappingJackson2HttpMessageConverter} using default configuration
	 * provided by {@link Jackson2ObjectMapperBuilder}.
	 */
	public MappingJackson2HttpMessageConverter() {
		this(Jackson2ObjectMapperBuilder.json().build());
	}

	/**
	 * Construct a new {@link MappingJackson2HttpMessageConverter} with a custom {@link ObjectMapper}.
	 * You can use {@link Jackson2ObjectMapperBuilder} to build it easily.
	 * @see Jackson2ObjectMapperBuilder#json()
	 */
	public MappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
		super(objectMapper, MediaType.APPLICATION_JSON_UTF8,
				new MediaType("application", "*+json", DEFAULT_CHARSET));
	}

}
