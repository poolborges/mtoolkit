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
package org.tigris.mtoolkit.iagent.internal.tcp;

import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;

import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.pmp.EventListener;
import org.tigris.mtoolkit.iagent.pmp.PMPException;
import org.tigris.mtoolkit.iagent.pmp.PMPService;
import org.tigris.mtoolkit.iagent.pmp.PMPServiceFactory;
import org.tigris.mtoolkit.iagent.pmp.RemoteObject;
import org.tigris.mtoolkit.iagent.spi.ConnectionManager;
import org.tigris.mtoolkit.iagent.spi.PMPConnection;
import org.tigris.mtoolkit.iagent.spi.PMPConnector;
import org.tigris.mtoolkit.iagent.spi.Utils;
import org.tigris.mtoolkit.iagent.transport.Transport;
import org.tigris.mtoolkit.iagent.util.LightServiceRegistry;

public class PMPConnectionImpl implements PMPConnection, EventListener {

	private org.tigris.mtoolkit.iagent.pmp.PMPConnection pmpConnection;
	private ConnectionManagerImpl connManager;

	private HashMap remoteObjects = new HashMap(5);
	private RemoteObject administration;
	private RemoteObject remoteParserService;

	private LightServiceRegistry pmpRegistry;
	private volatile boolean closed = false;

	public PMPConnectionImpl(Transport transport, Dictionary conProperties, ConnectionManagerImpl connManager)
			throws IAgentException {
		log("[Constructor] >>> Create PMP Connection: props: " + DebugUtils.convertForDebug(conProperties)
				+ "; manager: " + connManager);
		PMPService pmpService = PMPServiceFactory.getDefault();
		try {
			log("[Constructor] Transport: " + transport);
			pmpConnection = pmpService.connect(transport);
		} catch (PMPException e) {
			log("[Constructor] Failed to create PMP connection 1", e);
			if ("socket".equals(transport.getType().getTypeId())) {
				// if we are using old socket protocol, try to create
				// backward compatible connection
				try {
					pmpConnection = createClosedConnection(transport.getId());
				} catch (PMPException e2) {
					log("[Constructor] Failed to create PMP connection 2", e2);
					throw new IAgentException("Unable to connect to the framework", IAgentErrors.ERROR_CANNOT_CONNECT,
							e2);
				}
			}
			if (pmpConnection == null)
				throw new IAgentException("Unable to connect to the framework", IAgentErrors.ERROR_CANNOT_CONNECT, e);
		}
		this.connManager = connManager;
		pmpConnection.addEventListener(this,
				new String[] { org.tigris.mtoolkit.iagent.pmp.PMPConnection.FRAMEWORK_DISCONNECTED });
	}

	public int getType() {
		return ConnectionManager.PMP_CONNECTION;
	}

	public void closeConnection() throws IAgentException {
		log("[closeConnection] >>>");
		synchronized (this) {
			if (closed) {
				log("[closeConnection] Already closed");
				return;
			}
			closed = true;
		}

		try {
			resetRemoteReferences();

			log("[closeConnection] remove event listener");
			pmpConnection.removeEventListener(this,
					new String[] { org.tigris.mtoolkit.iagent.pmp.PMPConnection.FRAMEWORK_DISCONNECTED });
			pmpConnection.disconnect("Integration Agent request");
		} finally {
			if (connManager != null) {
				try {
					connManager.connectionClosed(this);
				} catch (Throwable e) {
					log("[closeConnection] Internal error in connection manager", e);
				}
			}
		}
	}

	private org.tigris.mtoolkit.iagent.pmp.PMPConnection createClosedConnection(String targetIP) throws PMPException {
		org.tigris.mtoolkit.iagent.pmp.PMPConnection connection = null;
		if (targetIP == null)
			throw new IllegalArgumentException(
					"Connection properties hashtable does not contain device IP value with key DeviceConnector.KEY_DEVICE_IP!");
		PMPConnector connectionMngr = (PMPConnector) getManager("org.tigris.mtoolkit.iagent.spi.PMPConnector");
		if (connectionMngr != null) {
			connection = connectionMngr.createPMPConnection(targetIP);
		}
		return connection;
	}

