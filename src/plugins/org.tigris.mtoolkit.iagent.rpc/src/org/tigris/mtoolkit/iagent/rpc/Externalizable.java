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
package org.tigris.mtoolkit.iagent.rpc;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface for custum serialization. Classes that implements this interface
 * must have public emty constructor so deserialization can be done.
 */
public interface Externalizable {

	/**
	 * Use this method for serialization of object state.
	 */
	public void writeObject(OutputStream oStream) throws Exception;

	/**
	 * Use this method to deserializion of object state.
	 */
	public void readObject(InputStream iStream) throws Exception;

}
