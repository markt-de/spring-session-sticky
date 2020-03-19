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

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.SessionRepository;

/**
 * @author Bernhard Frauendienst
 */
public interface StickySessionRepositoryAdapter<S extends SessionRepository<?>> {

  /**
   * Returns the delegate repository
   * @return
   */
  S getSessionRepository();

  /**
   * Sets an {@link ApplicationEventPublisher} on the delegate repository. The delegate repository must emit
   * {@link org.springframework.session.events.SessionDestroyedEvent}s for the local cache to be properly cleaned.
   *
   * The supplied eventPublisher will re-emit the events coming from the sticky repository.
   * @param eventPublisher
   */
  void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher);

}
