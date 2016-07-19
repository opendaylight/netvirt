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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
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

public class DhcpHwvtepListener extends AbstractDataChangeListener<Node> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpHwvtepListener.class);
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DataBroker dataBroker;

    public DhcpHwvtepListener(DataBroker dataBroker, DhcpExternalTunnelManager dhcpManager) {
        super(Node.class);
        this.dhcpExternalTunnelManager = dhcpManager;
        this.dataBroker = dataBroker;
        registerListener();
    }

    private void registerListener() {
        try {
            listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    getWildCardPath(), DhcpHwvtepListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            logger.error("DhcpHwvtepListener DataChange listener registration fail!", e);
            throw new IllegalStateException("DhcpHwvtepListener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Node> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HwvtepSouthboundConstants.HWVTEP_TOPOLOGY_ID)).child(Node.class);
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
        logger.info("DhcpHwvtepListener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Node> identifier,
            Node del) {
        logger.trace("Received Hwvtep remove DCN {}", del);
        HwvtepGlobalAugmentation hwvtepNode = del.getAugmentation(HwvtepGlobalAugmentation.class);
        if (hwvtepNode == null) {
            logger.trace("LogicalSwitch is not present");
            return;
        }
        List<LogicalSwitches> logicalSwitches = hwvtepNode.getLogicalSwitches();
        if (logicalSwitches == null || logicalSwitches.isEmpty()) {
            logger.trace("LogicalSwitch is not added");
            return;
        }
        for (LogicalSwitches logicalSwitch : logicalSwitches) {
            String elanInstanceName = logicalSwitch.getHwvtepNodeName().getValue();
            IpAddress tunnelIp = hwvtepNode.getConnectionInfo().getRemoteIp();
            handleLogicalSwitchRemove(elanInstanceName, tunnelIp);
        }
    }

    @Override
    protected void update(InstanceIdentifier<Node> identifier,
            Node original, Node update) {
    }

    @Override
    protected void add(InstanceIdentifier<Node> identifier,
            Node add) {
    }

    private void handleLogicalSwitchRemove(String elanInstanceName, IpAddress tunnelIp) {
        BigInteger designatedDpnId;
        designatedDpnId = dhcpExternalTunnelManager.readDesignatedSwitchesForExternalTunnel(tunnelIp, elanInstanceName);
        if (designatedDpnId == null) {
            logger.info("Could not find designated DPN ID elanInstanceName {}, tunnelIp {}", elanInstanceName, tunnelIp);
            return;
        }
        dhcpExternalTunnelManager.removeDesignatedSwitchForExternalTunnel(designatedDpnId, tunnelIp, elanInstanceName);
    }
}

