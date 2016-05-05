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
import org.opendaylight.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManagerProvider implements BindingAwareProvider, IFibManager, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FibManagerProvider.class);

  private FibManager fibManager;
  private IMdsalApiManager mdsalManager;
  private IVpnManager vpnmanager;
  private NexthopManager nexthopManager;
  private IdManagerService idManager;
  private ItmRpcService itmManager;
  private OdlInterfaceRpcService interfaceManager;
  private FibNodeCapableListener fibNcListener;
  private RpcProviderRegistry rpcProviderRegistry;
  private RpcRegistration<FibRpcService> rpcRegistration;

  @Override
  public void onSessionInitiated(ProviderContext session) {
    LOG.info("FibManagerProvider Session Initiated");
    try {
      final  DataBroker dataBroker = session.getSALService(DataBroker.class);
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
    fibManager.close();
    fibNcListener.close();
  }

  public void setMdsalManager(IMdsalApiManager mdsalManager) {
    this.mdsalManager = mdsalManager;
  }

  public void setVpnmanager(IVpnManager vpnmanager) {
    this.vpnmanager = vpnmanager;
    vpnmanager.setFibService(this);
  }

  public void setIdManager(IdManagerService idManager) {
    this.idManager = idManager;
  }

  public void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
    this.interfaceManager = interfaceManager;
  }

  public void setITMProvider(ItmRpcService itmManager) {
    this.itmManager = itmManager;
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

  public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
    this.rpcProviderRegistry = rpcProviderRegistry;
  }

  private RpcProviderRegistry getRpcProviderRegistry() {
    return rpcProviderRegistry;
  }

}
