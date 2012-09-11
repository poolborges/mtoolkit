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

import java.util.LinkedList;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.tigris.mtoolkit.iagent.event.EventData;
import org.tigris.mtoolkit.iagent.event.EventSynchronizer;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.internal.utils.ThreadUtils;
import org.tigris.mtoolkit.iagent.pmp.PMPServer;

public class EventSynchronizerImpl implements Runnable, EventSynchronizer {

  private List                eventQueue = new LinkedList();
  private volatile boolean    running;
  private PMPServer           server;
  private ServiceRegistration registration;
  private BundleContext       bc;
  private Thread              eventsThread;

  EventSynchronizerImpl(BundleContext bc) {
    eventsThread = ThreadUtils.createThread(this, "IAgent RPC Event Thread");
    this.bc = bc;
    eventsThread.setDaemon(true);

    registration = bc.registerService(EventSynchronizer.class.getName(), this, null);
  }

  void setPMPServer(PMPServer server) {
    // TODO: Make event synchronizer listen for PMP servers
    if (server == null) {
      throw new IllegalArgumentException("Cannot pass null as a parameter");
    }
    if (this.server != null) {
      throw new IllegalStateException("Event synchronizer already initialized");
    }
    this.server = server;
  }

  public void start() {
    if (server == null) {
      throw new IllegalStateException("Event synchronizer is not fully initialized");
    }
    running = true;
    eventsThread.start();
  }

  public void run() {
    while (running) {
      EventData eventData = null;
      synchronized (this) {
        try {
          while (eventQueue.isEmpty() && running) {
            if (DebugUtils.DEBUG_ENABLED) {
              debug("[run] event queue is empty >> thread will wait");
            }
            wait();
          }
        } catch (InterruptedException e) {
          running = false;
          return;
        }
        if (!running) {
          return;
        }
        eventData = (EventData) eventQueue.remove(0);
      }
      Object convEvent = eventData.getConvertedEvent();
      String eventType = eventData.getEventType();
      if (DebugUtils.DEBUG_ENABLED) {
        debug("[run] sending event: " + eventData);
      }
      server.event(convEvent, eventType);
    }
  }

  public void enqueue(EventData eventData) {
    if (DebugUtils.DEBUG_ENABLED) {
      debug("[enqueue] >>> eventData: " + eventData);
    }
    if (!running) {
      if (DebugUtils.DEBUG_ENABLED) {
        debug("[enqueue] Not running anymore. Skipping...");
      }
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
    if (DebugUtils.DEBUG_ENABLED) {
      debug("[unregister] Unregistering EventSynchronizer...");
    }

    if (registration != null) {
      registration.unregister();
      registration = null;
    }
    this.bc = null;
    if (DebugUtils.DEBUG_ENABLED) {
      debug("[unregister] EventSynchronizer unregistered.");
    }
  }

  private final void debug(String message) {
    DebugUtils.debug(this, message);
  }
}
