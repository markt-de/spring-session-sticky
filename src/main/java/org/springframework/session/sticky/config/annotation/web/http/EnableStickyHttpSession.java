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
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.sticky.StickySessionCache;
import org.springframework.session.sticky.StickySessionRepository;
import org.springframework.session.sticky.StickySessionRepositoryAdapter;

/**
 * Add this annotation to an {@code @Configuration} class to create a {@link StickySessionRepository}
 * bean. It is marked as @Primary and can thus be combined with other configurations
 * like {@link EnableRedisHttpSession}.
 *
 * In order to leverage the annotation, a single {@link StickySessionRepositoryAdapter} must
 * be provided. For example:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableRedisHttpSession
 * &#064;EnableStickyHttpSession
 * public class StickyRedisHttpSessionConfig {
 *
 *     &#064;Bean
 *     public StickyRedisSessionRepositoryAdapter stickyRedisSessionRepositoryAdapter() {
 *         return new StickyRedisSessionRepositoryAdapter();
 *     }
 *
 * }
 * </pre>
 *
 *
 * More advanced configurations can extend {@link StickyHttpSessionConfiguration} instead.
 *
 * @author Rob Winch
 * @author Vedran Pavic
 * @since 1.0
 * @see EnableSpringHttpSession
 * @see EnableStickyRedisHttpSession
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(StickyHttpSessionConfiguration.class)
@Configuration(proxyBeanMethods = false)
public @interface EnableStickyHttpSession {

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
	int revalidateAfterSeconds() default StickySessionRepository.DEFAULT_REVALIDATE_AFTER_SECONDS;

	/**
	 * Cached session entries that have not been accessed for this number of minutes will
	 * be removed from the cache (but not the remote store).
	 *
	 * @return the number of minutes after which a session should become oudated if it has not been accessed
	 */
	int cleanupAfterMinutes() default StickySessionCache.DEFAULT_CLEANUP_AFTER_MINUTES;

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
	int asyncSaveThreads() default -1;

	/**
	 * Flush mode for the cached sessions. The default is {@code ON_SAVE} which only
	 * updates the backing Redis when {@link SessionRepository#save(Session)} is invoked.
	 * In a web environment this happens just before the HTTP response is committed.
	 * <p>
	 * Setting the value to {@code IMMEDIATE} will ensure that the any updates to the
	 * Session are immediately written to the Redis instance.
	 * @return the {@link FlushMode} to use
	 */
	FlushMode flushMode() default FlushMode.ON_SAVE;

	/**
	 * The cron expression for outdated cache entry cleanup job. By default runs every minute.
	 * @return the cache cleanup cron expression
	 */
	String cacheCleanupCron() default StickyHttpSessionConfiguration.DEFAULT_CACHE_CLEANUP_CRON;

	/**
	 * Save mode for the cached session. The default is {@link SaveMode#ON_SET_ATTRIBUTE}, which
	 * only saves changes made to session.
	 * @return the save mode
	 */
	SaveMode saveMode() default SaveMode.ON_SET_ATTRIBUTE;

}
