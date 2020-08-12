/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.qos.rev191004.qos.policy.port.map.qos.policy.list.node.list.PortList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.QosEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.SouthBoundRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.EgressQos;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.southboundrpc.rev190820.configure.termination.point.with.qos.input.EgressQosBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class QosOvsdbEntryListener extends
    AbstractClusteredAsyncDataTreeChangeListener<QosEntries> {

    private static final Logger LOG = LoggerFactory.getLogger(QosOvsdbEntryListener.class);
    private final DataBroker dataBroker;
    private final SouthBoundRpcService southBoundRpcService;
    private final QosNeutronUtils qosNeutronUtils;
    private final JobCoordinator jobCoordinator;

    @Inject
    public QosOvsdbEntryListener(final DataBroker dataBroker,
                                 final SouthBoundRpcService southBoundRpcService,
                                 final JobCoordinator jobCoordinator,
                                 final QosNeutronUtils qosNeutronUtils) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(NetworkTopology.class)
    .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
    .child(Node.class).augmentation(OvsdbNodeAugmentation.class).child(QosEntries.class),
        Executors.newListeningSingleThreadExecutor("QosOvsdbEntryListener", LOG));
        this.dataBroker = dataBroker;
        this.southBoundRpcService = southBoundRpcService;
        this.jobCoordinator = jobCoordinator;
        this.qosNeutronUtils = qosNeutronUtils;
    }

    @Override
    public void remove(InstanceIdentifier<QosEntries> key, QosEntries dataObjectModification) {
    }

    @Override
    public void update(InstanceIdentifier<QosEntries> key,
                          QosEntries dataObjectModificationBefore, QosEntries dataObjectModificationAfter) {
    }

    @Override
    public void add(InstanceIdentifier<QosEntries> identifier, QosEntries qosOvsdbEntry) {
        InstanceIdentifier<Node> nodeIid = identifier.firstIdentifierOf(Node.class);
        NodeKey nodeKey = nodeIid.firstKeyOf(Node.class);
        String nodeId = nodeKey.getNodeId().getValue();
        String qosId = qosOvsdbEntry.getQosId().getValue();
        qosId = qosId.substring(qosId.indexOf("=") + 1, qosId.indexOf("]"));
        LOG.info("querying for key {}", nodeId + "-" + qosId);
        List<PortList> portList = qosNeutronUtils.getPortListFromQosPolicyPortMapDS(qosId, nodeId);
        Uuid qosUuid = qosOvsdbEntry.getQosUuid();
        if (portList == null) {
            LOG.error("add: port list empty for qos policy {}", qosId);
            return;
        }
        String ovsNodeId = nodeId + QosConstants.BRIDGE_PREFIX + QosConstants.INTEGRATION_BRIDGE_NAME;
        InstanceIdentifier<Node> ovsdbNodeIid = InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(new NodeId(ovsNodeId)));
        LOG.error("ovsdbNodeIid {}", ovsdbNodeIid);
        portList.forEach(port -> {
            jobCoordinator.enqueueJob("QosPort-" + port, () -> {
                String tpName = port.getTpName();
                List<EgressQos> egressQosList = new ArrayList<EgressQos>();
                egressQosList.add(new EgressQosBuilder().setEgressQosParam("qos")
                        .setEgressQosParamValue(qosUuid.getValue()).build());
                qosNeutronUtils.configureTerminationPoint(null, egressQosList,
                        tpName, ovsdbNodeIid);
                return Collections.emptyList();
            });
        });
    }
}
