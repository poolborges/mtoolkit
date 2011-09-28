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
package org.tigris.mtoolkit.common.certificates;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.tigris.mtoolkit.common.Messages;
import org.tigris.mtoolkit.common.images.UIResources;

public class CertificatesPanel {

	private static final String MTOOLKIT_PAGE_ID = "org.tigris.mtoolkit.common.preferences.MToolkitPreferencePage"; //$NON-NLS-1$

  private Composite signContentGroup;
	private Label lblCertificates;
	private Table tblCertificates;
	private CheckboxTableViewer certificatesViewer;
	private Link link;
	private Set listeners = new HashSet();

	/**
	 * @since 5.0
	 */
  public static final int EVENT_CONTENT_MODIFIED = 1;

	public CertificatesPanel(Composite parent, int horizontalSpan, int verticalSpan) {
		this(parent, horizontalSpan, verticalSpan, GridData.FILL_BOTH);
	}
	/**
	 * @since 6.0
	 */
  public CertificatesPanel(Composite parent, int horizontalSpan, int verticalSpan, boolean isComposite) {
    this(parent, horizontalSpan, verticalSpan, GridData.FILL_BOTH, isComposite);
  }

  public CertificatesPanel(Composite parent, int horizontalSpan, int verticalSpan, int style, boolean isComposite) {
    if (isComposite) {
      signContentGroup = new Composite(parent, SWT.NONE);
    } else {
      signContentGroup = new Group(parent, SWT.NONE);
      ((Group) signContentGroup).setText(Messages.CertificatesPanel_signContentGroup);
    }
    initContent(horizontalSpan, verticalSpan, style);
  }

	public CertificatesPanel(Composite parent, int horizontalSpan, int verticalSpan, int style) {
    signContentGroup = new Group(parent, SWT.NONE);
    ((Group) signContentGroup).setText(Messages.CertificatesPanel_signContentGroup);
    initContent(horizontalSpan, verticalSpan, style);
  }
  
