/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LocalUcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpUCastMacListener extends AbstractClusteredAsyncDataTreeChangeListener<LocalUcastMacs> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpUCastMacListener.class);
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DhcpManager dhcpManager;
    private final DataBroker broker;
    private final DhcpserviceConfig config;

    @Inject
    public DhcpUCastMacListener(final DhcpManager dhcpManager, final DhcpExternalTunnelManager dhcpExtTunnelMgr,
                                final DataBroker dataBroker, final DhcpserviceConfig config) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class).child(Node.class).augmentation(HwvtepGlobalAugmentation.class)
                .child(LocalUcastMacs.class),
                Executors.newListeningSingleThreadExecutor("DhcpUCastMacListener", LOG));
        this.broker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExtTunnelMgr;
        this.dhcpManager = dhcpManager;
        this.config = config;
    }

    public void init() {
        if (config.isControllerDhcpEnabled()) {
            LOG.info("{} init", getClass().getSimpleName());
        }
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        LOG.info("DhcpUCastMacListener Closed");
    }

    @Override
    public void remove(InstanceIdentifier<LocalUcastMacs> identifier,
            LocalUcastMacs del) {
        // Flow removal for table 18 is handled in Neutron Port delete.
        //remove the new CR-DHCP
        NodeId torNodeId = identifier.firstKeyOf(Node.class).getNodeId();
        LogicalSwitches logicalSwitch = getLogicalSwitches(del);
        if (null == logicalSwitch) {
            LOG.error("DhcpUCastMacListener remove :Logical Switch ref doesn't have data");
            return;
        }
        String elanInstanceName = logicalSwitch.getHwvtepNodeName().getValue();
        L2GatewayDevice device = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanInstanceName, torNodeId.getValue());
        if (device == null) {
            LOG.error("Logical Switch Device with name {} is not present in L2GWCONN cache", elanInstanceName);
            return;
        }
        IpAddress tunnelIp = device.getTunnelIp();
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<>(tunnelIp, elanInstanceName);
        dhcpExternalTunnelManager.removeFromAvailableCache(tunnelIpElanName);
    }

    @Override
    public void update(InstanceIdentifier<LocalUcastMacs> identifier,
            LocalUcastMacs original, LocalUcastMacs update) {
        // TODO Auto-generated method stub

    }

    @Override
    public void add(InstanceIdentifier<LocalUcastMacs> identifier,
            LocalUcastMacs add) {
        NodeId torNodeId = identifier.firstKeyOf(Node.class).getNodeId();
        InstanceIdentifier<LogicalSwitches> logicalSwitchRef =
                (InstanceIdentifier<LogicalSwitches>) add.getLogicalSwitchRef().getValue();
        Optional<LogicalSwitches> logicalSwitchOptional;
        try {
            logicalSwitchOptional = SingleTransactionDataBroker.syncReadOptional(broker,
                    LogicalDatastoreType.OPERATIONAL, logicalSwitchRef);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("add: Exception while reading LogicalSwitches DS for the TOR Node ID {}", torNodeId, e);
            return;
        }
        if (!logicalSwitchOptional.isPresent()) {
            LOG.error("Logical Switch ref doesn't have data {}", logicalSwitchRef);
            return;
        }
        LogicalSwitches logicalSwitch = logicalSwitchOptional.get();
        String elanInstanceName = logicalSwitch.getHwvtepNodeName().getValue();
        String macAddress = add.getMacEntryKey().getValue();
        Uint64 vni = Uint64.valueOf(logicalSwitch.getTunnelKey());
        Port port = dhcpExternalTunnelManager.readVniMacToPortCache(vni, macAddress);
        if (port == null) {
            LOG.trace("No neutron port created for macAddress {}, tunnelKey {}", macAddress, vni);
            return;
        }
        L2GatewayDevice device = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanInstanceName, torNodeId.getValue());
        if (device == null) {
            LOG.error("Logical Switch Device with name {} is not present in L2GWCONN cache", elanInstanceName);
            return;
        }
        IpAddress tunnelIp = device.getTunnelIp();
        Subnet subnet = dhcpManager.getNeutronSubnet(port);
        if (null != subnet && !subnet.isEnableDhcp()) {
            dhcpExternalTunnelManager.updateExistingVMTunnelIPCache(tunnelIp, elanInstanceName, macAddress);
            LOG.warn("DhcpUCastMacListener add: flag for the subnetId {} is False so Table 18 entries are not added",
                     subnet.getUuid());
            return;
        }
        Uint64 designatedDpnId =
                dhcpExternalTunnelManager.readDesignatedSwitchesForExternalTunnel(tunnelIp, elanInstanceName);
        if (designatedDpnId == null || designatedDpnId.equals(DhcpMConstants.INVALID_DPID)) {
            LOG.trace("Unable to install flows for macAddress {}. TunnelIp {}, elanInstanceName {}, designatedDpn {} ",
                    macAddress, tunnelIp, elanInstanceName, designatedDpnId);
            dhcpExternalTunnelManager.updateLocalCache(tunnelIp, elanInstanceName, macAddress);
            return;
        }
        dhcpExternalTunnelManager.installDhcpFlowsForVms(tunnelIp, elanInstanceName,
                DhcpServiceUtils.getListOfDpns(broker), designatedDpnId, macAddress);
    }

    @Nullable
    private LogicalSwitches getLogicalSwitches(LocalUcastMacs ucastMacs) {
        InstanceIdentifier<LogicalSwitches> logicalSwitchRef =
                (InstanceIdentifier<LogicalSwitches>)ucastMacs.getLogicalSwitchRef().getValue();
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, LogicalDatastoreType.OPERATIONAL,
                    logicalSwitchRef).orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getLogicalSwitches: Exception while reading LogicalSwitches DS for ucastMacs {}", ucastMacs, e);
            return null;
        }
    }
}
