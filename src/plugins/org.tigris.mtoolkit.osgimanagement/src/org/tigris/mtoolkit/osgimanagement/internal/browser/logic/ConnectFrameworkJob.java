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
package org.tigris.mtoolkit.osgimanagement.internal.browser.logic;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.statushandlers.StatusManager;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.osgimanagement.internal.FrameWorkView;
import org.tigris.mtoolkit.osgimanagement.internal.FrameworkPlugin;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameWork;

public class ConnectFrameworkJob extends Job {
	private static List connectingFrameworks = new ArrayList();

	private FrameWork fw;

	public ConnectFrameworkJob(FrameWork framework) {
		super(NLS.bind(Messages.connect_framework, framework.getName()));
		this.fw = framework;
	}

	public IStatus run(IProgressMonitor monitor) {
		monitor.beginTask(NLS.bind(Messages.connect_framework, fw.getName()), 1);

		synchronized (connectingFrameworks) {
			// there is already job for this fw, so wait that job
			// otherwise, start connecting
			if (connectingFrameworks.contains(fw)) {
				do {
					try {
						connectingFrameworks.wait();
					} catch (InterruptedException e) {
					}
					if (monitor.isCanceled()) {
						monitor.done();
						return Status.CANCEL_STATUS;
					}
				} while (connectingFrameworks.contains(fw));
				monitor.done();
				if (fw.isConnected()) {
					return Status.OK_STATUS;
				} else {
					return FrameworkPlugin.newStatus(IStatus.ERROR,
						"Could not connect to framework " + fw.getName(),
						null);
				}
			}
			connectingFrameworks.add(fw);
		}

		DeviceConnector connector = fw.getConnector();
		try {
			if (connector != null && connector.isActive()) {
				FrameworkConnectorFactory.createPMPConnection(connector, fw, fw.getName(), fw.autoConnected);
			} else {
				IMemento config = fw.getConfig();
				Dictionary aConnProps = new Hashtable();
				aConnProps.put(DeviceConnector.KEY_DEVICE_IP, config.getString(ConstantsDistributor.FRAMEWORK_IP_ID));
				aConnProps.put("framework-name", fw.getName()); //$NON-NLS-1$
				aConnProps.put("framework-connection-immediate", new Boolean(false)); //$NON-NLS-1$
				connector = DeviceConnector.openClientConnection(DeviceConnector.TYPE_TCP, aConnProps);
				fw.setConnector(connector);
				FrameworkConnectorFactory.createPMPConnection(connector, fw, fw.getName(), false);
			}
		} catch (IAgentException e) {
			if (e.getErrorCode() == IAgentErrors.ERROR_CANNOT_CONNECT)
				handleConnectionFailure();
			else
				return FrameworkPlugin.handleIAgentException(e);
		} finally {
			// remove the framework in any case
			synchronized (connectingFrameworks) {
				connectingFrameworks.remove(fw);
				connectingFrameworks.notifyAll();
			}
		}

		monitor.done();
		return Status.OK_STATUS;
	}

	public static boolean isConnecting(FrameWork fw) {
		synchronized (connectingFrameworks) {
			return connectingFrameworks.contains(fw);
		}
	}

	protected void handleConnectionFailure() {
		final Display display = Display.getDefault();
		display.asyncExec(new Runnable() {
			public void run() {
				String[] buttons = { Messages.close_button_label, Messages.get_iagent_button_label };
				MessageDialog dialog = new MessageDialog(FrameWorkView.getShell(),
					Messages.rcp_bundle_missing_title,
					null,
					Messages.rcp_bundle_missing_message,
					MessageDialog.INFORMATION,
					buttons,
					0);
				dialog.setBlockOnOpen(true);
				dialog.open();
				if (dialog.getReturnCode() == 1) {
					// get IAgent button has been selected
					InputStream iagentInput = FrameworkPlugin.getIAgentBundleAsStream();
					OutputStream output = null;
					try {
						if (iagentInput == null)
							// TODO: Add dialog here
							return;

						FileDialog saveDialog = new FileDialog(display.getActiveShell(), SWT.SAVE);
						saveDialog.setText(Messages.save_as_dialog_title);
						String[] filterExt = { "*.jar" }; //$NON-NLS-1$
						saveDialog.setFilterExtensions(filterExt);
						// TODO: initial filename setting doesn't work on Mac OS
						// X
						saveDialog.setFileName("iagent.rpc.jar");
						String path = saveDialog.open();

						output = new FileOutputStream(path);

						int bytesRead = 0;
						byte[] buffer = new byte[1024];

						while ((bytesRead = iagentInput.read(buffer)) != -1) {
							output.write(buffer, 0, bytesRead);
						}

					} catch (IOException e1) {
						StatusManager.getManager().handle(FrameworkPlugin.newStatus(IStatus.ERROR,
							"An error occurred while saving IAgent bundle",
							e1));
					} finally {
						if (output != null)
							try {
								output.close();
							} catch (IOException e) {
							}
						if (iagentInput != null)
							try {
								iagentInput.close();
							} catch (IOException e) {
							}
					}
				}
			}
		});

	}
}