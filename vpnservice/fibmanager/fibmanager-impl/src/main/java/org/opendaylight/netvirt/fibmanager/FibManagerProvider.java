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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManagerProvider implements IFibManager, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FibManagerProvider.class);

  private FibManager fibManager;
  private final IMdsalApiManager mdsalManager;
  private final IVpnManager vpnmanager;
  private NexthopManager nexthopManager;
  private final IdManagerService idManager;
  private final ItmRpcService itmManager;
  private final OdlInterfaceRpcService interfaceManager;
  private FibNodeCapableListener fibNcListener;
  private final RpcProviderRegistry rpcProviderRegistry;
  private RpcRegistration<FibRpcService> rpcRegistration;
  private final DataBroker dataBroker;


  public FibManagerProvider(final DataBroker dataBroker,
                            final RpcProviderRegistry rpcProviderRegistry,
                            final IMdsalApiManager mdsalApiManager,
                            final IVpnManager vpnManager,
                            final IdManagerService idManager,
                            final OdlInterfaceRpcService interfaceManager,
                            final ItmRpcService itmManager) {
      this.dataBroker = dataBroker;
      this.rpcProviderRegistry = rpcProviderRegistry;
      this.mdsalManager = mdsalApiManager;
      this.vpnmanager = vpnManager;
      this.idManager = idManager;
      this.interfaceManager = interfaceManager;
      this.itmManager = itmManager;
}

  public void start() {
    LOG.info("FibManagerProvider Session Initiated");
    try {
      vpnmanager.setFibService(this);
      nexthopManager = new NexthopManager(dataBroker);
      nexthopManager.setMdsalManager(mdsalManager);
      nexthopManager.setIdManager(idManager);
      nexthopManager.setInterfaceManager(interfaceManager);
      nexthopManager.setITMRpcService(itmManager);
      fibManager = new FibManager(dataBroker);
      fibManager.setMdsalManager(mdsalManager);
      fibManager.setVpnmanager(vpnmanager);
      fibManager.setNextHopManager(nexthopManager);
      fibManager.setITMRpcService(itmManager);
      fibManager.setInterfaceManager(interfaceManager);
      fibManager.setIdManager(idManager);
      fibNcListener = new FibNodeCapableListener(dataBroker, fibManager);
      FibRpcService fibRpcService = new FibRpcServiceImpl(dataBroker, mdsalManager, this);
      rpcRegistration = getRpcProviderRegistry().addRpcImplementation(FibRpcService.class, fibRpcService);
    } catch (Exception e) {
      LOG.error("Error initializing services", e);
    }
  }

  @Override
  public void close() throws Exception {
    LOG.info("FibManagerProvider Closed");
    if (fibManager != null) {
        fibManager.close();
    }
    if (fibNcListener != null) {
        fibNcListener.close();
    }
    if (rpcRegistration != null) {
        rpcRegistration.close();
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

  private RpcProviderRegistry getRpcProviderRegistry() {
    return rpcProviderRegistry;
  }

  @Override
  public void setConfTransType(String service, String transportType)
  {
    this.nexthopManager.setConfTransType(service, transportType);
  }

  @Override
  public void writeConfTransTypeConfigDS()
  {
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
