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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;

/**
 * {@link SessionRepository} implementation that delegates to a (usually remote) session repository, but keeps
 * a local copy of the session in the configured cache.
 *
 * Supports emitting session events IFF the configured delegate repository emits such events. The local cache will only
 * be cleaned up when the delegate repository emits {@link SessionDestroyedEvent}s.
 *
 * <p>
 * If configured to {@linkplain #setRevalidateAfter(Duration) revalidate sessions}, the lastAccessTime of the local
 * session's version will be compared against the remote version, and refreshed if stale.
 * <p>
 * If configured to {@linkplain #setAsyncSaveExecutor(Executor) save session asynchronously}, saving of the delegate
 * session will be dispatched to the configured executor.
 *
 *
 * @author Bernhard Frauendienst
 */
public final class StickySessionRepository<S extends Session>
    implements SessionRepository<StickySessionRepository<S>.StickySession> {

  private static final Log logger = LogFactory.getLog(StickySessionRepository.class);

  private final SessionRepository<S> delegate;

  private final LastAccessedTimeAccessor lastAccessedTimeAccessor;

  private final Map<String, StickySession> sessionCache;

  private ApplicationEventPublisher eventPublisher = event -> {
  };

  private Executor asyncSaveExecutor = null;

  private Duration revalidateAfter = null;

  public <R extends SessionRepository<S>> StickySessionRepository(StickySessionRepositoryAdapter<R> repositoryAdapter,
      Map<String, StickySession> sessionCache) {
    this.delegate = repositoryAdapter.getSessionRepository();
    this.sessionCache = sessionCache;

    if (delegate instanceof LastAccessedTimeAccessor) {
      this.lastAccessedTimeAccessor = (LastAccessedTimeAccessor) delegate;
    } else if (repositoryAdapter instanceof LastAccessedTimeAccessor) {
      this.lastAccessedTimeAccessor = (LastAccessedTimeAccessor) repositoryAdapter;
    } else {
      this.lastAccessedTimeAccessor = null;
    }

    repositoryAdapter.setApplicationEventPublisher(new EventPublisher());
  }

  public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * If set to a non-null value, sessions will be saved to the remote repository in an asynchronous fashion using
   * the given executor.
   *
   * @param asyncSaveExecutor the executor to save delegate sessions with, or {@code null} to disable async saving.
   */
  public void setAsyncSaveExecutor(Executor asyncSaveExecutor) {
    this.asyncSaveExecutor = asyncSaveExecutor;
  }

  /**
   * If set to a non-null value, the {@link Session#getLastAccessedTime() lastAccessedTime} will be fetched from the
   * remote repository and compared to the cached value if the cached session is older than {@code revalidateAfter}.
   *
   * Set to {@code null} to disable revalidation (then the local copy will always be used if it exists).
   *
   * @param revalidateAfter
   */
  public void setRevalidateAfter(Duration revalidateAfter) {
    this.revalidateAfter = revalidateAfter;
  }

  @Override public StickySession createSession() {
    S delegate = this.delegate.createSession();
    return new StickySession(delegate);
  }

  @Override public void save(StickySession session) {
    sessionCache.put(session.getId(), session);
    session.delegateAwaitsSave = true;
    if (asyncSaveExecutor != null) {
      asyncSaveExecutor.execute(session::saveDelegate);
    } else {
      session.saveDelegate();
    }
  }

  @Override public StickySession findById(String id) {
    StickySession cached = sessionCache.get(id);
    if (cached == null || cached.isExpired()) {
      if (cached != null) {
        logger.trace("Removing expired session from cache.");
        sessionCache.remove(id);
      }
      S delegate = this.delegate.findById(id);
      if (delegate == null) {
        return null;
      }
      return new StickySession(delegate);
    }

    // re-validate if not accessed within the configured period
    if (revalidateAfter != null) {
      if (cached.getLastAccessedTime().isBefore(Instant.now().minus(revalidateAfter))) {
        logger.trace("Revalidating session against delegate repository.");
        S delegate = null;
        final Instant lastAccessedTime;
        if (lastAccessedTimeAccessor != null) {
          // if we can get the lastAccessedTime without loading the session, let's try to be efficient
          lastAccessedTime = lastAccessedTimeAccessor.getLastAccessedTime(id);
        } else {
          delegate = this.delegate.findById(id);
          lastAccessedTime = delegate != null ? delegate.getLastAccessedTime() : null;
        }

        // if the delegate repository does not know this session because we have not yet saved it, don't remove it
        if (lastAccessedTime == null && !cached.delegateAwaitsSave) {
          logger.trace("Delegate session is unknown, removing from cache.");
          sessionCache.remove(id);
          return null;
        }

        if (delegate != null && delegate.isExpired()) {
          logger.trace("Delegate session is expired, removing from cache.");
          sessionCache.remove(id);
          return null;
        }

        // if the delegate session is newer than our cache, we need to evict it
        if (lastAccessedTime != null && lastAccessedTime.isAfter(cached.getLastAccessedTime())) {
          logger.trace("Cached session is outdated, removing from cache.");
          sessionCache.remove(id);
          if (delegate == null) {
            delegate = this.delegate.findById(id);
          }
          if (delegate == null) {
            return null;
          }
          return new StickySession(delegate);
        }
      }
    }

    return cached;
  }

  @Override public void deleteById(String id) {
    sessionCache.remove(id);
    delegate.deleteById(id);
  }

  public final class StickySession implements Session {

    private final S delegate;

    private final MapSession cached;

    private boolean delegateAwaitsSave = false;

    public StickySession(S delegate) {
      this(delegate, new MapSession(delegate));
    }

    public StickySession(S delegate, MapSession cached) {
      this.delegate = delegate;
      this.cached = cached;
    }

    private synchronized void saveDelegate() {
      if (logger.isDebugEnabled())
        logger.debug("Saving delegate session " + getId());
      StickySessionRepository.this.delegate.save(delegate);
      delegateAwaitsSave = false;
    }

    @Override public String getId() {
      return delegate.getId();
    }

    @Override public synchronized String changeSessionId() {
      String sessionId = delegate.changeSessionId();
      cached.setId(sessionId);
      return sessionId;
    }

    @Override public <T> T getAttribute(String attributeName) {
      return cached.getAttribute(attributeName);
    }

    @Override public Set<String> getAttributeNames() {
      return cached.getAttributeNames();
    }

    @Override public synchronized void setAttribute(String attributeName, Object attributeValue) {
      cached.setAttribute(attributeName, attributeValue);
      delegate.setAttribute(attributeName, attributeValue);
    }

    @Override public synchronized  void removeAttribute(String attributeName) {
      cached.removeAttribute(attributeName);
      delegate.removeAttribute(attributeName);
    }

    @Override public Instant getCreationTime() {
      return cached.getCreationTime();
    }

    @Override public Instant getLastAccessedTime() {
      return cached.getLastAccessedTime();
    }

    @Override public synchronized void setLastAccessedTime(Instant lastAccessedTime) {
      cached.setLastAccessedTime(lastAccessedTime);
      delegate.setLastAccessedTime(lastAccessedTime);
    }

    @Override public Duration getMaxInactiveInterval() {
      return cached.getMaxInactiveInterval();
    }

    @Override public synchronized void setMaxInactiveInterval(Duration interval) {
      cached.setMaxInactiveInterval(interval);
      delegate.setMaxInactiveInterval(interval);
    }

    @Override public boolean isExpired() {
      return delegate.isExpired();
    }
  }

  private class EventPublisher implements ApplicationEventPublisher {
    @Override public void publishEvent(ApplicationEvent event) {
      if (event instanceof AbstractSessionEvent) {
        publishEvent((AbstractSessionEvent) event);
      }
    }

    @Override public void publishEvent(Object event) {
      if (event instanceof AbstractSessionEvent) {
        publishEvent((AbstractSessionEvent) event);
      }
    }

    private void publishEvent(AbstractSessionEvent event) {
      if (event.getSource() != delegate) {
        logger.warn("Will not publish " + event.getClass().getSimpleName() + " not originating from " + delegate);
        return;
      }

      Session delegateSession = event.getSession();
      if (delegateSession == null) {
        // AbstractSessionEvent javadocs claims this can happen. AFAICT, the source code says otherwise.
        logger.warn("Cannot publish " + event.getClass().getSimpleName() + " for session " + event.getSessionId()
            + ", no cached session found.");
        return;
      }
      StickySession cached = sessionCache.get(event.getSessionId());
      Session session = cached != null ? cached : delegateSession;
      if (event instanceof SessionCreatedEvent) {
        eventPublisher.publishEvent(new SessionCreatedEvent(StickySessionRepository.this, session));
      } else if (event instanceof SessionDestroyedEvent) {
        if (event instanceof SessionDeletedEvent) {
          eventPublisher.publishEvent(new SessionDeletedEvent(StickySessionRepository.this, session));
        } else if (event instanceof SessionExpiredEvent) {
          eventPublisher.publishEvent(new SessionExpiredEvent(StickySessionRepository.this, session));
        }
        sessionCache.remove(event.getSessionId());
      } else {
        logger.warn("Unknown event type " + event.getClass());
      }
    }
  }
}
