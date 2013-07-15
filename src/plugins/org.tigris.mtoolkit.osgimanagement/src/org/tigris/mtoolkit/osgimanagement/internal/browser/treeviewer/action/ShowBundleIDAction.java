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
import org.eclipse.jface.viewers.TreeViewer;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.TreeRoot;
import org.tigris.mtoolkit.osgimanagement.model.AbstractFrameworkTreeAction;

public final class ShowBundleIDAction extends AbstractFrameworkTreeAction {
  private final TreeViewer tree;
  private final TreeRoot   root;

  public ShowBundleIDAction(ISelectionProvider provider, String label, TreeViewer tree, TreeRoot root) {
    super(provider, label);
    this.tree = tree;
    this.root = root;
    setChecked(root.isShowBundlesID());
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.action.Action#run()
   */
  @Override
  public void run() {
    root.setShowBundlesID(!root.isShowBundlesID());
    setChecked(root.isShowBundlesID());
    tree.refresh();
  }
}
