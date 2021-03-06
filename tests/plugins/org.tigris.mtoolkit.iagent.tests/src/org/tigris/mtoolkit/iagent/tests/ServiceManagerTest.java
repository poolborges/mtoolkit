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
package org.tigris.mtoolkit.iagent.tests;

import java.util.ArrayList;
import java.util.List;

import junit.framework.AssertionFailedError;

import org.osgi.framework.Bundle;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.iagent.RemoteService;
import org.tigris.mtoolkit.iagent.event.RemoteServiceEvent;
import org.tigris.mtoolkit.iagent.event.RemoteServiceListener;

public class ServiceManagerTest extends ServiceManagerTestCase implements RemoteServiceListener {

	private static final int SLEEP_INTERVAL = 5000;
	protected static final String FILTER = "(TEST_PROPERTY=Test)";

	private List events = new ArrayList();
	private Object sleeper = new Object();

	public void testGetRemoteServices() throws IAgentException {
		RemoteBundle bundle = installBundle("test_register_service.jar");
		bundle.start(0);
		assertEquals(Bundle.ACTIVE, bundle.getState());

		assertTrue(isServicePresent(TEST_SERVICE_CLASS, FILTER));
		assertTrue(isServicePresent(TEST_SERVICE_CLASS, null));
		assertTrue(isServicePresent(null, FILTER));
		assertTrue(isServicePresent(null, null));

		try {
			isServicePresent(null, "wrong_filter");
			isServicePresent(TEST_SERVICE_CLASS, "wrong_filter");
			fail("Should have thrown exception, because the filter is invalid");
		} catch (IllegalArgumentException e) {
			// expected
		}

		assertFalse(isServicePresent("unknown_class", null));

		bundle.stop(0);
		assertTrue(bundle.getState() != Bundle.ACTIVE);
		assertFalse(isServicePresent(TEST_SERVICE_CLASS, null));

		bundle.uninstall();
		bundle = null;
	}

	public void testServiceListener() throws IAgentException {
		events.clear();
		addRemoteServiceListener(this);
		RemoteBundle bundle1 = installBundle("test_register_service.jar");
		bundle1.start(0);
		sleep(SLEEP_INTERVAL);
		RemoteServiceEvent registeredEvent = findEvent(TEST_SERVICE_CLASS, RemoteServiceEvent.REGISTERED);
		assertNotNull("Service Registered event not appear!", registeredEvent);
		assertNotNull(registeredEvent.getService());

		events.clear();
		RemoteBundle bundle2 = installBundle("test_listener_service.jar");
		bundle2.start(0);
		sleep(SLEEP_INTERVAL);
		RemoteServiceEvent modifiedEvent = findEvent(TEST_SERVICE_CLASS, RemoteServiceEvent.MODIFIED);
		assertNotNull("Service Modify event not appear!", modifiedEvent);
		assertEquals(registeredEvent.getService().getServiceId(), modifiedEvent.getService().getServiceId());
		assertEquals(registeredEvent.getService().getObjectClass(), modifiedEvent.getService().getObjectClass());
		assertNotNull(modifiedEvent.getService());

		events.clear();
		bundle1.stop(0);
		sleep(SLEEP_INTERVAL);
		RemoteServiceEvent unregisteredEvent = findEvent(TEST_SERVICE_CLASS, RemoteServiceEvent.UNREGISTERED);
		assertNotNull("Service Unregistered event not appear!", unregisteredEvent);
		assertNotNull(unregisteredEvent.getService());
		assertEquals(registeredEvent.getService().getServiceId(), unregisteredEvent.getService().getServiceId());
		assertEquals(registeredEvent.getService().getObjectClass(), unregisteredEvent.getService().getObjectClass());

		removeRemoteServiceListener(this);
		events.clear();
		bundle1.start(0);
		bundle1.stop(0);
		sleep(SLEEP_INTERVAL);
		assertTrue("Unexpected event(s) appear(s)!", events.size() == 0);
		bundle1.uninstall();
		bundle2.uninstall();
	}

	public static void assertEquals(Object[] expected, Object[] actual) {
		if (expected == actual)
			return;
		if (expected == null || actual == null)
			throw new AssertionFailedError("Expected " + expected + ", but was " + actual);
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < actual.length; i++) {
			assertEquals(expected[i], actual[i]);
		}

	}

	private void sleep(long time) {
		synchronized (sleeper) {
			if (events.size() > 0)
				return;
			try {
				sleeper.wait(time);
			} catch (InterruptedException e) {
			}
		}
	}

	public void serviceChanged(RemoteServiceEvent event) {
		synchronized (sleeper) {
			events.add(event);
			sleeper.notifyAll();
		}
	}

	private boolean isServicePresent(String clazz, String filter) throws IAgentException {
		return findService(getAllRemoteServices(clazz, filter)) != null;
	}

	private RemoteServiceEvent findEvent(String clazz, int type) throws IAgentException {
		synchronized (sleeper) {
			for (int j = 0; j < events.size(); j++) {
				RemoteServiceEvent event = (RemoteServiceEvent) events.get(j);
				if (event.getType() == type) {
					String[] clazzs = event.getService().getObjectClass();
					for (int i = 0; i < clazzs.length; i++) {
						if (clazz.equals(clazzs[i])) {
							return event;
						}
					}
				}
			}
		}
		return null;
	}

	private RemoteService findService(RemoteService[] services) throws IAgentException {
		if (services != null) {
			for (int j = 0; j < services.length; j++) {
				String[] clazzs = services[j].getObjectClass();
				for (int i = 0; i < clazzs.length; i++) {
					if (TEST_SERVICE_CLASS.equals(clazzs[i])) {
						return services[j];
					}
				}
			}
		}
		return null;
	}

}
