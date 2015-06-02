/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager;

import java.math.BigInteger;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.fibmanager.api.IFibManager;
import org.opendaylight.vpnmanager.api.IVpnManager;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.L3nexthopService;
import org.opendaylight.yangtools.yang.binding.RpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibManagerProvider implements BindingAwareProvider, IFibManager, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FibManagerProvider.class);

  private FibManager fibManager;
  private IMdsalApiManager mdsalManager;
  private IVpnManager vpnmanager;
  //private FibNodeCapableListener fibNcListener;

  @Override
  public void onSessionInitiated(ProviderContext session) {
    LOG.info("FibManagerProvider Session Initiated");
    try {
      final  DataBroker dataBroker = session.getSALService(DataBroker.class);
      final RpcService nexthopService = session.getRpcService(L3nexthopService.class);
      fibManager = new FibManager(dataBroker, nexthopService);
      fibManager.setMdsalManager(mdsalManager);
      fibManager.setVpnmanager(vpnmanager);
      //fibNcListener = new FibNodeCapableListener(dataBroker, fibManager);
    } catch (Exception e) {
      LOG.error("Error initializing services", e);
    }
  }

  @Override
  public void close() throws Exception {
    LOG.info("FibManagerProvider Closed");
    fibManager.close();
    //fibNcListener.close();
  }

  public void setMdsalManager(IMdsalApiManager mdsalManager) {
    this.mdsalManager = mdsalManager;
  }

  public void setVpnmanager(IVpnManager vpnmanager) {
    this.vpnmanager = vpnmanager;
    vpnmanager.setFibService(this);
  }

  @Override
  public void populateFibOnNewDpn(BigInteger dpnId, long vpnId, String rd) {
    fibManager.populateFibOnNewDpn(dpnId, vpnId, rd);
  }

  @Override
  public void cleanUpDpnForVpn(BigInteger dpnId, long vpnId, String rd) {
    fibManager.cleanUpDpnForVpn(dpnId, vpnId, rd);
  }
}
