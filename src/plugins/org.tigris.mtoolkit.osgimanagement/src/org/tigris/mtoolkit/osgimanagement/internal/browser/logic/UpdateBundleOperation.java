/*******************************************************************************
 * Copyright (c) 2005, 2012 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.osgimanagement.internal.browser.logic;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.osgi.util.NLS;
import org.tigris.mtoolkit.common.FileUtils;
import org.tigris.mtoolkit.common.android.AndroidUtils;
import org.tigris.mtoolkit.common.certificates.CertUtils;
import org.tigris.mtoolkit.common.installation.ProgressInputStream;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.osgimanagement.Util;
import org.tigris.mtoolkit.osgimanagement.internal.FrameworkPlugin;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.Bundle;
import org.tigris.mtoolkit.osgimanagement.model.Framework;

public class UpdateBundleOperation extends RemoteBundleOperation {
  private final File bundleFile;

  public UpdateBundleOperation(Bundle bundle, File bundleFile) {
    super(Messages.update_bundle, bundle);
    this.bundleFile = bundleFile;
  }

  // This job is scheduled after preverification completes successfully, so
  // there is no need to check preconditions here.

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.internal.browser.logic.RemoteBundleOperation#doOperation(org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  protected IStatus doOperation(IProgressMonitor monitor) throws IAgentException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    File preparedFile = null;
    InputStream pis = null;
    try {
      Framework framework = getBundle().findFramework();
      String transportType = (String) framework.getConnector().getProperties().get(DeviceConnector.TRANSPORT_TYPE);

      // converting to dex
      if ("android".equals(transportType) && !AndroidUtils.isConvertedToDex(bundleFile)) {
        File convertedFile = new File(FrameworkPlugin.getDefault().getStateLocation() + "/dex/" + bundleFile.getName());
        convertedFile.getParentFile().mkdirs();
        AndroidUtils.convertToDex(bundleFile, convertedFile, subMonitor.newChild(10));
        preparedFile = convertedFile;
      }
      // signing
      File signedFile = new File(FrameworkPlugin.getDefault().getStateLocation() + "/signed/" + bundleFile.getName());
      signedFile.getParentFile().mkdirs();
      if (signedFile.exists()) {
        signedFile.delete();
      }
      IStatus status = CertUtils.signJar(preparedFile != null ? preparedFile : bundleFile, signedFile, subMonitor.newChild(10),
          framework.getSigningProperties());
      if (status.matches(IStatus.ERROR)) {
        if (CertUtils.continueWithoutSigning(status.getMessage())) {
          signedFile.delete();
        } else {
          return Status.CANCEL_STATUS;
        }
      }
      if (signedFile.exists()) {
        if (preparedFile != null) {
          preparedFile.delete();
        }
        preparedFile = signedFile;
      }

      File updateFile = preparedFile != null ? preparedFile : bundleFile;
      RemoteBundle rBundle = getBundle().getRemoteBundle();

      SubMonitor mon = subMonitor.newChild(80);
      mon.beginTask(Messages.update_bundle, (int) updateFile.length());

      pis = new ProgressInputStream(new FileInputStream(updateFile), mon);
      rBundle.update(pis);
      rBundle.refreshPackages();

      getBundle().refreshTypeFromRemote();

      return status;
    } catch (IOException ioe) {
      return Util.newStatus(IStatus.ERROR, "Failed to update bundle", ioe);
    } finally {
      FileUtils.close(pis);
      if (preparedFile != null) {
        preparedFile.delete();
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.internal.browser.logic.RemoteBundleOperation#getMessage(org.eclipse.core.runtime.IStatus)
   */
  @Override
  protected String getMessage(IStatus operationStatus) {
    return NLS.bind(Messages.bundle_update_failure, operationStatus);
  }
}