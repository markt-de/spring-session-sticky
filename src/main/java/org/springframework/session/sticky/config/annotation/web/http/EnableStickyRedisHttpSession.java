/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.session.sticky.config.annotation.web.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.sticky.StickySessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * {@link SessionRepositoryFilter} as a bean named {@code springSessionRepositoryFilter}
 * and backed by a sticky cache connected to a Redis repository.
 * In order to leverage the annotation, a single {@link RedisConnectionFactory} must
 * be provided. For example:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableStickyRedisHttpSession
 * public class StickyRedisHttpSessionConfig {
 *
 *     &#064;Bean
 *     public LettuceConnectionFactory redisConnectionFactory() {
 *         return new LettuceConnectionFactory();
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link StickyHttpSessionConfiguration} instead.
 *
 * @author Bernhard Frauendienst
 * @since 1.0
 * @see EnableRedisHttpSession
 * @see EnableStickyHttpSession
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(StickyRedisHttpSessionConfiguration.class)
@Configuration(proxyBeanMethods = false)
@EnableRedisHttpSession
@EnableStickyHttpSession
public @interface EnableStickyRedisHttpSession {

	/**
	 * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 * @return the seconds a session can be inactive before expiring
	 */
	@AliasFor(annotation = EnableRedisHttpSession.class)
	int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

	/**
	 * If set to a non-zero value, the {@link Session#getLastAccessedTime() lastAccessedTime} will be fetched from the
	 * remote repository and compared to the cached value if the cached session is older than
	 * {@code revalidateAfterSeconds} seconds.
	 * <p>
	 * Set to {@code 0} to revalidate on every request.
	 * <p>
	 * Set to {@code -1} to disable revalidation (then the local copy will always be used if it exists).
	 * @return the seconds after which a session's freshness will be revalidated with the remote repository
	 */
	@AliasFor(annotation = EnableStickyHttpSession.class)
	int revalidateAfterSeconds() default StickySessionRepository.DEFAULT_REVALIDATE_AFTER_SECONDS;

	/**
	 * Cached session entries that have not been accessed for this number of minutes will
	 * be removed from the cache (but not the remote store).
	 *
	 * @return the number of minutes after which a session should become oudated if it has not been accessed
	 */
	@AliasFor(annotation = EnableStickyHttpSession.class)
	int cleanupAfterMinutes() default StickySessionRepository.DEFAULT_CLEANUP_AFTER_MINUTES;

	/**
	 * If set to a positive value, a {@linkplain java.util.concurrent.Executors#newFixedThreadPool(int) fixed thread pool}
	 * of that size will be configured for
	 * {@linkplain StickySessionRepository#setAsyncSaveExecutor(Executor) asynchronous saving.
	 *
	 * If set to zero, no executor will be configured (sessions will be saved to the remote store synchronously).
	 *
	 * By default (or when set to a negativ value), a default executor of 16 threads will be configured.
	 * @return
	 */
	@AliasFor(annotation = EnableStickyHttpSession.class)
	int asyncSaveThreads() default -1;

	/**
	 * Defines a unique namespace for keys. The value is used to isolate sessions by
	 * changing the prefix from default {@code spring:session:} to
	 * {@code <redisNamespace>:}.
	 * <p>
	 * For example, if you had an application named "Application A" that needed to keep
	 * the sessions isolated from "Application B" you could set two different values for
	 * the applications and they could function within the same Redis instance.
	 * @return the unique namespace for keys
	 */
	@AliasFor(annotation = EnableRedisHttpSession.class)
	String redisNamespace() default RedisIndexedSessionRepository.DEFAULT_NAMESPACE;

	/**
	 * Flush mode for the Redis sessions. The default is {@code ON_SAVE} which only
	 * updates the backing Redis when {@link SessionRepository#save(Session)} is invoked.
	 * In a web environment this happens just before the HTTP response is committed.
	 * <p>
	 * Setting the value to {@code IMMEDIATE} will ensure that the any updates to the
	 * Session are immediately written to the Redis instance.
	 * @return the {@link FlushMode} to use
	 */
	@AliasFor(annotation = EnableRedisHttpSession.class, attribute = "flushMode")
	FlushMode redisSessionFlushMode() default FlushMode.ON_SAVE;

	/**
	 * Flush mode for the cached sessions. The default is {@code ON_SAVE} which only
	 * updates the backing Redis when {@link SessionRepository#save(Session)} is invoked.
	 * In a web environment this happens just before the HTTP response is committed.
	 * <p>
	 * Setting the value to {@code IMMEDIATE} will ensure that the any updates to the
	 * Session are immediately written to the Redis instance.
	 * @return the {@link FlushMode} to use
	 */
	@AliasFor(annotation = EnableStickyHttpSession.class, attribute = "flushMode")
	FlushMode stickySessionFlushMode() default FlushMode.ON_SAVE;

	/**
	 * The cron expression for expired session cleanup job. By default runs every minute.
	 * @return the session cleanup cron expression
	 */
	@AliasFor(annotation = EnableRedisHttpSession.class)
	String cleanupCron() default "";

	/**
	 * The cron expression for outdated cache entry cleanup job. By default runs every 5 minutes.
	 * @return the cache cleanup cron expression
	 */
	@AliasFor(annotation = EnableStickyHttpSession.class)
	String cacheCleanupCron() default StickyHttpSessionConfiguration.DEFAULT_CACHE_CLEANUP_CRON;

	/**
	 * Save mode for the redis session. The default is {@link SaveMode#ON_SET_ATTRIBUTE}, which
	 * only saves changes made to session.
	 * @return the save mode
	 */
	@AliasFor(annotation = EnableRedisHttpSession.class, attribute = "saveMode")
	SaveMode redisSessionSaveMode() default SaveMode.ON_SET_ATTRIBUTE;

	/**
	 * Save mode for the cached session. The default is {@link SaveMode#ON_SET_ATTRIBUTE}, which
	 * only saves changes made to session.
	 * @return the save mode
	 */
	@AliasFor(annotation = EnableStickyHttpSession.class, attribute = "saveMode")
	SaveMode stickySessionSaveMode() default SaveMode.ON_SET_ATTRIBUTE;

}
