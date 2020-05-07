/*
 * This document contains trade secret data which is the property of markt.de
 * GmbH & Co KG. Information contained herein may not be used, copied or
 * disclosed in whole or part except as permitted by written agreement from
 * markt.de GmbH & Co KG.
 *
 * Copyright (C) 2020 markt.de GmbH & Co KG / Munich / Germany
 */
package org.springframework.session.sticky.internal.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Util class that allows "synchronizing over strings" by returning
 * a monitor object, which is re-used as long as a strong reference
 * is pointing to it.
 *
 * @author Bernhard Frauendienst <bernhard.frauendienst@markt.de>
 */
public class StringMonitorPool {


  private ConcurrentHashMap<String, EntryReference<String, Object>> monitors;

  private ReferenceQueue<Object> queue = new ReferenceQueue<>();

  public StringMonitorPool(int concurrency) {
    this.monitors = new ConcurrentHashMap<>(16, 0.75f, concurrency);
  }

  /**
   * Retrieve a monitor for the given string. This monitor object
   * can be used for the equivalent of synchronizing over this string,
   * since this method guarantees to return the same object for the
   * same input string as long as the result is strongly referenced
   * (which it is as long as it is synchronized over).
   * @param s the string to synchronize over
   * @return a monitor suitable for synchronizing of the input string
   */
  public Object getMonitor(String s) {
    StrongReference strongRef = new StrongReference();
    Object result = monitors.compute(s, (key, ref) -> {
      if (ref != null) {
        Object monitor = ref.get();
        if (monitor != null) {
          strongRef.obj = monitor;
          return ref;
        }
      }
      Object monitor = new Object();
      strongRef.obj = monitor;
      return new EntryReference<>(s, monitor, queue);
    }).get();
    if (result == null) {
      throw new IllegalStateException("This cannot happen.");
    }
    drainReferenceQueue();
    return result;
  }

  private void drainReferenceQueue() {
    Reference<?> ref;
    while ((ref = queue.poll()) != null) {
      @SuppressWarnings("unchecked")
      final String key = ((EntryReference<String, ?>) ref).key;
      monitors.remove(key);
    }
  }

  private static class StrongReference {
    Object obj;
  }

  private static class EntryReference<K, T> extends SoftReference<T> {
    final K key;

    public EntryReference(K key, T value, ReferenceQueue<? super T> q) {
      super(value, q);
      this.key = key;
    }
  }
}
