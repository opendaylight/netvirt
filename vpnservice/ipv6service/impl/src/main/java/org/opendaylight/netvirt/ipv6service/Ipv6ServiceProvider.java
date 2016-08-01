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
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6PeriodicTrQueue;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ServiceProvider implements BindingAwareProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceProvider.class);
    private IInterfaceManager interfaceManager;
    private IMdsalApiManager mdsalManager;
    private Ipv6PktHandler ipv6PktHandler;
    private OdlInterfaceRpcService interfaceManagerRpc;
    private NeutronPortChangeListener portListener;
    private Registration packetListener = null;
    private NotificationProviderService notificationService;
    private NeutronSubnetChangeListener subnetListener;
    private NeutronRouterChangeListener routerListener;
    private IfMgr ifMgr;
    private Ipv6ServiceInterfaceEventListener ipv6ServiceInterfaceEventListener;
    private Ipv6NodeListener ipv6NodeListener;

    private DataBroker broker;

    public Ipv6ServiceProvider() {
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        broker = session.getSALService(DataBroker.class);
        final PacketProcessingService pktProcessingService = session.getRpcService(PacketProcessingService.class);
        portListener = new NeutronPortChangeListener(broker);
        subnetListener = new NeutronSubnetChangeListener(broker);
        routerListener = new NeutronRouterChangeListener(broker);

        ifMgr = IfMgr.getIfMgrInstance();
        ifMgr.setInterfaceManagerRpc(interfaceManagerRpc);
        Ipv6PeriodicRAThread ipv6Thread = Ipv6PeriodicRAThread.getInstance();
        ipv6ServiceInterfaceEventListener = new Ipv6ServiceInterfaceEventListener(broker, mdsalManager);
        ipv6ServiceInterfaceEventListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);
        ipv6ServiceInterfaceEventListener.setIfMgrInstance(ifMgr);
        Ipv6RouterAdvt.setPacketProcessingService(pktProcessingService);
        ipv6NodeListener = new Ipv6NodeListener(broker, mdsalManager);
        ipv6NodeListener.setIfMgrInstance(ifMgr);

        ipv6PktHandler = new Ipv6PktHandler();
        ipv6PktHandler.setIfMgrInstance(ifMgr);
        ipv6PktHandler.setPacketProcessingService(pktProcessingService);
        packetListener = notificationService.registerNotificationListener(ipv6PktHandler);
        LOG.info("IPv6 Service Initiated");
    }

    @Override
    public void close() throws Exception {
        portListener.close();
        subnetListener.close();
        routerListener.close();
        ipv6NodeListener.close();
        ipv6ServiceInterfaceEventListener.close();
        Ipv6PeriodicTrQueue queue = Ipv6PeriodicTrQueue.getInstance();
        queue.clearTimerQueue();
        Ipv6PeriodicRAThread ipv6Thread = Ipv6PeriodicRAThread.getInstance();
        ipv6Thread.stopIpv6PeriodicRAThread();
        LOG.info("IPv6 Service closed");
    }

    public void setInterfaceManager(IInterfaceManager interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setInterfaceManagerRpc(OdlInterfaceRpcService interfaceManagerRpc) {
        this.interfaceManagerRpc = interfaceManagerRpc;
    }

    public void setNotificationProviderService(NotificationProviderService notificationServiceDependency) {
        this.notificationService = notificationServiceDependency;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }
}
