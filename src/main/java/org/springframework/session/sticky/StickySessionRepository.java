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

import static java.util.Comparator.comparing;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.session.FlushMode;
import org.springframework.session.MapSession;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.sticky.internal.util.StringMonitorPool;
import org.springframework.util.Assert;

/**
 * {@link SessionRepository} implementation that delegates to a (usually remote) session repository, but keeps
 * a local copy of the session in the configured cache.
 * <p>
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
 * @author Bernhard Frauendienst
 */
public final class StickySessionRepository
    implements SessionRepository<StickySessionRepository.StickySession> {

  private static final Log logger = LogFactory.getLog(StickySessionRepository.class);

  public static final int DEFAULT_REVALIDATE_AFTER_SECONDS = 30;

  private final SessionRepository<?> delegate;

  private final LastAccessedTimeAccessor lastAccessedTimeAccessor;

  private final StickySessionCache sessionCache;

  private final StringMonitorPool monitors;

  private ApplicationEventPublisher eventPublisher = event -> {
  };

  private @Nullable Executor asyncSaveExecutor = null;

  private @Nullable Duration revalidateAfter = Duration.ofMinutes(DEFAULT_REVALIDATE_AFTER_SECONDS);

  private FlushMode flushMode = FlushMode.ON_SAVE;

  private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

  public StickySessionRepository(StickySessionRepositoryAdapter<? extends SessionRepository<?>> repositoryAdapter,
      StickySessionCache sessionCache, int concurrency) {
    this.delegate = repositoryAdapter.getSessionRepository();
    this.sessionCache = sessionCache;
    this.monitors = new StringMonitorPool(concurrency);

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
  public void setAsyncSaveExecutor(@Nullable Executor asyncSaveExecutor) {
    this.asyncSaveExecutor = asyncSaveExecutor;
  }

  /**
   * If set to a non-null value, the {@link Session#getLastAccessedTime() lastAccessedTime} will be fetched from the
   * remote repository and compared to the cached value if the cached session is older than {@code revalidateAfter}.
   * <p>
   * Set to {@code null} to disable revalidation (then the local copy will always be used if it exists).
   *
   * @param revalidateAfter duration after which to revalidate cached sessions, or {@code null} to disable revalidation
   */
  public void setRevalidateAfter(@Nullable Duration revalidateAfter) {
    this.revalidateAfter = revalidateAfter;
  }

  /**
   * Sets the flush mode. Default flush mode is {@link FlushMode#ON_SAVE}.
   *
   * @param flushMode the flush mode
   */
  public void setFlushMode(FlushMode flushMode) {
    Assert.notNull(flushMode, "flushMode cannot be null");
    this.flushMode = flushMode;
  }

  /**
   * Set the save mode.
   *
   * @param saveMode the save mode
   */
  public void setSaveMode(SaveMode saveMode) {
    Assert.notNull(saveMode, "saveMode must not be null");
    this.saveMode = saveMode;
  }

  private CacheEntry putCache(Session delegate) {
    if (logger.isTraceEnabled())
      logger.trace("Adding cache entry for session " + delegate.getId() + ".");
    CacheEntry entry = new CacheEntry(delegate);
    sessionCache.put(entry);
    return entry;
  }

  @Override public StickySession createSession() {
    Session delegate = this.delegate.createSession();
    return putCache(delegate).createView();
  }

  @Override public void save(StickySession session) {
    session.save();
  }

  @Override public @Nullable StickySession findById(String id) {
    CacheEntry cached = sessionCache.get(id);
    if (cached == null || cached.isExpired()) {
      synchronized (monitors.getMonitor(id)) {
        CacheEntry doubleChecked = sessionCache.get(id);
        if (doubleChecked != null) {
          if (doubleChecked.isExpired()) {
            if (logger.isTraceEnabled())
              logger.trace("Removing expired session " + id + " from cache.");
            sessionCache.remove(id);
          } else {
            return doubleChecked.createView();
          }
        }
        Session delegate = this.delegate.findById(id);
        if (delegate == null) {
          return null;
        }
        return putCache(delegate).createView();
      }
    }

    if (revalidateAfter == null || !cached.getLastAccessedTime().isBefore(Instant.now().minus(revalidateAfter))) {
      return cached.createView();
    }

    synchronized (monitors.getMonitor(id)) {
      // re-validate if not accessed within the configured period
      if (logger.isTraceEnabled())
        logger.trace("Revalidating session " + id + " against delegate repository.");

      Session delegate = null;
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
        if (logger.isTraceEnabled())
          logger.trace("Delegate session " + id + " is unknown, removing from cache.");
        sessionCache.remove(id);
        return null;
      }

      if (delegate != null && delegate.isExpired()) {
        if (logger.isTraceEnabled())
          logger.trace("Delegate session " + id + " is expired, removing from cache.");
        sessionCache.remove(id);
        return null;
      }

      if (lastAccessedTime == null || !lastAccessedTime.isAfter(cached.getLastAccessedTime())) {
        return cached.createView();
      }

      // if the delegate session is newer than our cache, we need to evict it
      if (logger.isDebugEnabled())
        logger.debug("Cached session " + id + " is newer on the remote, removing from cache.");
      sessionCache.remove(id);

      if (delegate == null) {
        delegate = this.delegate.findById(id);
      }
      if (delegate == null) {
        return null;
      }

      return putCache(delegate).createView();
    }
  }

  @Override public void deleteById(String id) {
    if (logger.isDebugEnabled())
      logger.debug("Deleting session " + id + ".");
    sessionCache.remove(id);
    delegate.deleteById(id);
  }

  /**
   * This class holds a {@link MapSession} entry as well as a matching {@linkplain Session delegate session} from the
   * remote repository.
   * Entries allow to create "view" sessions that will be saved back to this stored entry (by calling
   * {@link #saveDelta(Map, Instant, Duration, Session) saveDelta}). This should allows multiple threads to access
   * the same session entry without concurrency issues or unexpected race conditions.
   */
  public final class CacheEntry {
    private final MapSession cached;

    private Session delegate;

    private boolean delegateAwaitsSave = false;

    CacheEntry(Session delegate) {
      this.delegate = delegate;
      this.cached = new MapSession(delegate);
    }

    /**
     * Saves the given session changes to this cache entry.
     *
     * @param deltaAttributes     the attributes that have changed in the view
     * @param lastAccessedTime    the lastAccessedTime if it has changed in the view, {@code null} otherwise
     * @param maxInactiveInterval the maxInactiveInterval if it has changed in the view, {@code null} otherwise
     * @param changedIdDelegate   a new delegate if #changeSessionId was called on the view, {@code null} otherwise
     * @apiNote see {@link StickySession#changeSessionId()} for an explanation why switching delegates is necessary
     */
    private synchronized void saveDelta(Map<String, Object> deltaAttributes, @Nullable Instant lastAccessedTime,
        @Nullable Duration maxInactiveInterval, @Nullable Session changedIdDelegate) {
      String originalSessionId = getId();
      if (changedIdDelegate != null) {
        if (delegateAwaitsSave) {
          // if the delegate is going to be replaced, but the old one is not saved yet, we have to save it
          // right now so it does not write to the old session later.
          saveDelegate();
        }
        delegate = changedIdDelegate;
        cached.setId(changedIdDelegate.getId());
      }

      deltaAttributes.forEach((attributeName, attributeValue) -> {
        cached.setAttribute(attributeName, attributeValue);
        delegate.setAttribute(attributeName, attributeValue);
      });

      if (lastAccessedTime != null && lastAccessedTime.isAfter(getLastAccessedTime())) {
        cached.setLastAccessedTime(lastAccessedTime);
        delegate.setLastAccessedTime(lastAccessedTime);
      }

      if (maxInactiveInterval != null) {
        cached.setMaxInactiveInterval(maxInactiveInterval);
        delegate.setMaxInactiveInterval(maxInactiveInterval);
      }

      if (changedIdDelegate != null) {
        sessionCache.remove(originalSessionId);
        sessionCache.put(this);
      }

      delegateAwaitsSave = true;
      // if the session id changes, save to delegate session immediately
      if (asyncSaveExecutor != null && changedIdDelegate == null) {
        asyncSaveExecutor.execute(this::saveDelegate);
      } else {
        this.saveDelegate();
      }
    }

    private synchronized void saveDelegate() {
      if (logger.isDebugEnabled())
        logger.debug("Saving delegate session " + delegate.getId());
      @SuppressWarnings("unchecked") // if we don't do this here, we need to to it in a lot of other places
      SessionRepository<Session> delegateRepository = (SessionRepository<Session>) StickySessionRepository.this.delegate;
      delegateRepository.save(delegate);
      delegateAwaitsSave = false;
    }

    private synchronized StickySession createView() {
      if (logger.isTraceEnabled())
        logger.trace("Creating new session view for " + getId());
      return new StickySession(this, new MapSession(cached));
    }

    public String getId() {
      return cached.getId();
    }

    public boolean isExpired() {
      return cached.isExpired();
    }

    public Instant getLastAccessedTime() {
      return cached.getLastAccessedTime();
    }
  }

  /**
   * A custom implementation of {@link Session} that uses a {@link MapSession} as the
   * basis for its mapping. It keeps track of any attributes that have changed. When
   * {@link #save()} is invoked all the attributes that have been changed will be
   * persisted to the owning {@link CacheEntry}.
   */
  public final class StickySession implements Session {

    // keep a reference on our cache entry as long as this session object lives
    private final CacheEntry cacheEntry;

    private final MapSession cached;

    private Map<String, Object> delta = new HashMap<>();

    private Instant originalLastAccessTime;

    private Duration originalMaxInactiveInterval;

    private @Nullable Session changedIdDelegate;

    StickySession(CacheEntry cacheEntry, MapSession cached) {
      this.cacheEntry = cacheEntry;
      this.cached = cached;
      this.originalLastAccessTime = cached.getLastAccessedTime();
      this.originalMaxInactiveInterval = cached.getMaxInactiveInterval();
      if (StickySessionRepository.this.saveMode == SaveMode.ALWAYS) {
        markAllAttributes();
      }
    }

    @Override public boolean isExpired() {
      return this.cached.isExpired();
    }

    @Override public Instant getCreationTime() {
      return this.cached.getCreationTime();
    }

    @Override public String getId() {
      return this.cached.getId();
    }

    @Override public String changeSessionId() {
      // This one is a bit tricky: since we can't set the id on the delegate session, we must call the delegates
      // #changeSessionId. However, we don't want to persist that change until #save is called, so we must fetch a new
      // delegate and call #changeSessionId on that copy. When saving our StickySession, we will exchange the delegate
      // in the CacheEntry with our changed one.
      Session changedIdDelegate = StickySessionRepository.this.delegate.findById(getId());
      if (changedIdDelegate == null) {
        // This is strange, the remote repository does no longer know this session? Let's create a new one.
        logger.warn("Called changeSessionId on a session unknown to the remote repository (" + getId()
            + "). Will switch to a new session.");
        changedIdDelegate = StickySessionRepository.this.delegate.createSession();
        markAllAttributes();
      }
      this.changedIdDelegate = changedIdDelegate;

      String newSessionId = changedIdDelegate.changeSessionId();
      cached.setId(newSessionId);
      return newSessionId;
    }

    @Override public Instant getLastAccessedTime() {
      return this.cached.getLastAccessedTime();
    }

    @Override public void setLastAccessedTime(Instant lastAccessedTime) {
      this.cached.setLastAccessedTime(lastAccessedTime);
      flushImmediateIfNecessary();
    }

    @Override public Duration getMaxInactiveInterval() {
      return this.cached.getMaxInactiveInterval();
    }

    @Override public void setMaxInactiveInterval(Duration interval) {
      this.cached.setMaxInactiveInterval(interval);
      flushImmediateIfNecessary();
    }

    @Override @Nullable public <T> T getAttribute(String attributeName) {
      T attributeValue = this.cached.getAttribute(attributeName);
      if (attributeValue != null && saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
        this.delta.put(attributeName, attributeValue);
      }
      return attributeValue;
    }

    @Override public Set<String> getAttributeNames() {
      return this.cached.getAttributeNames();
    }

    @Override public void setAttribute(String attributeName, Object attributeValue) {
      this.cached.setAttribute(attributeName, attributeValue);
      this.delta.put(attributeName, attributeValue);
      flushImmediateIfNecessary();
    }

    @Override public void removeAttribute(String attributeName) {
      this.cached.removeAttribute(attributeName);
      this.delta.put(attributeName, null);
      flushImmediateIfNecessary();
    }

    private void flushImmediateIfNecessary() {
      if (StickySessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
        save();
      }
    }

    private void markAllAttributes() {
      getAttributeNames().forEach((attributeName) -> this.delta.put(attributeName, this.cached.getAttribute(attributeName)));
    }

    private void save() {
      final Map<String, Object> delta = this.delta;
      this.delta = new HashMap<>((int) (delta.size() / 0.75 + 1));

      Instant lastAccessedTime = getLastAccessedTime();
      if (lastAccessedTime.equals(originalLastAccessTime)) {
        lastAccessedTime = null;
      } else {
        this.originalLastAccessTime = lastAccessedTime;
      }

      Duration maxInactiveInterval = getMaxInactiveInterval();
      if (maxInactiveInterval.equals(originalMaxInactiveInterval)) {
        maxInactiveInterval = null;
      } else {
        originalMaxInactiveInterval = maxInactiveInterval;
      }

      Session newDelegate = this.changedIdDelegate;
      this.changedIdDelegate = null;

      cacheEntry.saveDelta(delta, lastAccessedTime, maxInactiveInterval, newDelegate);
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
        // AbstractSessionEvent javadoc claims this can happen. AFAICT, the source code says otherwise.
        logger.warn("Cannot publish " + event.getClass().getSimpleName() + " for session " + event.getSessionId()
            + ", no session found.");
        return;
      }
      CacheEntry cached = sessionCache.get(event.getSessionId());
      Session session = cached != null ? cached.createView() : delegateSession;
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
