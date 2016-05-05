/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.fcapsapp.performancecounter.FlowNodeConnectorInventoryTranslatorImpl;
import org.opendaylight.netvirt.fcapsapp.alarm.AlarmAgent;
import org.opendaylight.netvirt.fcapsapp.performancecounter.PMAgent;
import org.opendaylight.netvirt.fcapsapp.performancecounter.PacketInCounterHandler;
import org.opendaylight.netvirt.fcapsapp.portinfo.PortNameMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FcapsProvider implements AutoCloseable {

    public static Logger s_logger = LoggerFactory.getLogger(FcapsProvider.class);
    private final DataBroker dataService;
    private final NotificationService notificationProviderService;
    private final EntityOwnershipService entityOwnershipService;
    private FlowNodeConnectorInventoryTranslatorImpl flowNodeConnectorInventoryTranslatorImpl;
    private PacketInCounterHandler packetInCounterHandler;
    private NodeEventListener<FlowCapableNode> nodeNodeEventListener;
    private final AlarmAgent alarmAgent;
    private final PMAgent pmAgent;

    /**
     * Contructor sets the services
     * @param dataBroker instance of databroker
     * @param notificationService instance of notificationservice
     * @param eos instance of EntityOwnershipService
     */
    public FcapsProvider(DataBroker dataBroker,NotificationService notificationService,
                         final EntityOwnershipService eos) {
        this.dataService = Preconditions.checkNotNull(dataBroker, "DataBroker can not be null!");
        s_logger.info("FcapsProvider dataBroket is set");

        this.notificationProviderService = Preconditions.checkNotNull(notificationService,
                "notificationService can not be null!");
        s_logger.info("FcapsProvider notificationProviderService is set");

        this.entityOwnershipService = Preconditions.checkNotNull(eos, "EntityOwnership service can not be null");
        s_logger.info("FcapsProvider entityOwnershipService is set");

        alarmAgent = new AlarmAgent();
        pmAgent = new PMAgent();

        alarmAgent.registerAlarmMbean();

        pmAgent.registerMbeanForEFS();
        pmAgent.registerMbeanForPorts();
        pmAgent.registerMbeanForPacketIn();
        PortNameMapping.registerPortMappingBean();

        nodeNodeEventListener = new NodeEventListener<>(entityOwnershipService);
        registerListener(dataService);
        flowNodeConnectorInventoryTranslatorImpl = new
                FlowNodeConnectorInventoryTranslatorImpl(dataService,entityOwnershipService);
        packetInCounterHandler = new PacketInCounterHandler();
        notificationProviderService.registerNotificationListener(packetInCounterHandler);
    }

    private void registerListener(DataBroker dataBroker) {
        final DataTreeIdentifier<FlowCapableNode> treeId =
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, getWildCardPath());
        try {
            dataBroker.registerDataTreeChangeListener(treeId, nodeNodeEventListener);
        } catch (Exception e) {
            s_logger.error("Registeration failed on DataTreeChangeListener {}",e);
        }
    }

    private InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class)
                .child(Node.class)
                .augmentation(FlowCapableNode.class);
    }

    @Override
    public void close() throws Exception {

    }
}
