/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.fibmanager.api.IFibManager;
import org.opendaylight.vpnmanager.api.IVpnManager;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnserviceProvider implements BindingAwareProvider, IVpnManager,
                                                       AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnserviceProvider.class);
    private VpnInterfaceManager vpnInterfaceManager;
    private VpnManager vpnManager;
    private IBgpManager bgpManager;
    private IFibManager fibManager;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private IdManagerService idManager;
    private InterfaceChangeListener interfaceListener;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("VpnserviceProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            vpnManager = new VpnManager(dataBroker, bgpManager);
            vpnManager.setIdManager(idManager);
            vpnInterfaceManager = new VpnInterfaceManager(dataBroker, bgpManager);
            vpnInterfaceManager.setMdsalManager(mdsalManager);
            vpnInterfaceManager.setInterfaceManager(interfaceManager);
            vpnInterfaceManager.setIdManager(idManager);
            vpnManager.setVpnInterfaceManager(vpnInterfaceManager);
            interfaceListener = new InterfaceChangeListener(dataBroker, vpnInterfaceManager);
            interfaceListener.setInterfaceManager(interfaceManager);
            createIdPool();
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    public void setBgpManager(IBgpManager bgpManager) {
        LOG.debug("BGP Manager reference initialized");
        this.bgpManager = bgpManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setFibManager(IFibManager fibManager) {
        this.fibManager = fibManager;
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
            .setIdStart(VpnConstants.VPN_IDPOOL_START)
            .setPoolSize(new BigInteger(VpnConstants.VPN_IDPOOL_SIZE))
            .build();
        try {
           Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
           if ((result != null) && (result.get().isSuccessful())) {
                LOG.debug("Created IdPool for VPN Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for VPN Service",e);
        }
    }

    @Override
    public void close() throws Exception {
        vpnManager.close();
        vpnInterfaceManager.close();
        interfaceListener.close();
    }

    @Override
    public Collection<BigInteger> getDpnsForVpn(long vpnId) {
        return vpnInterfaceManager.getDpnsForVpn(vpnId);
    }

    @Override
    public void setFibService(IFibManager fibManager) {
        LOG.debug("Fib service reference is initialized in VPN Manager");
        this.fibManager = fibManager;
        vpnInterfaceManager.setFibManager(fibManager);
    }
}
