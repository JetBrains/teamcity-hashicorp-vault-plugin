/*
 * Copyright 2002-2015 the original author or authors.
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A builder used to create {@link ObjectMapper} instances with a fluent API.
 *
 * <p>It customizes Jackson's default properties with the following ones:
 * <ul>
 * <li>{@link MapperFeature#DEFAULT_VIEW_INCLUSION} is disabled</li>
 * <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} is disabled</li>
 * </ul>
 *
 * <p>It also automatically registers the following well-known modules if they are
 * detected on the classpath:
 * <ul>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-jdk7">jackson-datatype-jdk7</a>: support for Java 7 types like {@link java.nio.file.Path}</li>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-jdk8">jackson-datatype-jdk8</a>: support for other Java 8 types like {@link java.util.Optional}</li>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-jsr310">jackson-datatype-jsr310</a>: support for Java 8 Date & Time API types</li>
 * <li><a href="https://github.com/FasterXML/jackson-datatype-joda">jackson-datatype-joda</a>: support for Joda-Time types</li>
 * </ul>
 *
 * <p>Tested against Jackson 2.4, 2.5, 2.6; compatible with Jackson 2.0 and higher.
 *
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @author Tadaya Tsuyukubo
 * @since 4.1.1
 * @see #build()
 * @see #configure(ObjectMapper)
 * @see Jackson2ObjectMapperFactoryBean
 */
public class Jackson2ObjectMapperBuilder {

	private ClassLoader moduleClassLoader = getClass().getClassLoader();


	/**
	 * Build a new {@link ObjectMapper} instance.
	 * <p>Each build operation produces an independent {@link ObjectMapper} instance.
	 * The builder's settings can get modified, with a subsequent build operation
	 * then producing a new {@link ObjectMapper} based on the most recent settings.
	 * @return the newly built ObjectMapper
	 */
	@SuppressWarnings("unchecked")
	public <T extends ObjectMapper> T build() {
		ObjectMapper mapper;
		mapper = new ObjectMapper();
		configure(mapper);
		return (T) mapper;
	}

	/**
	 * Configure an existing {@link ObjectMapper} instance with this builder's
	 * settings. This can be applied to any number of {@code ObjectMappers}.
	 * @param objectMapper the ObjectMapper to configure
	 */
	@SuppressWarnings("deprecation")
	public void configure(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");

		registerWellKnownModulesIfAvailable(objectMapper);


		customizeDefaultFeatures(objectMapper);

	}


	// Any change to this method should be also applied to spring-jms and spring-messaging
	// MappingJackson2MessageConverter default constructors
	private void customizeDefaultFeatures(ObjectMapper objectMapper) {
		configureFeature(objectMapper, MapperFeature.DEFAULT_VIEW_INCLUSION, false);
		configureFeature(objectMapper, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	private void configureFeature(ObjectMapper objectMapper, Object feature, boolean enabled) {
		if (feature instanceof JsonParser.Feature) {
			objectMapper.configure((JsonParser.Feature) feature, enabled);
		}
		else if (feature instanceof JsonGenerator.Feature) {
			objectMapper.configure((JsonGenerator.Feature) feature, enabled);
		}
		else if (feature instanceof SerializationFeature) {
			objectMapper.configure((SerializationFeature) feature, enabled);
		}
		else if (feature instanceof DeserializationFeature) {
			objectMapper.configure((DeserializationFeature) feature, enabled);
		}
		else if (feature instanceof MapperFeature) {
			objectMapper.configure((MapperFeature) feature, enabled);
		}
		else {
			throw new FatalBeanException("Unknown feature class: " + feature.getClass().getName());
		}
	}

	@SuppressWarnings("unchecked")
	private void registerWellKnownModulesIfAvailable(ObjectMapper objectMapper) {
		// Java 7 java.nio.file.Path class present?
		if (ClassUtils.isPresent("java.nio.file.Path", this.moduleClassLoader)) {
			try {
				Class<? extends Module> jdk7Module = (Class<? extends Module>)
						ClassUtils.forName("com.fasterxml.jackson.datatype.jdk7.Jdk7Module", this.moduleClassLoader);
				objectMapper.registerModule(BeanUtils.instantiate(jdk7Module));
			}
			catch (ClassNotFoundException ignored) {
				// jackson-datatype-jdk7 not available
			}
		}

		// Java 8 java.util.Optional class present?
		if (ClassUtils.isPresent("java.util.Optional", this.moduleClassLoader)) {
			try {
				Class<? extends Module> jdk8Module = (Class<? extends Module>)
						ClassUtils.forName("com.fasterxml.jackson.datatype.jdk8.Jdk8Module", this.moduleClassLoader);
				objectMapper.registerModule(BeanUtils.instantiate(jdk8Module));
			}
			catch (ClassNotFoundException ignored) {
				// jackson-datatype-jdk8 not available
			}
		}

		// Java 8 java.time package present?
		if (ClassUtils.isPresent("java.time.LocalDate", this.moduleClassLoader)) {
			try {
				Class<? extends Module> javaTimeModule = (Class<? extends Module>)
						ClassUtils.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", this.moduleClassLoader);
				objectMapper.registerModule(BeanUtils.instantiate(javaTimeModule));
			}
			catch (ClassNotFoundException ignored) {
				// jackson-datatype-jsr310 not available or older than 2.6
				try {
					Class<? extends Module> jsr310Module = (Class<? extends Module>)
							ClassUtils.forName("com.fasterxml.jackson.datatype.jsr310.JSR310Module", this.moduleClassLoader);
					objectMapper.registerModule(BeanUtils.instantiate(jsr310Module));
				}
				catch (ClassNotFoundException ignored2) {
					// OK, jackson-datatype-jsr310 not available at all...
				}
			}
		}

		// Joda-Time present?
		if (ClassUtils.isPresent("org.joda.time.LocalDate", this.moduleClassLoader)) {
			try {
				Class<? extends Module> jodaModule = (Class<? extends Module>)
						ClassUtils.forName("com.fasterxml.jackson.datatype.joda.JodaModule", this.moduleClassLoader);
				objectMapper.registerModule(BeanUtils.instantiate(jodaModule));
			}
			catch (ClassNotFoundException ignored) {
				// jackson-datatype-joda not available
			}
		}
	}


	// Convenience factory methods

	/**
	 * Obtain a {@link Jackson2ObjectMapperBuilder} instance in order to
	 * build a regular JSON {@link ObjectMapper} instance.
	 */
	public static Jackson2ObjectMapperBuilder json() {
		return new Jackson2ObjectMapperBuilder();
	}

}
