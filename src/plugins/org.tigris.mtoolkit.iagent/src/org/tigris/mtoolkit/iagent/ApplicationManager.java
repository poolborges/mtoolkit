package org.tigris.mtoolkit.iagent;

import org.tigris.mtoolkit.iagent.event.RemoteApplicationListener;

public interface ApplicationManager {

	/**
	 * Add a listener which will be notified whenever application event is
	 * generated on the remote site. Adding the same listener twice doesn't have
	 * any effect
	 * 
	 * @param listener
	 *            the listener which will be notified for remote application
	 *            events
	 * @throws IAgentException
	 * @see {@link RemoteApplicationEvent}
	 */
	void addRemoteApplicationListener(RemoteApplicationListener listener) throws IAgentException;

	/**
	 * Removes a listener from the listener list. This means that the listener
	 * won't be notified for remote application events anymore.
	 * 
	 * @param listener
	 *            the listener to be removed
	 * @throws IAgentException
	 *             if the device is already disconnected
	 */
	void removeRemoteApplicationListener(RemoteApplicationListener listener) throws IAgentException;

	/**
	 * Returns all installed applications currently available in the runtime.
	 * The returned array contains RemoteApplication objects, which can be used
	 * to start or stop the underling application.
	 * 
	 * @return array with RemoteApplication objects. The result is never null
	 * @throws IAgentException
	 */
	RemoteApplication[] listApplications() throws IAgentException;

}