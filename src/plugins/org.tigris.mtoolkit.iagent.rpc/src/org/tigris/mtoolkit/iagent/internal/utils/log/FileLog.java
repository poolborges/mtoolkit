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
package org.tigris.mtoolkit.iagent.internal.utils.log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import org.tigris.mtoolkit.iagent.internal.rpc.Messages;
import org.tigris.mtoolkit.iagent.util.DebugUtils;

public final class FileLog implements Log {
  private PrintWriter out;
  private final Object lock = new Object();

  public FileLog(File logFile) throws IOException {
    if (logFile == null) {
      throw new NullPointerException(Messages.getString("FileLog_NullFileErr")); //$NON-NLS-1$
    }
    out = new PrintWriter(new FileWriter(logFile.getAbsolutePath(), true));
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.internal.utils.log.Log#log(int, java.lang.String, java.lang.Throwable)
   */
  public void log(int severity, String msg, Throwable t) {
    synchronized (lock) {
      try {
        out.println(getDateTime() + " " + getSeverityString(severity) + msg); //$NON-NLS-1$
        if (t != null) {
          out.println(DebugUtils.getStackTrace(t));
        }
      } catch (Exception ex) {
        // logging failed
        ConsoleLog.getDefault().log(severity, msg, t);
      } finally {
        out.flush();
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.internal.utils.log.Log#close()
   */
  public void close() {
    out.close();
  }

  private static String getDateTime() {
    return new Date().toString();
  }

  private static String getSeverityString(int severity) {
    switch (severity) {
    case INFO:
      return "[I]"; //$NON-NLS-1$
    case ERROR:
      return "[E]"; //$NON-NLS-1$
    case DEBUG:
      return "[D]"; //$NON-NLS-1$
    default:
      return "[E]"; //$NON-NLS-1$
    }
  }
}
