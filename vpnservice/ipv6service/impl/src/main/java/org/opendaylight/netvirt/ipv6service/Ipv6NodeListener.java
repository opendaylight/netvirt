/*
 * Copyright (c) 2016 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.InterfaceExternalIds;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6NodeListener extends AbstractDataChangeListener<Node> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6NodeListener.class);
    private ListenerRegistration<DataChangeListener> nodeListener;
    private IMdsalApiManager mdsalUtil;
    private IfMgr ifMgr;

    public Ipv6NodeListener(final DataBroker dataBroker, final IMdsalApiManager mdsalUtil) {
        super(Node.class);
        registerListener(dataBroker);
        this.mdsalUtil = mdsalUtil;
    }

    private void registerListener(final DataBroker db) {
        nodeListener = db.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                                                     getWildCardPath(), Ipv6NodeListener.this,
                                                     AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(SouthboundUtils.OVSDB_TOPOLOGY_ID))
                .child(Node.class);
    }

    public void setIfMgrInstance(IfMgr instance) {
        this.ifMgr = instance;
    }

    @Override
    protected void add(InstanceIdentifier<Node> key, Node dataObjectModification) {
        LOG.debug("Node added, key: {}, node {}", key, dataObjectModification);

        OvsdbBridgeAugmentation bridge = dataObjectModification.getAugmentation(OvsdbBridgeAugmentation.class);
        if (bridge != null) {
            String dpId = bridge.getDatapathId().getValue();
            for (TerminationPoint tp : dataObjectModification.getTerminationPoint()) {
                OvsdbTerminationPointAugmentation intf = tp.getAugmentation(OvsdbTerminationPointAugmentation.class);
                LOG.debug("tp {}", intf);
                LOG.debug("id {}, name {}, ofport {}", intf.getInterfaceUuid(), intf.getName(),
                    intf.getOfport());
                Uuid externalId = getExternalInterfaceId(intf.getInterfaceExternalIds());
                if (externalId == null) {
                    LOG.debug("No external iface-id associated with the port {}", intf.getInterfaceUuid());
                    continue;
                }
                if (intf.getOfport() != null) {
                    ifMgr.updateInterface(externalId, dpId, intf.getOfport());
                }
            }
            LOG.debug("bridge aug {}, dp {} ", bridge, bridge.getDatapathId().getValue());
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Node> key, Node dataObjectModification) {
        LOG.debug("Node removed, key: {}, node {}", key, dataObjectModification);

        OvsdbBridgeAugmentation bridge = dataObjectModification.getAugmentation(OvsdbBridgeAugmentation.class);
        if (bridge != null) {
            String dpId = bridge.getDatapathId().getValue();
            for (TerminationPoint tp : dataObjectModification.getTerminationPoint()) {
                OvsdbTerminationPointAugmentation intf = tp.getAugmentation(OvsdbTerminationPointAugmentation.class);
                LOG.debug("tp {}", intf);
                LOG.debug("id {}, name {}, ofport {}", intf.getInterfaceUuid(), intf.getName(),
                    intf.getOfport());
                Uuid externalId = getExternalInterfaceId(intf.getInterfaceExternalIds());
                if (externalId != null) {
                    ifMgr.deleteInterface(externalId, dpId);
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Node> key, Node dataObjectModificationBefore,
                          Node dataObjectModificationAfter) {
        LOG.debug("Node updated, beforeNode {}, afterNode {}", dataObjectModificationBefore,
            dataObjectModificationAfter);

        OvsdbBridgeAugmentation bridge = dataObjectModificationAfter.getAugmentation(OvsdbBridgeAugmentation.class);
        if (bridge != null) {
            String dpId = bridge.getDatapathId().getValue();
            for (TerminationPoint tp : dataObjectModificationAfter.getTerminationPoint()) {
                OvsdbTerminationPointAugmentation intf = tp.getAugmentation(OvsdbTerminationPointAugmentation.class);
                LOG.debug("tp {}", intf);
                LOG.debug("id {}, name {}, ofport {}", intf.getInterfaceUuid(), intf.getName(),
                    intf.getOfport());
                Uuid externalId = getExternalInterfaceId(intf.getInterfaceExternalIds());
                if (externalId == null) {
                    LOG.debug("No external iface-id associated with the port {}", intf.getInterfaceUuid());
                    continue;
                }
                if (intf.getOfport() != null) {
                    ifMgr.updateInterface(externalId, dpId, intf.getOfport());
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (nodeListener != null) {
            nodeListener.close();
            nodeListener = null;
        }
        LOG.info("Ipv6NodeListener Closed");
    }

    private Uuid getExternalInterfaceId(List<InterfaceExternalIds> extIdList) {
        if (extIdList == null) {
            return null;
        }
        for (InterfaceExternalIds ifaceId : extIdList) {
            LOG.trace("for external iface {}", ifaceId.getExternalIdKey());
            if ("iface-id".equals(ifaceId.getExternalIdKey())) {
                Uuid id = new Uuid(ifaceId.getExternalIdValue());
                return id;
            }
        }
        return null;
    }
}
