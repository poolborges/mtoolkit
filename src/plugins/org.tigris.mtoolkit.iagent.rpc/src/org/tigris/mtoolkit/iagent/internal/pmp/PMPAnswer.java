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
package org.tigris.mtoolkit.iagent.internal.pmp;

import java.io.IOException;

import org.tigris.mtoolkit.iagent.internal.rpc.Messages;
import org.tigris.mtoolkit.iagent.pmp.PMPException;

final class PMPAnswer {
  public boolean               connected     = false; // ok - connect
  protected int                objID         = -1;   // ok - getReference, invoke
  protected int                methodID      = -1;   // ok - getMethod
  protected String             returnType    = null; // ok - getMethod
  protected RemoteMethodImpl[] methods       = null;
  protected Object             obj           = null;
  protected ClassLoader        loader        = null;
  protected String             errMsg        = null;
  protected Throwable          errCause      = null;

  public boolean               success       = false;
  protected Connection         connection;
  protected RemoteObjectImpl   requestingRObj;       // ok
  protected boolean            expectsReturn = false; // ok

  protected boolean            received      = false;
  protected boolean            waiting       = false;

  private PMPSessionThread     c;

  protected PMPAnswer(PMPSessionThread c) {
    this.c = c;
  }

  public void free() {
    connected = false;
    objID = -1;
    methodID = -1;
    returnType = null;
    methods = null;
    obj = null;
    errMsg = null;
    errCause = null;

    success = false;
    expectsReturn = false;

    received = true;
    waiting = false;
  }

  public synchronized void finish() {
    received = true;
    if (waiting) {
      notify();
    }
  }

  public void get(int timeout) throws IOException {
    long time = System.currentTimeMillis();
    synchronized (this) {
      while (!received) {
        waiting = true;
        try {
          if (timeout > 0) {
            wait(timeout);
          } else {
            wait();
            break;
          }
        } catch (Exception ignore) {
        }
        if (!received && (timeout > 0 && (System.currentTimeMillis() - time) > timeout)) {
          break;
        }
      }
    }
    if (!received) {
      c.disconnect(Messages.getString("PMPAnswer_ConnLostErr"), true); //$NON-NLS-1$
      throw new IOException(Messages.getString("PMPAnswer_ConnLostErr")); //$NON-NLS-1$
    }
  }

  public String toString() {
    return "PMPAnswer --->>> " + c + " : " + c.hashCode(); //$NON-NLS-1$ //$NON-NLS-2$
  }

  public static PMPException createException(String errMsg, Throwable cause) {
    return (cause == null ? new PMPException(errMsg) : new PMPException(errMsg, cause));
  }
}
