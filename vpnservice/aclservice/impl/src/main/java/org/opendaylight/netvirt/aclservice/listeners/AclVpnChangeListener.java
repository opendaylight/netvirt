/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
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

    @Inject
    public AclVpnChangeListener(AclServiceManager aclServiceManager) {
        this.aclServiceManager = aclServiceManager;
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
        List<Long> vpnId = Collections.singletonList(data.getVpnId());
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(data.getInterfaceName());
        for (Long vpnIdSingle : vpnId) {
            if (!aclInterface.getVpnId().contains(vpnIdSingle)) {
                aclServiceManager.notify(aclInterface, null, Action.UNBIND);
                aclInterface.setVpnId(vpnId);
                aclServiceManager.notify(aclInterface, null, Action.BIND);
                break;
            }
        }
    }

    @Override
    public void onRemoveInterfaceFromDpnOnVpnEvent(RemoveInterfaceFromDpnOnVpnEvent notification) {
        RemoveInterfaceEventData data = notification.getRemoveInterfaceEventData();
        LOG.trace("Processing vpn interface {} deletion", data.getInterfaceName());
        List<Long> vpnId = Collections.singletonList(data.getVpnId());
        boolean vpnEqual = true;
        AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(data.getInterfaceName());
        for (Long vpnIdSingle : vpnId) {
            if (!aclInterface.getVpnId().contains(vpnIdSingle)) {
                vpnEqual = false;
            }
        }
        if (vpnEqual == true) {
            aclServiceManager.notify(aclInterface, null, Action.UNBIND);
            aclInterface.setVpnId(null);
            aclServiceManager.notify(aclInterface, null, Action.BIND);
        }
    }
}

