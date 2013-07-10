/*******************************************************************************
 * Copyright (c) 2005, 2012 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.dpeditor.osgimanagement.dp.logic;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.tigris.mtoolkit.osgimanagement.model.Framework;

public final class DPOperationSchedulingRule implements ISchedulingRule {
  private Framework fw;

  public DPOperationSchedulingRule(Framework fw) {
    Assert.isNotNull(fw);
    this.fw = fw;
  }

  /* (non-Javadoc)
   * @see org.eclipse.core.runtime.jobs.ISchedulingRule#contains(org.eclipse.core.runtime.jobs.ISchedulingRule)
   */
  public boolean contains(ISchedulingRule rule) {
    if (rule == this) {
      return true;
    }
    return false;
  }

  /* (non-Javadoc)
   * @see org.eclipse.core.runtime.jobs.ISchedulingRule#isConflicting(org.eclipse.core.runtime.jobs.ISchedulingRule)
   */
  public boolean isConflicting(ISchedulingRule rule) {
    if (!(rule instanceof DPOperationSchedulingRule)) {
      return false;
    }
    return ((DPOperationSchedulingRule) rule).fw == fw;
  }
}