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
package org.tigris.mtoolkit.iagent.internal.rpc.console;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.tigris.mtoolkit.iagent.internal.pmp.InvocationThread;
import org.tigris.mtoolkit.iagent.internal.rpc.Activator;
import org.tigris.mtoolkit.iagent.internal.utils.CircularBuffer;
import org.tigris.mtoolkit.iagent.pmp.EventListener;
import org.tigris.mtoolkit.iagent.pmp.PMPConnection;
import org.tigris.mtoolkit.iagent.pmp.PMPException;
import org.tigris.mtoolkit.iagent.pmp.RemoteMethod;
import org.tigris.mtoolkit.iagent.pmp.RemoteObject;
import org.tigris.mtoolkit.iagent.rpc.Capabilities;
import org.tigris.mtoolkit.iagent.rpc.RemoteCapabilitiesManager;
import org.tigris.mtoolkit.iagent.rpc.RemoteConsole;

public abstract class RemoteConsoleServiceBase implements RemoteConsole {

	private Map dispatchers = new HashMap();
	private ServiceRegistration registration;

	private PrintStream oldSystemOut;
	private PrintStream oldSystemErr;
	private PrintStream newSystemStream = new PrintStream(new RedirectedSystemOutput());

	private boolean replacedSystemOutputs = false;

	private EventListener closeConnectionHandler = new EventListener() {
		public void event(Object event, String evType) {
			if (PMPConnection.FRAMEWORK_DISCONNECTED.equals(evType)) {
				doReleaseConsole((PMPConnection) event);
			}
		}
	};

	public void register(BundleContext context) {
		registration = context.registerService(RemoteConsole.class.getName(), this, null);

		RemoteCapabilitiesManager capMan = Activator.getCapabilitiesManager();
		if (capMan != null) {
			capMan.setCapability(Capabilities.CONSOLE_SUPPORT, new Boolean(true));
		}
	}

	public void unregister() {
		registration.unregister();
		restoreSystemOutputs();

		RemoteCapabilitiesManager capMan = Activator.getCapabilitiesManager();
		if (capMan != null) {
			capMan.setCapability(Capabilities.CONSOLE_SUPPORT, new Boolean(false));
		}
	}

	public void registerOutput(RemoteObject remoteObject) throws PMPException {
		PMPConnection conn = InvocationThread.getContext().getConnection();
		WriteDispatcher dispatcher = createDispatcher(conn, new CircularBuffer(), remoteObject);
		dispatcher.start();
		conn.addEventListener(closeConnectionHandler, new String[] { PMPConnection.FRAMEWORK_DISCONNECTED });
		synchronized (dispatchers) {
			WriteDispatcher oldDispatcher = (WriteDispatcher) dispatchers.put(conn, dispatcher);
			if (oldDispatcher != null)
				oldDispatcher.finish();
			replaceSystemOutputs();
		}
	}

	protected WriteDispatcher createDispatcher(PMPConnection conn, CircularBuffer buffer, RemoteObject remoteObject)
					throws PMPException {
		return new WriteDispatcher(conn, buffer, remoteObject);
	}

	public synchronized void releaseConsole() {
		PMPConnection conn = InvocationThread.getContext().getConnection();
		doReleaseConsole(conn);
	}

	private void doReleaseConsole(PMPConnection conn) {
		synchronized (dispatchers) {
			WriteDispatcher dispatcher = (WriteDispatcher) dispatchers.remove(conn);
			if (dispatcher != null)
				dispatcher.finish();
			if (dispatchers.size() == 0)
				restoreSystemOutputs();
		}
	}

	protected void print(String msg) {
		// TODO: Handle different encodings
		byte[] msgBytes = msg.getBytes();
		print(msgBytes, 0, msgBytes.length);
	}

	protected void print(byte[] buf, int off, int len) {
		PMPConnection conn = InvocationThread.getContext().getConnection();
		WriteDispatcher dispatcher;
		synchronized (dispatchers) {
			dispatcher = (WriteDispatcher) dispatchers.get(conn);
			if (dispatcher == null)
				return;
		}
		dispatcher.buffer.write(buf, off, len);
		synchronized (dispatcher) {
			dispatcher.notifyAll();
		}
	}

	protected synchronized void replaceSystemOutputs() {
		if (replacedSystemOutputs)
			return;
		oldSystemOut = System.out;
		oldSystemErr = System.err;
		System.setOut(newSystemStream);
		System.setErr(newSystemStream);
		replacedSystemOutputs = true;
	}

	protected synchronized void restoreSystemOutputs() {
		if (replacedSystemOutputs) {
			if (System.out == newSystemStream)
				System.setOut(oldSystemOut);
			if (System.err == newSystemStream)
				System.setErr(oldSystemErr);
			replacedSystemOutputs = false;
		}
	}

	private class RedirectedSystemOutput extends OutputStream {

		private byte[] singleByte = new byte[1];

		public synchronized void write(byte[] var0, int var1, int var2) throws IOException {
			oldSystemOut.write(var0, var1, var2);
			synchronized (dispatchers) {
				for (Iterator it = dispatchers.values().iterator(); it.hasNext();) {
					WriteDispatcher dispatcher = (WriteDispatcher) it.next();
					dispatcher.buffer.write(var0, var1, var2);
					synchronized (dispatcher) {
						dispatcher.notifyAll();
					}
				}
			}
		}

		public synchronized void write(byte[] var0) throws IOException {
			write(var0, 0, var0.length);
		}

		public synchronized void write(int arg0) throws IOException {
			singleByte[0] = (byte) (arg0 & 0xFF);
			write(singleByte, 0, 1);
		}

		public synchronized void flush() throws IOException {
			oldSystemOut.flush();
		}

	}

	protected WriteDispatcher getDispatcher(PMPConnection conn) {
		return (WriteDispatcher) dispatchers.get(conn);
	}

	protected class WriteDispatcher extends Thread {

		public PMPConnection conn;
		public CircularBuffer buffer;
		public RemoteMethod method;
		public RemoteObject object;

		private volatile boolean running = true;

		public WriteDispatcher(PMPConnection conn, CircularBuffer buffer, RemoteObject object) throws PMPException {
			super("Remote Console Dispatcher");
			this.conn = conn;
			this.buffer = buffer;
			this.object = object;
			method = object.getMethod("write", new String[] { byte[].class.getName(),
				Integer.TYPE.getName(),
				Integer.TYPE.getName() });
		}

		public void run() {
			while (true) {
				synchronized (this) {
					while (buffer.available() <= 0 && running && conn.isConnected())
						try {
							wait();
						} catch (InterruptedException e) {
						}
				}
				if (!running || !conn.isConnected()) {
					try {
						object.dispose();
					} catch (PMPException e) {
						// TODO: Log exception
					}
					return;
				}
				byte[] buf = new byte[1024];
				while (buffer.available() > 0) {
					int read = buffer.read(buf);
					try {
						method.invoke(new Object[] { buf, new Integer(0), new Integer(read) }, true);
					} catch (PMPException e) {
						// TODO: Log exception
					}
				}
			}
		}

		public synchronized void finish() {
			running = false;
			notifyAll();
		}
	}
}