	private void resetRemoteReferences() {
		log("[resetRemoteReferences] >>>");
		Utils.clearCache();
		if (remoteObjects != null) {
			Collection objects = remoteObjects.values();
			for (Iterator iterator = objects.iterator(); iterator.hasNext();) {
				RemoteObject remoteObject = (RemoteObject) iterator.next();
				try {
					remoteObject.dispose();
				} catch (PMPException e) {
					log("[resetRemoteReferences] Failure during PMP connection cleanup", e);
				}
			}
			remoteObjects.clear();
		}

		if (remoteParserService != null) {
			try {
				remoteParserService.dispose();
			} catch (PMPException e) {
				log("[resetRemoteReferences] Failure during PMP connection cleanup", e);
			}
			remoteParserService = null;
		}

		if (administration != null) {
			try {
				administration.dispose();
			} catch (PMPException e) {
				log("[resetRemoteReferences] Failure during PMP connection cleanup", e);
			}
			administration = null;
		}
	}

	public boolean isConnected() {
		return !closed && pmpConnection.isConnected();
	}

	public RemoteObject getRemoteBundleAdmin() throws IAgentException {
		return getRemoteAdmin(REMOTE_BUNDLE_ADMIN_NAME);
	}

	public RemoteObject getRemoteApplicationAdmin() throws IAgentException {
		return getRemoteAdmin(REMOTE_APPLICATION_ADMIN_NAME);
	}

	public RemoteObject getRemoteDeploymentAdmin() throws IAgentException {
		return getRemoteAdmin(REMOTE_DEPLOYMENT_ADMIN_NAME);
	}

	public RemoteObject getRemoteParserService() throws IAgentException {
		log("[getRemoteParserService] >>>");
		if (!isConnected()) {
			log("[getRemoteParserService] The connecton has been closed!");
			throw new IAgentException("The connecton has been closed!", IAgentErrors.ERROR_DISCONNECTED);
		}
		if (remoteParserService == null) {
			log("[getRemoteParserService] No RemoteParserService. Creating");
			try {
				remoteParserService = pmpConnection.getReference(REMOTE_CONSOLE_NAME, null);
			} catch (PMPException e) {
				log("[getRemoteParserService] RemoteParserGenerator service isn't available", e);
				throw new IAgentException("Unable to retrieve reference to remote administration service",
						IAgentErrors.ERROR_INTERNAL_ERROR);
			}
		}
		return remoteParserService;
	}

	public void releaseRemoteParserService() throws IAgentException {
		log("[releaseRemoteParserService] >>>");
		if (remoteParserService != null) {
			try {
				Utils.callRemoteMethod(remoteParserService, Utils.RELEASE_METHOD, null);
				remoteParserService.dispose();
			} catch (PMPException e) {
				log("[releaseRemoteParserService]", e);
			}
			remoteParserService = null;
		}
	}

	public void addEventListener(EventListener listener, String[] eventTypes) throws IAgentException {
		log("[addEventListener] >>> listener: " + listener + "; eventTypes: " + DebugUtils.convertForDebug(eventTypes));
		if (!isConnected()) {
			log("[addEventListener] The connecton has been closed!");
			throw new IAgentException("The connecton has been closed!", IAgentErrors.ERROR_DISCONNECTED);
		}

		pmpConnection.addEventListener(listener, eventTypes);
	}

	public void removeEventListener(EventListener listener, String[] eventTypes) throws IAgentException {
		log("[removeEventListener] listener: " + listener + "; eventTypes: " + DebugUtils.convertForDebug(eventTypes));
		if (!isConnected()) {
			log("[removeEventListener] The connecton has been closed!");
			throw new IAgentException("The connecton has been closed!", IAgentErrors.ERROR_DISCONNECTED);
		}
		pmpConnection.removeEventListener(listener, eventTypes);
	}

