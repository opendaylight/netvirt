/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpLogicalSwitchListener extends AbstractDataChangeListener<LogicalSwitches> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpLogicalSwitchListener.class);
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DataBroker dataBroker;

    public DhcpLogicalSwitchListener(DhcpExternalTunnelManager dhcpManager, DataBroker dataBroker) {
        super(LogicalSwitches.class);
        this.dhcpExternalTunnelManager = dhcpManager;
        this.dataBroker = dataBroker;
        registerListener();
    }

    private void registerListener() {
        try {
            listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), DhcpLogicalSwitchListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("DhcpLogicalSwitchListener DataChange listener registration fail!", e);
            throw new IllegalStateException("DhcpLogicalSwitchListener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<LogicalSwitches> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID))
                .child(Node.class).augmentation(HwvtepGlobalAugmentation.class)
                .child(LogicalSwitches.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        logger.info("DhcpLogicalSwitchListener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<LogicalSwitches> identifier,
            LogicalSwitches del) {
        logger.trace("Received LogicalSwitch remove DCN");
        String elanInstanceName = del.getLogicalSwitchUuid().toString();
        ConcurrentMap<String, L2GatewayDevice> devices  = L2GatewayCacheUtils.getCache();
        String nodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        L2GatewayDevice targetDevice = null;
        for (L2GatewayDevice device : devices.values()) {
            if (nodeId.equals(device.getHwvtepNodeId())) {
                targetDevice = device;
                break;
            }
        }
        if (targetDevice == null) {
            logger.error("Logical Switch Device with name {} is not present in L2GW cache", elanInstanceName);
            return;
        }
        IpAddress tunnelIp = targetDevice.getTunnelIp();
        handleLogicalSwitchRemove(elanInstanceName, tunnelIp);
    }

    @Override
    protected void update(InstanceIdentifier<LogicalSwitches> identifier,
            LogicalSwitches original, LogicalSwitches update) {
        logger.trace("Received LogicalSwitch update DCN");

    }

    @Override
    protected void add(InstanceIdentifier<LogicalSwitches> identifier,
            LogicalSwitches add) {
        logger.trace("Received LogicalSwitch add DCN");
        String elanInstanceName = add.getHwvtepNodeName().getValue();
        ConcurrentMap<String, L2GatewayDevice> devices  = L2GatewayCacheUtils.getCache();
        String nodeId = identifier.firstKeyOf(Node.class).getNodeId().getValue();
        L2GatewayDevice targetDevice = null;
        for (L2GatewayDevice device : devices.values()) {
            if (nodeId.equals(device.getHwvtepNodeId())) {
                targetDevice = device;
                break;
            }
        }
        if (targetDevice == null) {
            logger.error("Logical Switch Device with name {} is not present in L2GW cache", elanInstanceName);
            return;
        }
        IpAddress tunnelIp = targetDevice.getTunnelIp();
        handleLogicalSwitchAdd(elanInstanceName, tunnelIp);
    }

    private void handleLogicalSwitchRemove(String elanInstanceName, IpAddress tunnelIp) {
        BigInteger designatedDpnId;
        designatedDpnId = dhcpExternalTunnelManager.readDesignatedSwitchesForExternalTunnel(tunnelIp, elanInstanceName);
        if (designatedDpnId == null || designatedDpnId.equals(DHCPMConstants.INVALID_DPID)) {
            logger.info("Could not find designated DPN ID");
            return;
        }
        dhcpExternalTunnelManager.removeDesignatedSwitchForExternalTunnel(designatedDpnId, tunnelIp, elanInstanceName);
    }

    private void handleLogicalSwitchAdd(String elanInstanceName, IpAddress tunnelIp) {
        List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(dataBroker);
        BigInteger designatedDpnId;
        designatedDpnId = dhcpExternalTunnelManager.designateDpnId(tunnelIp, elanInstanceName, dpns);
        if (designatedDpnId == null || designatedDpnId.equals(DHCPMConstants.INVALID_DPID)) {
            logger.info("Unable to designate a DPN");
            return;
        }
    }
}