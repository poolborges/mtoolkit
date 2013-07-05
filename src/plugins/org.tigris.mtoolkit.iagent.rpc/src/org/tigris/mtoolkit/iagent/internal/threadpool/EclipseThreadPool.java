/*******************************************************************************
 * Copyright (c) 2005, 2013 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.iagent.internal.threadpool;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

final class EclipseThreadPool extends ThreadPool {
  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.internal.threadpool.ThreadPool#enqueueWork(java.lang.Runnable)
   */
  public void enqueueWork(final Runnable runnable) {
    Job job = new Job(WORKER_NAME) {
      protected IStatus run(IProgressMonitor monitor) {
        runnable.run();
        return Status.OK_STATUS;
      }
    };
    job.schedule();
  }
}
