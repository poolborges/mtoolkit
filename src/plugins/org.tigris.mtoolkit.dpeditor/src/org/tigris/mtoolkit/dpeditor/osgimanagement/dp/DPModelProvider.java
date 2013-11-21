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
package org.tigris.mtoolkit.dpeditor.osgimanagement.dp;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.ui.statushandlers.StatusManager;
import org.tigris.mtoolkit.dpeditor.DPActivator;
import org.tigris.mtoolkit.dpeditor.osgimanagement.dp.model.DeploymentPackage;
import org.tigris.mtoolkit.iagent.DeploymentManager;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteDP;
import org.tigris.mtoolkit.iagent.event.RemoteDPEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDPListener;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener;
import org.tigris.mtoolkit.iagent.rpc.Capabilities;
import org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider;
import org.tigris.mtoolkit.osgimanagement.Util;
import org.tigris.mtoolkit.osgimanagement.model.Framework;
import org.tigris.mtoolkit.osgimanagement.model.Model;
import org.tigris.mtoolkit.osgimanagement.model.SimpleNode;

public final class DPModelProvider implements ContentTypeModelProvider, RemoteDPListener, RemoteDevicePropertyListener {
  private SimpleNode                                       dpNode;
  private DeviceConnector                                  connector;
  private Framework                                        fw;
  private DeploymentManager                                manager;
  private volatile boolean                                 supportDP;
  //FIXME This might cause memory leaks in some cases
  public static final Dictionary<DeviceConnector, Boolean> supportDPDictionary = new Hashtable<DeviceConnector, Boolean>();

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider#connect(org.tigris.mtoolkit.osgimanagement.model.Framework, org.eclipse.core.runtime.IProgressMonitor)
   */
  public void connect(Framework fw, IProgressMonitor monitor) {
    this.fw = fw;
    this.connector = fw.getConnector();
    supportDP = isDpSupported(connector);
    supportDPDictionary.put(connector, Boolean.valueOf(supportDP));
    try {
      connector.addRemoteDevicePropertyListener(this);
    } catch (IAgentException e1) {
      e1.printStackTrace();
    }
    if (supportDP) {
      initModel(monitor);
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider#disconnect()
   */
  public void disconnect() {
    if (manager != null) {
      try {
        manager.removeRemoteDPListener(this);
      } catch (IAgentException e) {
        e.printStackTrace();
      }
    }
    if (fw != null) {
      if (dpNode != null) {
        fw.removeElement(dpNode);
      }
      fw = null;
    }
    if (connector != null) {
      supportDPDictionary.remove(connector);
      try {
        connector.removeRemoteDevicePropertyListener(this);
      } catch (IAgentException e1) {
        e1.printStackTrace();
      }
      connector = null;
    }
    dpNode = null;
    supportDP = false;
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider#switchView(int)
   */
  public Model switchView(int viewType) {
    Model node = null;
    if (supportDP) {
      if (viewType == Framework.BUNDLES_VIEW) {
        fw.addElement(dpNode);
        node = dpNode;
      } else if (viewType == Framework.SERVICES_VIEW) {
        fw.removeElement(dpNode);
      }
    }
    return node;
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.event.RemoteDPListener#deploymentPackageChanged(org.tigris.mtoolkit.iagent.event.RemoteDPEvent)
   */
  public void deploymentPackageChanged(final RemoteDPEvent e) {
    synchronized (fw.getLockObject()) {
      try {
        RemoteDP remoteDP = e.getDeploymentPackage();
        if (e.getType() == RemoteDPEvent.INSTALLED) {
          Model dpNodeRoot = dpNode;
          try {
            // check if this install actually is update
            DeploymentPackage dp = findDP(dpNodeRoot, remoteDP.getName());
            if (dp != null) {
              dpNode.removeElement(dp);
            }
            DeploymentPackage dpNode = new DeploymentPackage(remoteDP, fw);
            dpNodeRoot.addElement(dpNode);
          } catch (IAgentException e1) {
            if (e1.getErrorCode() != IAgentErrors.ERROR_DEPLOYMENT_STALE
                && e1.getErrorCode() != IAgentErrors.ERROR_REMOTE_ADMIN_NOT_AVAILABLE) {
              StatusManager.getManager().handle(new Status(IStatus.ERROR, DPActivator.PLUGIN_ID, e1.getMessage(), e1));
            }
          }
        } else if (e.getType() == RemoteDPEvent.UNINSTALLED) {
          if (remoteDP != null) {
            // there are cases where the dp failed to be added,
            // because it was too quickly uninstalled/updated
            DeploymentPackage dp = findDP(dpNode, remoteDP.getName());
            if (dp != null) {
              dpNode.removeElement(dp);
            }
          }
        }
        dpNode.updateElement();
      } catch (IllegalStateException ex) {
        // ignore state exceptions, which usually indicates that something
        // is was fast enough to disappear
      } catch (Throwable t) {
        StatusManager.getManager().handle(new Status(IStatus.ERROR, DPActivator.PLUGIN_ID, t.getMessage(), t));
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener#devicePropertiesChanged(org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent)
   */
  public void devicePropertiesChanged(RemoteDevicePropertyEvent e) throws IAgentException {
    if (connector == null) {
      return;
    }
    if (e.getType() == RemoteDevicePropertyEvent.PROPERTY_CHANGED_TYPE) {
      Object property = e.getProperty();
      if (Capabilities.DEPLOYMENT_SUPPORT.equals(property)) {
        boolean enabled = ((Boolean) e.getValue()).booleanValue();
        supportDPDictionary.put(connector, Boolean.valueOf(enabled));
        if (enabled) {
          supportDP = true;
          initModel(new NullProgressMonitor());
        } else {
          supportDP = false;
          try {
            manager.removeRemoteDPListener(this);
          } catch (IAgentException ex) {
            ex.printStackTrace();
          }
          if (dpNode != null) {
            dpNode.removeChildren();
            fw.removeElement(dpNode);
            dpNode = null;
          }
        }
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider#getResource(java.lang.String, java.lang.String, org.tigris.mtoolkit.osgimanagement.model.Framework)
   */
  public Model getResource(String id, String version, Framework fw) throws IAgentException {
    return null;
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider#getSupportedMimeTypes()
   */
  public String[] getSupportedMimeTypes() {
    return null;
  }

  private void initModel(IProgressMonitor monitor) {
    dpNode = new SimpleNode("Deployment Packages");
    if (fw.getViewType() == Framework.BUNDLES_VIEW) {
      fw.addElement(dpNode);
    }
    try {
      manager = connector.getDeploymentManager();
      addDPs(monitor);
      try {
        manager.addRemoteDPListener(this);
      } catch (IAgentException e) {
        e.printStackTrace();
      }
    } catch (IAgentException e) {
      e.printStackTrace();
    }
  }

  private void addDPs(IProgressMonitor monitor) throws IAgentException {
    if (!supportDP) {
      return;
    }
    Model deplPackagesNode = dpNode;
    RemoteDP dps[] = null;
    dps = connector.getDeploymentManager().listDeploymentPackages();
    if (dps != null && dps.length > 0) {
      SubMonitor sMonitor = SubMonitor.convert(monitor, dps.length);
      sMonitor.setTaskName("Retrieving deployment packages information...");
      for (int i = 0; i < dps.length; i++) {
        DeploymentPackage dpNode = new DeploymentPackage(dps[i], fw);
        deplPackagesNode.addElement(dpNode);
        monitor.worked(1);
        if (monitor.isCanceled()) {
          return;
        }
      }
    }
  }

  public static DeploymentPackage findDP(Model dpNode, String name) {
    Model[] dps = dpNode.getChildren();
    for (int i = 0; i < dps.length; i++) {
      if (dps[i].getName().equals(name)) {
        return (DeploymentPackage) dps[i];
      }
    }
    return null;
  }

  public static boolean isDpSupported(DeviceConnector connector) {
    if (connector == null) {
      return false;
    }
    Framework fw = Util.findFramework(connector);
    if (fw == null) {
      return false;
    }
    Dictionary remoteProperties = fw.getRemoteDeviceProperties();
    if (remoteProperties == null) {
      return false;
    }
    Object support = remoteProperties.get(Capabilities.DEPLOYMENT_SUPPORT);
    if (support != null && Boolean.parseBoolean((support.toString()))) {
      return true;
    }
    return false;
  }
}
