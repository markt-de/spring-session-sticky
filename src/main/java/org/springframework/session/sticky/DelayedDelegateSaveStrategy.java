/*
 * This document contains trade secret data which is the property of markt.de
 * GmbH & Co KG. Information contained herein may not be used, copied or
 * disclosed in whole or part except as permitted by written agreement from
 * markt.de GmbH & Co KG.
 *
 * Copyright (C) 2020 markt.de GmbH & Co KG / Munich / Germany
 */
package org.springframework.session.sticky;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;

/**
 * {@link DelegateSaveStrategy} implementation that asynchronously saves
 * delegate sessions after a configured delay. This allows multiple updates to
 * a cache entry be saved only once to the remote repository.
 *
 * A suitable delay value is something lower than the value configured for
 * {@link StickySessionRepository#setRevalidateAfter(Duration) revalidateAfter}
 *
 * @author Bernhard Frauendienst <bernhard.frauendienst@markt.de>
 */
public class DelayedDelegateSaveStrategy implements DelegateSaveStrategy, DisposableBean {

  private final ScheduledExecutorService executor;

  private final boolean manageExecutor;

  private final long delay;

  private final TimeUnit timeUnit;

  public DelayedDelegateSaveStrategy(ScheduledExecutorService executor, long delay, TimeUnit timeUnit) {
    this(executor, false, delay, timeUnit);
  }

  public DelayedDelegateSaveStrategy(ScheduledExecutorService executor, boolean manageExecutor, long delay, TimeUnit timeUnit) {
    this.executor = executor;
    this.manageExecutor = manageExecutor;
    this.delay = delay;
    this.timeUnit = timeUnit;
  }

  @Override
  public void queueSaveDelegate(Runnable saveDelegate) {
    executor.schedule(saveDelegate, delay, timeUnit);
  }

  @Override
  public void destroy() {
    if (manageExecutor) {
      executor.shutdown();
    }
  }

  public static DelayedDelegateSaveStrategy withScheduledThreadPool(int nThreads, long delay, TimeUnit timeUnit) {
    return new DelayedDelegateSaveStrategy(Executors.newScheduledThreadPool(nThreads), true, delay, timeUnit);
  }
}
