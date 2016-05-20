/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import java.math.BigInteger;
import java.util.List;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManagerProvider implements IFibManager, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FibManagerProvider.class);

  private final FibManager fibManager;
  private final IVpnManager vpnmanager;
  private final NexthopManager nexthopManager;


  public FibManagerProvider(final IVpnManager vpnManager,
                            final NexthopManager nexthopManager,
                            final FibManager fibManager) {
      this.vpnmanager = vpnManager;
      this.nexthopManager = nexthopManager;
      this.fibManager = fibManager;
  }

  public void start() {
    LOG.info("FibManagerProvider Session Initiated");
      vpnmanager.setFibService(this);
  }

  @Override
  public void close() throws Exception {
    LOG.info("FibManagerProvider Closed");
    if (fibManager != null) {
        fibManager.close();
    }
  }

  @Override
  public void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd) {
    fibManager.populateFibOnNewDpn(dpnId, vpnId, rd);
  }

  @Override
  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd) {
    fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
  }

  @Override
  public List<String> printFibEntries() {
    return fibManager.printFibEntries();
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