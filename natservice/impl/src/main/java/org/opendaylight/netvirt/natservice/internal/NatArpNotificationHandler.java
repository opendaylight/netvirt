/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpRequestReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.ArpResponseReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.MacChanged;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.arputil.rev160406.OdlArputilListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.elan._interface.StaticMacEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;

@Singleton
public class NatArpNotificationHandler implements OdlArputilListener {

    private static final Logger LOG = LoggerFactory.getLogger(NatArpNotificationHandler.class);

    private final DataBroker dataBroker;
    private final IElanService elanService;
    private final NatSouthboundEventHandlers southboundEventHandlers;

    @Inject
    public NatArpNotificationHandler(final DataBroker dataBroker,
                                     final IElanService elanService,
                                     final NatSouthboundEventHandlers southboundEventHandlers) {
        this.dataBroker = dataBroker;
        this.elanService = elanService;
        this.southboundEventHandlers = southboundEventHandlers;
    }

    @Override
    public void onArpResponseReceived(ArpResponseReceived notification) {

    }

    @Override
    public void onMacChanged(MacChanged notification) {

    }

    @Override
    public void onArpRequestReceived(ArpRequestReceived notification) {

        IpAddress srcIp = notification.getSrcIpaddress();
        if (srcIp == null || !Objects.equals(srcIp, notification.getDstIpaddress())) {
            LOG.debug("NatArpNotificationHandler: ignoring ARP packet, not gratuitous {}", notification);
            return;
        }

        ElanInterface arpSenderIfc = elanService.getElanInterfaceByElanInterfaceName(notification.getInterface());
        if (ipBelongsToElanInterface(arpSenderIfc, srcIp)) {
            LOG.debug("NatArpNotificationHandler: ignoring GARP packet. No need to NAT a port's static IP. {}",
                    notification);
            return;
        }

        ElanInterface targetIfc = null;
        for(String ifcName : elanService.getElanInterfaces(arpSenderIfc.getElanInstanceName())) {
             ElanInterface elanInterface = elanService.getElanInterfaceByElanInterfaceName(ifcName);
             if (ipBelongsToElanInterface(elanInterface, srcIp)) {
                 targetIfc = elanInterface;
                 break;
             }
        }

        if (null == targetIfc) {
            LOG.warn("NatArpNotificationHandler: GARP does not correspond to an interface in this elan {}",
                     notification);
            return;
        }

        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, targetIfc.getName());
        if (null == routerInterface) {
            LOG.warn("NatArpNotificationHandler: Could not retrieve router ifc for {}", targetIfc);
            return;
        }

        this.southboundEventHandlers.handleAdd(targetIfc.getName(), notification.getDpnId(), routerInterface);

    }

    private boolean ipBelongsToElanInterface(ElanInterface elanInterface, IpAddress ip) {
        for (StaticMacEntries staticMacEntries :  elanInterface.getStaticMacEntries()) {
            if (Objects.equals(staticMacEntries.getIpPrefix(), ip)) {
                return true;
            }
        }
        return false;
    }
}
