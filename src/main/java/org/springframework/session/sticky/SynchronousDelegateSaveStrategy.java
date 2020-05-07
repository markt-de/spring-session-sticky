/*
 * This document contains trade secret data which is the property of markt.de
 * GmbH & Co KG. Information contained herein may not be used, copied or
 * disclosed in whole or part except as permitted by written agreement from
 * markt.de GmbH & Co KG.
 *
 * Copyright (C) 2020 markt.de GmbH & Co KG / Munich / Germany
 */
package org.springframework.session.sticky;

/**
 * {@link DelegateSaveStrategy} implementation that synchronously saves
 * delegate sessions in the caller thread.
 *
 * @author Bernhard Frauendienst <bernhard.frauendienst@markt.de>
 */
public class SynchronousDelegateSaveStrategy implements DelegateSaveStrategy {
  @Override
  public void queueSaveDelegate(Runnable saveDelegate) {
    saveDelegate.run();
  }
}