  private void initContent(int horizontalSpan, int verticalSpan, int style) {
    GridData gridData = new GridData(style);
    gridData.horizontalSpan = horizontalSpan;
    gridData.verticalSpan = verticalSpan;
    signContentGroup.setLayoutData(gridData);
    signContentGroup.setLayout(new GridLayout());

    lblCertificates = new Label(signContentGroup, SWT.NONE);
    lblCertificates.setText(Messages.CertificatesPanel_lblCertificates);
    lblCertificates.setLayoutData(new GridData());

    // Certificates table
    int stl = SWT.SINGLE | SWT.CHECK | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION;
    tblCertificates = new Table(signContentGroup, stl);
    gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    gridData.heightHint = 60;
    tblCertificates.setLayoutData(gridData);
    tblCertificates.setLinesVisible(true);
    tblCertificates.setHeaderVisible(true);
    tblCertificates.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        if (e.detail == SWT.CHECK) {
          fireModifyEvent();
        }
      }
    });
    TableColumn column = new TableColumn(tblCertificates, SWT.LEFT);
    column.setText(Messages.CertificatesPanel_tblCertColAlias);
    column.setWidth(100);
    column = new TableColumn(tblCertificates, SWT.LEFT);
    column.setText(Messages.CertificatesPanel_tblCertColLocation);
    column.setWidth(160);

    certificatesViewer = new CheckboxTableViewer(tblCertificates);
    certificatesViewer.setContentProvider(new CertContentProvider());
    certificatesViewer.setLabelProvider(new CertLabelProvider());
    
  }

  /**
   * Initializes the signing certificates with the given list of certificate
   * ids (of type String). This method could be called multiple times.
   * 
   * @param signUids
   *            list with certificate ids or <code>null</code>
   */
  public void initialize(List signUids) {
		ICertificateDescriptor certificates[] = CertUtils.getCertificates();
    certificatesViewer.setAllChecked(false);
		certificatesViewer.setInput(certificates);
		if (certificates == null || certificates.length == 0) {
			setNoCertificatesAvailable();
		} else {
     if (link != null) {
        disposeNoCertificatesAvailableState();
      }
			if (signUids != null) {
				for (int i = 0; i < certificates.length; i++) {
					if (signUids.contains(certificates[i].getUid())) {
						certificatesViewer.setChecked(certificates[i], true);
          }
			}
      }
    }
  }

	public List getSignCertificateUids() {
		List signUids = new ArrayList();
			Object[] checkedCerts = certificatesViewer.getCheckedElements();
			for (int i = 0; i < checkedCerts.length; i++) {
				signUids.add(((ICertificateDescriptor) checkedCerts[i]).getUid());
		}
		return signUids;
	}

	/**
	 * @since 5.0
	 */
	public void addEventListener(Listener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	/**
	 * @since 5.0
	 */
	public void removeEventListener(Listener listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

	private void fireModifyEvent() {
		if (listeners.isEmpty()) {
			return;
		}
		Event event = new Event();
		event.type = EVENT_CONTENT_MODIFIED;
		for (Iterator it = listeners.iterator(); it.hasNext();) {
			Listener listener = (Listener) it.next();
			listener.handleEvent(event);
		}
	}

	private void setNoCertificatesAvailable() {
		setCertificateControlsVisible(false);

		if (link == null) {
			link = new Link(signContentGroup, SWT.NONE);
			link.setLayoutData(new GridData());
			link.setText(Messages.CertificatesPanel_lblNoCertificates);
			link.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
					PreferencesUtil.createPreferenceDialogOn(shell, MTOOLKIT_PAGE_ID, null, null).open();
					ICertificateDescriptor certificates[] = CertUtils.getCertificates();
					if (certificates == null || certificates.length == 0) {
						return;
					}
          initialize(null);
				}
			});
		}

		signContentGroup.layout();
	}
  
  private void disposeNoCertificatesAvailableState() {
    link.dispose();
    link = null;
    setCertificateControlsVisible(true);
    layoutControls();
  }

	private void setCertificateControlsVisible(boolean visible) {
		lblCertificates.setVisible(visible);
		((GridData) lblCertificates.getLayoutData()).exclude = !visible;

		tblCertificates.setVisible(visible);
		((GridData) tblCertificates.getLayoutData()).exclude = !visible;
	}

	private void layoutControls() {
		signContentGroup.layout();

		Composite parent = signContentGroup;
		while (parent != null) {
			if (parent instanceof Shell) {
				Shell shell = (Shell) parent;
				Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				int sizeX = Math.max(shell.getSize().x, size.x);
				int sizeY = Math.max(shell.getSize().y, size.y);
				shell.setBounds(shell.getLocation().x, shell.getLocation().y, sizeX, sizeY);
				break;
			}
			parent = parent.getParent();
		}

		if (signContentGroup.getParent() != null) {
			signContentGroup.getParent().layout();
		}
	}

	private class CertLabelProvider extends LabelProvider implements ITableLabelProvider {
		private Image iconCertMissing;

		public CertLabelProvider() {
			super();

			Image iconCert = UIResources.getImage(UIResources.CERTIFICATE_ICON);
			ImageDescriptor overlay = UIResources.getImageDescriptor(UIResources.OVR_ERROR_ICON);
			iconCertMissing = new DecorationOverlayIcon(iconCert, overlay, IDecoration.BOTTOM_LEFT).createImage();
		}

		public Image getColumnImage(Object element, int columnIndex) {
			if (!(element instanceof ICertificateDescriptor) || columnIndex > 0) {
				return null;
			}
			ICertificateDescriptor cert = (ICertificateDescriptor) element;
			File keystore = new File(cert.getStoreLocation());
			if (!keystore.exists() || !keystore.isFile()) {
				return iconCertMissing;
			}
			return UIResources.getImage(UIResources.CERTIFICATE_ICON);
		}

		public String getColumnText(Object element, int columnIndex) {
			if (!(element instanceof ICertificateDescriptor)) {
				return null;
			}
			ICertificateDescriptor cert = (ICertificateDescriptor) element;
			String columnText = null;
			switch (columnIndex) {
			case 0:
				columnText = cert.getAlias();
				break;
			case 1:
				columnText = cert.getStoreLocation();
				break;
			}
			return columnText;
		}

		public void dispose() {
			iconCertMissing.dispose();
		}
	}

	private class CertContentProvider implements IStructuredContentProvider {

		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof Object[]) {
				return ((Object[]) inputElement);
			}
			return new Object[0];
		}

		public void dispose() {
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}
}
