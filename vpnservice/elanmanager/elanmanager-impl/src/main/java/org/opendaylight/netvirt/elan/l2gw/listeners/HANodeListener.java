/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.genius.utils.hwvtep.HACacheUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.managers.ManagerOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

/**
 * Listener to handle physical switch updates.
 */
public class HANodeListener
        extends AsyncDataTreeChangeListenerBase<Node, HANodeListener>
        implements ClusteredDataTreeChangeListener<Node>, AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HANodeListener.class);

    /** The data broker. */
    private DataBroker dataBroker;

    /**
     * Instantiates a new hwvtep physical switch listener.
     *
     * @param dataBroker
     *            the data broker
     */
    public HANodeListener(final DataBroker dataBroker) {
        super(Node.class, HANodeListener.class);
        this.dataBroker = dataBroker;
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#getWildCardPath()
     */
    @Override
    protected InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Node> key, Node deleted) {
        //HACacheUtils.removeDeviceFromCache(deleted.getNodeId().getValue());
    }

    @Override
    protected void update(InstanceIdentifier<Node> key, Node before, Node updated) {
        HwvtepGlobalAugmentation updatedAugmentaion = updated.getAugmentation(HwvtepGlobalAugmentation.class);
        HwvtepGlobalAugmentation beforeAugmentaion = before.getAugmentation(HwvtepGlobalAugmentation.class);
        List<Managers> up = null;
        List<Managers> be = null;
        if (updatedAugmentaion != null) {
            up  = updatedAugmentaion.getManagers();
        }
        if (beforeAugmentaion != null) {
            be  = beforeAugmentaion.getManagers();
        }
        if (up != null && be != null) {
            if (up.size() > 0) {
                if (be.size() > 0) {
                    Managers m1 = up.get(0);
                    Managers m2 = be.get(0);
                    if (!m1.equals(m2)) {
                        LOG.error("manager entry updated for node {} ", updated.getNodeId().getValue());
                        if (isHANode(dataBroker, updated)) {
                            LOG.error("Added node {} to ha cache ",updated.getNodeId().getValue());
                            HACacheUtils.addDeviceToCache(updated.getNodeId().getValue());
                            ConcurrentMap<String, L2GatewayDevice> l2Devices = L2GatewayCacheUtils.getCache();
                            if (l2Devices == null || l2Devices.size() == 0) {
                                return;
                            }
                            String haId = getHAId(dataBroker, updated);
                            if (haId != null) {
                                String haNodeIdVal = "hwvtep" + "://" + "uuid" + "/" + UUID.nameUUIDFromBytes(haId.getBytes());
                                for (L2GatewayDevice l2Device : l2Devices.values()) {
                                    if (updated.getNodeId().getValue().equals(l2Device.getHwvtepNodeId())) {
                                        LOG.error("Replaced the l2gw device cache entry for device {} with val {}",
                                                l2Device.getDeviceName(), l2Device.getHwvtepNodeId());
                                        l2Device.setHwvtepNodeId(haNodeIdVal);
                                    }
                                }
                            } else {
                                for (L2GatewayDevice l2Device : l2Devices.values()) {
                                    if (updated.getNodeId().getValue().equals(l2Device.getHwvtepNodeId())) {
                                        LOG.error("Reset the l2gw device cache entry for device {} with val {}",
                                                l2Device.getDeviceName(), l2Device.getHwvtepNodeId());
                                        l2Device.setHwvtepNodeId(null);
                                        l2Device.isConnected();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //TODO handle unhaed case
    }

    @Override
    protected void add(InstanceIdentifier<Node> key, Node node) {
        if (isHANode(dataBroker, node)) {
            LOG.error("Added node {} to ha cache ",node.getNodeId().getValue());
            HACacheUtils.addDeviceToCache(node.getNodeId().getValue());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.opendaylight.vpnservice.datastoreutils.
     * AsyncDataTreeChangeListenerBase#getDataTreeChangeListener()
     */
    @Override
    protected HANodeListener getDataTreeChangeListener() {
        return HANodeListener.this;
    }

    public static String getManagedByNodeId(HwvtepGlobalRef globalRef) {
        InstanceIdentifier<?> instId = globalRef.getValue();
        return instId.firstKeyOf(Node.class).getNodeId().getValue();
    }

    public static String getHAId(DataBroker broker, Node node) {
        NodeId hwvtepNodeId = null;
        if (node.getAugmentation(PhysicalSwitchAugmentation.class) != null) {
            hwvtepNodeId = new NodeId(getManagedByNodeId(
                    node.getAugmentation(PhysicalSwitchAugmentation.class).getManagedBy()));
            node = HwvtepUtils.getHwVtepNode(broker, LogicalDatastoreType.OPERATIONAL, hwvtepNodeId);
        } else {
            hwvtepNodeId = node.getNodeId();
        }
        boolean haEnabled = false;
        HwvtepGlobalAugmentation globalAugmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
        if (globalAugmentation != null) {
            List<Managers> managers = globalAugmentation.getManagers();
            if (managers != null && managers.size() > 0 && managers.get(0).getManagerOtherConfigs() != null) {
                for (ManagerOtherConfigs configs : managers.get(0).getManagerOtherConfigs()) {
                    if (configs.getOtherConfigKey().equals("ha_id")) {
                        return configs.getOtherConfigValue();
                    }
                }
            }
        }
        return null;
    }

    public static boolean isHANode(DataBroker broker, Node node) {
        NodeId hwvtepNodeId = null;
        if (node == null) {
            return false;
        }
        if (node.getAugmentation(PhysicalSwitchAugmentation.class) != null) {
            hwvtepNodeId = new NodeId(getManagedByNodeId(
                    node.getAugmentation(PhysicalSwitchAugmentation.class).getManagedBy()));
            node = HwvtepUtils.getHwVtepNode(broker, LogicalDatastoreType.OPERATIONAL, hwvtepNodeId);
        } else {
            hwvtepNodeId = node.getNodeId();
        }
        if (node == null) {
            return false;
        }
        boolean haEnabled = false;
        HwvtepGlobalAugmentation globalAugmentation = node.getAugmentation(HwvtepGlobalAugmentation.class);
        if (globalAugmentation != null) {
            List<Managers> managers = globalAugmentation.getManagers();
            if (managers != null && managers.size() > 0 && managers.get(0).getManagerOtherConfigs() != null) {
                for (ManagerOtherConfigs configs : managers.get(0).getManagerOtherConfigs()) {
                    if (configs.getOtherConfigKey().equals("ha_enabled")) {
                        haEnabled = Boolean.valueOf(configs.getOtherConfigValue());
                        break;
                    }
                }
            }
        }
        return haEnabled;
    }

    public static boolean isHANode(DataBroker broker, PhysicalSwitchAugmentation augmentation) {
        NodeId hwvtepNodeId = null;
        hwvtepNodeId = new NodeId(getManagedByNodeId(
                augmentation.getManagedBy()));
        Node node = HwvtepUtils.getHwVtepNode(broker, LogicalDatastoreType.OPERATIONAL, hwvtepNodeId);
        return isHANode(broker, node);
    }

    public static boolean isHANode(NodeId nodeId) {

        return false;
    }


}
