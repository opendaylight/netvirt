/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ServiceProvider implements BindingAwareProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceProvider.class);
    private IInterfaceManager interfaceManager;
    private IMdsalApiManager mdsalManager;
    private OdlInterfaceRpcService interfaceManagerRpc;
    private NeutronPortChangeListener portListener;
    private NeutronSubnetChangeListener subnetListener;
    private NeutronRouterChangeListener routerListener;
    private Ipv6ServiceInterfaceEventListener ipv6ServiceInterfaceEventListener;

    private DataBroker broker;

    public Ipv6ServiceProvider() {
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        broker = session.getSALService(DataBroker.class);
        portListener = new NeutronPortChangeListener(broker);
        subnetListener = new NeutronSubnetChangeListener(broker);
        routerListener = new NeutronRouterChangeListener(broker);
        ipv6ServiceInterfaceEventListener = new Ipv6ServiceInterfaceEventListener(broker);
        ipv6ServiceInterfaceEventListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);
        LOG.info("IPv6 Service Initiated");
    }

    @Override
    public void close() throws Exception {
        portListener.close();
        subnetListener.close();
        routerListener.close();
        ipv6ServiceInterfaceEventListener.close();
        LOG.info("IPv6 Service closed");
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setInterfaceManagerRpcService(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManagerRpc = interfaceManagerRpc;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }
}
