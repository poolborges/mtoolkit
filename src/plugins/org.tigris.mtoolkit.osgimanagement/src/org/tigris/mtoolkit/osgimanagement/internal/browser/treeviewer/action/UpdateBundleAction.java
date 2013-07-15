/*******************************************************************************
 * Copyright (c) 2005, 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.Bundle;
import org.tigris.mtoolkit.osgimanagement.model.AbstractFrameworkTreeElementAction;

public final class UpdateBundleAction extends AbstractFrameworkTreeElementAction<Bundle> {
  private static final int UPDATE_BUNDLE_STATE_MASK = org.osgi.framework.Bundle.INSTALLED
                                                        | org.osgi.framework.Bundle.RESOLVED
                                                        | org.osgi.framework.Bundle.STARTING
                                                        | org.osgi.framework.Bundle.ACTIVE;

  public UpdateBundleAction(ISelectionProvider provider, String label) {
    super(false, Bundle.class, provider, label);
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action.AbstractFrameworkTreeElementAction#execute(org.tigris.mtoolkit.osgimanagement.model.Model)
   */
  @Override
  protected void execute(Bundle element) {
    ActionsManager.updateBundleAction(element);
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action.AbstractFrameworkTreeAction#isEnabled(org.tigris.mtoolkit.osgimanagement.model.Model)
   */
  @Override
  protected boolean isEnabledFor(Bundle bundle) {
    if (bundle.isSystemBundle()) {
      return false;
    }
    return (bundle.getState() & UPDATE_BUNDLE_STATE_MASK) != 0;
  }
}
