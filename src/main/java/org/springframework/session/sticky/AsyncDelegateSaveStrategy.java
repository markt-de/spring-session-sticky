/*
 * This document contains trade secret data which is the property of markt.de
 * GmbH & Co KG. Information contained herein may not be used, copied or
 * disclosed in whole or part except as permitted by written agreement from
 * markt.de GmbH & Co KG.
 *
 * Copyright (C) 2020 markt.de GmbH & Co KG / Munich / Germany
 */
package org.springframework.session.sticky;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.DisposableBean;

/**
 * {@link DelegateSaveStrategy} implementation that asynchronously saves
 * delegate sessions with a given executor, usually a {@link java.util.concurrent.ThreadPoolExecutor}.
 *
 * @author Bernhard Frauendienst <bernhard.frauendienst@markt.de>
 */
public class AsyncDelegateSaveStrategy implements DelegateSaveStrategy, DisposableBean {

  private final Executor executor;

  private final boolean manageExecutor;

  public AsyncDelegateSaveStrategy(Executor executor) {
    this.executor = executor;
    this.manageExecutor = false;
  }

  public AsyncDelegateSaveStrategy(ExecutorService executor, boolean manageExecutor) {
    this.executor = executor;
    this.manageExecutor = manageExecutor;
  }

  @Override
  public void queueSaveDelegate(Runnable saveDelegate) {
    executor.execute(saveDelegate);
  }

  @Override
  public void destroy() {
    if (manageExecutor) {
      ((ExecutorService) executor).shutdown();
    }
  }

  public static AsyncDelegateSaveStrategy withFixedThreadPool(int nThreads) {
    return new AsyncDelegateSaveStrategy(Executors.newFixedThreadPool(nThreads), true);
  }
}
