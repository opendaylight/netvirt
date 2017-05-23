/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.math.BigInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.utils.IAclServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddInterfaceToDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveInterfaceFromDpnOnVpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.add._interface.to.dpn.on.vpn.event.AddInterfaceEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.remove._interface.from.dpn.on.vpn.event.RemoveInterfaceEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclVpnChangeListener implements OdlL3vpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(AclVpnChangeListener.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IAclServiceUtil aclServiceUtil;

    @Inject
    public AclVpnChangeListener(DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                IAclServiceUtil aclServiceUtil) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.aclServiceUtil = aclServiceUtil;
    }

    @PostConstruct
    public void init() {
        LOG.trace("Initializing singleton..");
    }

    @PreDestroy
    public void close() {
        LOG.trace("Destroying singleton...");
    }

    @Override
    public void onAddDpnEvent(AddDpnEvent notification) {
    }

    @Override
    public void onRemoveDpnEvent(RemoveDpnEvent notification) {
    }

    @Override
    public void onAddInterfaceToDpnOnVpnEvent(AddInterfaceToDpnOnVpnEvent notification) {
        AddInterfaceEventData data = notification.getAddInterfaceEventData();
        String interfaceName = data.getInterfaceName();
        Long vpnId = data.getVpnId();
        BigInteger dpnId = data.getDpnId();
        LOG.trace("Processing vpn interface {} addition", interfaceName);
        aclServiceUtil.updateBoundServicesFlow(interfaceName, vpnId);
        aclServiceUtil.updateRemoteAclFilterTable(interfaceName, vpnId, dpnId, 0/*ADD*/);

    }

    @Override
    public void onRemoveInterfaceFromDpnOnVpnEvent(RemoveInterfaceFromDpnOnVpnEvent notification) {
        RemoveInterfaceEventData data = notification.getRemoveInterfaceEventData();
        String interfaceName = data.getInterfaceName();
        Long vpnId = data.getVpnId();
        BigInteger dpnId = data.getDpnId();
        LOG.trace("Processing vpn interface {} deletion", interfaceName);
        aclServiceUtil.updateBoundServicesFlow(interfaceName, null/*vpnName*/);
        aclServiceUtil.updateRemoteAclFilterTable(interfaceName, vpnId, dpnId, 1/*DELETE*/);
    }
}

