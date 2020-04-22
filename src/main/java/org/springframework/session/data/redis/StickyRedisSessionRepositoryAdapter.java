/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.session.data.redis;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.sticky.LastAccessedTimeAccessor;
import org.springframework.session.sticky.StickySessionRepositoryAdapter;

/**
 * {@link StickySessionRepositoryAdapter} for {@link RedisIndexedSessionRepository}, that also provides efficient
 * access to the a session's {@link Session#getLastAccessedTime() lastAccessedTime} by directly accessing the
 * hash member that stores this attribute.
 *
 * This class accesses package-private methods of {@link RedisIndexedSessionRepository}.
 * This should obviously be solved differently when integrated into spring-session upstream.
 *
 * @author Bernhard Frauendienst
 */
public class StickyRedisSessionRepositoryAdapter
    implements StickySessionRepositoryAdapter<RedisIndexedSessionRepository>, LastAccessedTimeAccessor {
  private final RedisIndexedSessionRepository repository;

  public StickyRedisSessionRepositoryAdapter(RedisIndexedSessionRepository repository) {
    this.repository = repository;
  }

  @Override public RedisIndexedSessionRepository getSessionRepository() {
    return repository;
  }

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
    repository.setApplicationEventPublisher(eventPublisher);
  }

  @Override
  public @Nullable Instant getLastAccessedTime(String sessionId) {
    String sessionKey = repository.getSessionKey(sessionId);
    BoundHashOperations<Object, Object, Object> hashOps = repository.getSessionRedisOperations().boundHashOps(sessionKey);
    Long lastAccessedTime = (Long) hashOps.get(RedisSessionMapper.LAST_ACCESSED_TIME_KEY);
    if (lastAccessedTime == null) {
      return null;
    }
    return Instant.ofEpochMilli(lastAccessedTime);
  }
}
