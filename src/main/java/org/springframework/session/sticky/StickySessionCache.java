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

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.session.sticky.StickySessionRepository.CacheEntry;
import org.springframework.util.Assert;

/**
 * @author Bernhard Frauendienst
 */
public class StickySessionCache {

  private static final Log logger = LogFactory.getLog(StickySessionRepository.class);

  public static final int DEFAULT_CLEANUP_AFTER_MINUTES = 20;

  protected final Map<String, CacheEntry> sessions;

  private final CacheCleanup<CacheEntry> cacheCleanup = new CacheCleanup<>();

  protected Duration cleanupAfter = Duration.ofMinutes(DEFAULT_CLEANUP_AFTER_MINUTES);

  public StickySessionCache(int cacheConcurrency) {
    this.sessions = new ConcurrentHashMap<>(16, 0.75F, cacheConcurrency);
  }

  /**
   * Cached session entries that have not been accessed for {@linkplain #cleanupAfter the configured period} will
   * be removed from the cache (but not the remote store) when {@link #cleanupOutdatedCacheEntries()} is called.
   *
   * NOTE: changing this value to a shorter period than before can cause some sessions to be not considered
   * for removal until the period that was configured when they were created has passed.
   *
   * @param cleanupAfter the period after which unaccessed session become considered outdated
   */
  public void setCleanupAfter(Duration cleanupAfter) {
    Assert.notNull(cleanupAfter, "cleanupAfter cannot be null");
    this.cleanupAfter = cleanupAfter;
  }

  @Nullable
  public CacheEntry get(String id) {
    return sessions.get(id);
  }

  public void put(CacheEntry entry) {
    sessions.put(entry.getId(), entry);
    cacheCleanup.schedule(entry);
  }

  @Nullable
  public void remove(String id) {
    sessions.remove(id);
    // We don't remove from cleanup cache here, because that would require a separate mapping of session ids.
    // The weak reference will be cleared, and the cleanup entry will simply be skipped when it's due.
  }

  /**
   * Removes all sessions from the cache that have not been accessed for {@link #cleanupAfter}.
   *
   * This does not delete sessions, it just removes them from the local cache.
   */
  public void cleanupOutdatedCacheEntries() {
    cacheCleanup.cleanup();
  }

  private static class CleanupEntry<E extends CacheEntry> implements Comparable<CleanupEntry<E>> {
    final WeakReference<E> session;

    final Instant scheduledCleanup;

    final String sessionId;

    public CleanupEntry(E session, Instant scheduledCleanup) {
      this.session = new WeakReference<>(session);
      this.sessionId = session.getId();
      this.scheduledCleanup = scheduledCleanup;
    }

    @Override
    public int compareTo(CleanupEntry<E> o) {
      int cmp = this.scheduledCleanup.compareTo(o.scheduledCleanup);
      return cmp != 0 ? cmp : this.sessionId.compareTo(o.sessionId);
    }
  }

  private class CacheCleanup<E extends CacheEntry> {

    private final SortedSet<CleanupEntry<E>> scheduledEntries = new ConcurrentSkipListSet<>();

    void schedule(E entry) {
      CleanupEntry<E> cleanupEntry = new CleanupEntry<>(entry, entry.getLastAccessedTime().plus(cleanupAfter));
      if (logger.isTraceEnabled())
        logger.trace("Scheduling cleanup for session " + entry.getId() + " @ " + cleanupEntry.scheduledCleanup);
      scheduledEntries.add(cleanupEntry);
    }

    private void remove(CleanupEntry<E> entry) {
      scheduledEntries.remove(entry);
    }

    void cleanup() {
      Instant now = Instant.now();
      Instant maxLastAccessed = now.minus(cleanupAfter);
      for (CleanupEntry<E> entry : scheduledEntries) {
        if (entry.scheduledCleanup.isAfter(now)) {
          // this and all further entries are not due yet
          break;
        }

        remove(entry);
        E session = entry.session.get();
        if (session == null) {
          continue;
        }
        if (session.getLastAccessedTime().isBefore(maxLastAccessed)) {
          if (logger.isDebugEnabled())
            logger.debug("Cached session " + session.getId() + " is scheduled for cleanup, removing from cache.");
          sessions.remove(session.getId());
        } else {
          schedule(session);
        }
      }
    }
  }

}
