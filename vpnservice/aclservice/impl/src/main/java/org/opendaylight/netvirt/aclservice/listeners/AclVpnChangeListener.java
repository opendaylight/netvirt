/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import com.google.common.base.Optional;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
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
    private final AclServiceManager aclServiceManager;
    private final DataBroker dataBroker;

    @Inject
    public AclVpnChangeListener(AclServiceManager aclServiceManager, DataBroker dataBroker) {
        this.aclServiceManager = aclServiceManager;
        this.dataBroker = dataBroker;
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
        LOG.trace("Processing vpn interface {} addition", data.getInterfaceName());
        Long vpnId = data.getVpnId();
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(data.getInterfaceName());
        if (null != aclInterface && aclInterface.isPortSecurityEnabled() && !vpnId.equals(aclInterface.getVpnId())) {
            aclInterface.setVpnId(vpnId);
            aclServiceManager.notify(aclInterface, null, Action.BIND);
        }
    }

    @Override
    public void onRemoveInterfaceFromDpnOnVpnEvent(RemoveInterfaceFromDpnOnVpnEvent notification) {
        RemoveInterfaceEventData data = notification.getRemoveInterfaceEventData();
        String interfaceName = data.getInterfaceName();
        LOG.trace("Processing vpn interface {} deletion", interfaceName);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .Interface> interfaceOpt = AclServiceUtils.getInterface(dataBroker, interfaceName);
        if (!interfaceOpt.isPresent() || (interfaceOpt.isPresent() && interfaceOpt.get() == null)) {
            LOG.trace("Interface is deleted; no need to rebind again");
            return;
        }
        Long vpnId = data.getVpnId();
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(interfaceName);
        if (null != aclInterface && aclInterface.isPortSecurityEnabled() && vpnId.equals(aclInterface.getVpnId())) {
            aclInterface.setVpnId(null);
            aclServiceManager.notify(aclInterface, null, Action.BIND);
        }
    }
}

