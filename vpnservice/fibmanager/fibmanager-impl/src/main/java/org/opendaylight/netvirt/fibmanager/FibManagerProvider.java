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
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
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

  @Override
  public void addStaticRoute(String prefix, String nextHop, String rd, int label) {
    this.vpnmanager.addExtraRoute(prefix, nextHop, rd, null, label);
  }

  @Override
  public void deleteStaticRoute(String prefix, String nextHop, String rd) {
    this.vpnmanager.delExtraRoute(prefix, nextHop, rd, null);
  }

  public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
    this.rpcProviderRegistry = rpcProviderRegistry;
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
  @Override
  public String getTransportTypeStr(String tunType) {
    return this.nexthopManager.getTransportTypeStr(tunType);
  }

  @Override
  public void handleRemoteRoute(boolean action, BigInteger localDpnId,
                                BigInteger remoteDpnId, long vpnId,
                                String  rd, String destPrefix ,
                                String localNextHopIP,
                                String remoteNextHopIp) {
    fibManager.handleRemoteRoute( action, localDpnId, remoteDpnId,
                                  vpnId,rd, destPrefix,
                                  localNextHopIP, remoteNextHopIp);
  }
  @Override
  public void populateFibOnDpn(BigInteger localDpnId, long vpnId,
                               String rd, String localNextHopIp,
                               String remoteNextHopIp) {
    fibManager.populateFibOnDpn(localDpnId, vpnId, rd,
                                localNextHopIp, remoteNextHopIp);
  }

  @Override
  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId,
                               String rd, String localNextHopIp,
                               String remoteNextHopIp) {
    fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd,
                                localNextHopIp, remoteNextHopIp);
  }

  public void addOrUpdateFibEntry(DataBroker broker, String rd, String prefix, List<String> nextHopList,
                                  int label, RouteOrigin origin, WriteTransaction writeConfigTxn) {
    FibUtil.addOrUpdateFibEntry(broker, rd, prefix , nextHopList, label, origin, writeConfigTxn);
  }

  public void removeFibEntry(DataBroker broker, String rd, String prefix, WriteTransaction writeConfigTxn) {
    FibUtil.removeFibEntry(broker, rd, prefix, writeConfigTxn);
  }

  public void removeOrUpdateFibEntry(DataBroker broker, String rd, String prefix, String nextHopToRemove, WriteTransaction writeConfigTxn) {
    FibUtil.removeOrUpdateFibEntry(broker, rd, prefix, nextHopToRemove, writeConfigTxn);
  }

  public  void addVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
    FibUtil.addVrfTable(broker, rd, writeConfigTxn);
  }

  public void removeVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
    FibUtil.removeVrfTable(broker, rd, writeConfigTxn);
  }
}