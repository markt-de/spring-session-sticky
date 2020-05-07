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
 * Interface for dispatching saving of a delegate session.
 *
 * @see SynchronousDelegateSaveStrategy
 * @see AsyncDelegateSaveStrategy
 * @see DelayedDelegateSaveStrategy
 *
 * @author Bernhard Frauendienst <bernhard.frauendienst@markt.de>
 */
public interface DelegateSaveStrategy {
  void queueSaveDelegate(Runnable saveDelegate);
}
