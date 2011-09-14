package org.tigris.mtoolkit.common.gui;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * @since 6.0
 */
public final class FilterJob extends Job {
  private final StructuredViewer viewer;
  private final Runnable postRefreshAction;

  public FilterJob(String name, StructuredViewer viewer) {
    this(name, viewer, null);
  }

  public FilterJob(String name, StructuredViewer aViewer, Runnable apostRefreshAction) {
    super(name);
    viewer = aViewer;
    postRefreshAction = apostRefreshAction;
  }

  /* (non-Javadoc)
   * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
   */
  protected IStatus run(IProgressMonitor monitor) {
    Display display = PlatformUI.getWorkbench().getDisplay();
    if (!display.isDisposed()) {
      display.asyncExec(new Runnable() {
        public void run() {
          Control control = viewer.getControl();
          if (control.isDisposed()) {
            return;
          }
          viewer.refresh();
          if (postRefreshAction != null) {
            postRefreshAction.run();
          }
        }
      });
    }
    return Status.OK_STATUS;
  }

}
