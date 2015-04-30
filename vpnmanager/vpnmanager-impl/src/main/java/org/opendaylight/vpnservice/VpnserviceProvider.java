/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice;

import java.util.Collections;

import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnserviceProvider implements BindingAwareProvider,
                                                       AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnserviceProvider.class);
    private VpnInterfaceManager vpnInterfaceManager;
    private VpnManager vpnManager;
    private IBgpManager bgpManager;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("VpnserviceProvider Session Initiated");
        try {
            final  DataBroker dataBroker = session.getSALService(DataBroker.class);
            vpnManager = new VpnManager(dataBroker, bgpManager);
            vpnInterfaceManager = new VpnInterfaceManager(dataBroker, bgpManager);
            vpnInterfaceManager.setMdsalManager(mdsalManager);
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

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    @Override
    public void close() throws Exception {
        vpnManager.close();
        vpnInterfaceManager.close();
    }
}
