/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import io.netty.util.concurrent.GlobalEventExecutor;
import java.math.BigInteger;
import java.util.List;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManagerImpl implements IFibManager {

  private static final Logger LOG = LoggerFactory.getLogger(FibManagerImpl.class);

  private final VfrEntryListener vfrEntryListener;
  private IVpnManager vpnmanager;
  private final NexthopManager nexthopManager;

    public FibManagerImpl(final NexthopManager nexthopManager,
            final VfrEntryListener vfrEntryListener,
            final BundleContext bundleContext) {
        this.nexthopManager = nexthopManager;
        this.vfrEntryListener = vfrEntryListener;

        GlobalEventExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
                final WaitingServiceTracker<IVpnManager> tracker = WaitingServiceTracker.create(
                        IVpnManager.class, bundleContext);
                vpnmanager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);
                LOG.info("FibManagerImpl initialized. IVpnManager={}", vpnmanager);
            }
        });
    }

  @Override
  public void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd) {
    vfrEntryListener.populateFibOnNewDpn(dpnId, vpnId, rd);
  }

  @Override
  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd) {
    vfrEntryListener.cleanUpDpnForVpn(dpnId, vpnId, rd);
  }

  @Override
  public List<String> printFibEntries() {
    return vfrEntryListener.printFibEntries();
  }

  //Temp
  @Override
  public void addStaticRoute(String prefix, String nextHop, String rd, int label) {
    this.vpnmanager.addExtraRoute(prefix, nextHop, rd, null, label);
  }

  @Override
  public void deleteStaticRoute(String prefix, String rd) {
    this.vpnmanager.delExtraRoute(prefix, rd, null);
  }

  @Override
  public void setConfTransType(String service, String transportType) {
    this.nexthopManager.setConfTransType(service, transportType);
  }

  @Override
  public void writeConfTransTypeConfigDS() {
    this.nexthopManager.writeConfTransTypeConfigDS();
  }

  @Override
  public String getConfTransType() {
    return this.nexthopManager.getConfiguredTransportTypeL3VPN().toString();
  }

  @Override
  public String getReqTransType() {
    return this.nexthopManager.getReqTransType();
  }

  @Override
  public boolean isVPNConfigured() {
    return this.vpnmanager.isVPNConfigured();
  }
}