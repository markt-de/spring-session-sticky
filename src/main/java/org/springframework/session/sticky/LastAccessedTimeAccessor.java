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
package org.springframework.session.sticky;

import java.time.Instant;

import org.springframework.lang.Nullable;
import org.springframework.session.Session;

/**
 * Extension interface for {@link StickySessionRepositoryAdapter} implementations that can provide access to a session's
 * lastAccessedTime attribute more efficiently than loading the whole session.
 *
 * @author Bernhard Frauendienst
 */
public interface LastAccessedTimeAccessor {

  /**
   * Returns the {@link Session#getLastAccessedTime()} of the session with the given sessionId
   *
   * @param sessionId
   * @return the lastAccessedTime if the session exists, {@code null} otherwise
   */
  @Nullable Instant getLastAccessedTime(String sessionId);
}