	public RemoteObject getRemoteServiceAdmin() throws IAgentException {
		return getRemoteAdmin(REMOTE_SERVICE_ADMIN_NAME);
	}

	public RemoteObject getRemoteAdmin(String adminClassName) throws IAgentException {
		log("[getRemoteAdmin]" + adminClassName + " >>>");
		if (!isConnected()) {
			log("[getRemoteBundleAdmin] The connecton has been closed!");
			throw new IAgentException("The connecton has been closed!", IAgentErrors.ERROR_DISCONNECTED);
		}
		RemoteObject admin = (RemoteObject) remoteObjects.get(adminClassName);
		if (admin == null) {
			try {
				log("[getRemoteAdmin] No remote admin [" + adminClassName + "]. Creating...");
				final String adminClass = adminClassName;
				admin = new PMPRemoteObjectAdapter(pmpConnection.getReference(adminClassName, null)) {
					public int verifyRemoteReference() throws IAgentException {
						if (!pmpConnection.isConnected()) {
							this.log("[verifyRemoteReference] The connection has been closed!");
							throw new IAgentException("The connecton has been closed!", IAgentErrors.ERROR_DISCONNECTED);
						}
						try {
							RemoteObject newRemoteObject = pmpConnection.getReference(adminClass, null);
							Long l = (Long) Utils.callRemoteMethod(newRemoteObject, Utils.GET_REMOTE_SERVICE_ID_METHOD,
									null);
							long newServiceID = l.longValue();
							if (newServiceID == -1) {
								this
										.log("[verifyRemoteReference] New reference service id is = -1. Nothing to do. Continuing.");
								return PMPRemoteObjectAdapter.CONTINUE;
							}
							this.log("[verifyRemoteReference] initial: " + this.getInitialServiceID() + "; new: " + l);
							if (newServiceID != this.getInitialServiceID()) {
								this.delegate = newRemoteObject;
								this.setInitialServiceID(newServiceID);
								this
										.log("[verifyRemoteReference] Reference to remote service was refreshed. Retry remote method call...");
								return PMPRemoteObjectAdapter.REPEAT;
							}
							newRemoteObject.dispose();
							this.log("[verifyRemoteReference] Reference to remote service is looking fine. Continue");
							return PMPRemoteObjectAdapter.CONTINUE;
						} catch (PMPException e) {
							// admin = null;
							this
									.log(
											"[verifyRemoteReference] Reference to remote service cannot be got, service is not available. Fail fast.",
											e);
							throw new IAgentException("Unable to retrieve reference to remote administration service",
									IAgentErrors.ERROR_REMOTE_ADMIN_NOT_AVAILABLE, e);
						}
					}
				};
				if (admin != null)
					remoteObjects.put(adminClassName, admin);
			} catch (PMPException e) {
				this.log("[getRemoteAdmin] Remote admin [" + adminClassName + "] isn't available", e);
				throw new IAgentException("Unable to retrieve reference to remote administration service ["
						+ adminClassName + "]", IAgentErrors.ERROR_REMOTE_ADMIN_NOT_AVAILABLE, e);
			}
		}
		return admin;
	}

	public void event(Object ev, String evType) {
		log("[event] >>> Object event: " + ev + "; eventType: " + evType);
		if (org.tigris.mtoolkit.iagent.pmp.PMPConnection.FRAMEWORK_DISCONNECTED.equals(evType)) {
			try {
				log("[event] Framework disconnection event received");
				closeConnection();
			} catch (Throwable e) {
				log("[event] Exception while cleaning up the connection", e);
			}
		}
	}

	private final void log(String message) {
		log(message, null);
	}

	private final void log(String message, Throwable e) {
		DebugUtils.log(this, message, e);
	}

	private LightServiceRegistry getServiceRegistry() {
		if (pmpRegistry == null)
			pmpRegistry = new LightServiceRegistry(PMPConnectionImpl.class.getClassLoader());
		return pmpRegistry;
	}

	public Object getManager(String className) {
		LightServiceRegistry registry = getServiceRegistry();
		return registry.get(className);
	}
}
