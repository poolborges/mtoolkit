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
package org.tigris.mtoolkit.iagent.internal.rpc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.tigris.mtoolkit.iagent.event.EventData;
import org.tigris.mtoolkit.iagent.event.EventSynchronizer;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.pmp.PMPServer;

public class EventSynchronizerImpl extends Thread implements EventSynchronizer {

	private List eventQueue = new LinkedList();
	private List eventTypes = new ArrayList();
	private volatile boolean running;
	private PMPServer server;
	private ServiceRegistration registration;
	private BundleContext bc;

	EventSynchronizerImpl(BundleContext bc) {
		super("IAgent RPC Event Thread");
		this.bc = bc;
		setDaemon(true);

		registration = bc.registerService(EventSynchronizer.class.getName(), this, null);
	}

	void setPMPServer(PMPServer server) {
		// TODO: Make event synchronizer listen for PMP servers
		if (server == null)
			throw new IllegalArgumentException("Cannot pass null as a parameter");
		if (this.server != null)
			throw new IllegalStateException("Event synchronizer already initialized");
		this.server = server;
		synchronized (eventTypes) {
			for (Iterator it = eventTypes.iterator(); it.hasNext();) {
				String evType = (String) it.next();
				server.addEventSource(evType);
			}
		}
		addEventSource(RemoteDeploymentAdminImpl.DEPLOYMENT_EVENT);
	}

	public void addEventSource(String eventType) {
		log("[addEventSource] >>> eventType: " + eventType);
		synchronized (eventTypes) {
			if (!eventTypes.contains(eventType))
				eventTypes.add(eventType);
			if (server != null)
				server.addEventSource(eventType);
		}
	}

	public void removeEventSource(String eventType) {
		log("[removeEventSource] >>> eventType: " + eventType);
		synchronized (eventTypes) {
			if (server != null)
				server.removeEventSource(eventType);
			eventTypes.remove(eventType);
		}
	}

	public void start() {
		if (server == null)
			throw new IllegalStateException("Event synchronizer is not fully initialized");
		running = true;
		super.start();
	}

	public void run() {
		while (running) {
			EventData eventData = null;
			synchronized (this) {
				try {
					while (eventQueue.isEmpty() && running) {
						log("[run] event queue is empty >> thread will wait");
						wait();
					}
				} catch (InterruptedException e) {
					running = false;
					return;
				}
				if (!running)
					return;
				eventData = (EventData) eventQueue.remove(0);
			}
			Object convEvent = eventData.getConvertedEvent();
			String eventType = eventData.getEventType();
			log("[run] sending event: " + eventData);
			server.event(convEvent, eventType);
		}
	}

	public void enqueue(EventData eventData) {
		log("[enqueue] >>> eventData: " + eventData);
		if (!running) {
			log("[enqueue] Not running anymore. Skipping...");
			return;
		}
		synchronized (this) {
			eventQueue.add(eventData);
			notify();
		}
	}

	public void stopDispatching() {
		synchronized (this) {
			running = false;
			notifyAll();
		}
		unregister(bc);
	}

	public void unregister(BundleContext bc) {
		log("[unregister] Unregistering EventSynchronizer...");

		for (int i = 0; i < eventTypes.size(); i++) {
			server.removeEventSource((String) eventTypes.get(i));
		}

		if (registration != null) {
			registration.unregister();
			registration = null;
		}
		this.bc = null;
		log("[unregister] EventSynchronizer unregistered.");
	}

	private static final void log(String message) {
		log(message, null);
	}

	private static final void log(String message, Throwable e) {
		DebugUtils.log(EventSynchronizerImpl.class, message, e);
	}
}
