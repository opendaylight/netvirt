/*
 * Copyright (c) 2015 - 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransportZoneNotificationUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TransportZoneNotificationUtil.class);
    private static final String TUNNEL_PORT = "tunnel_port";
    private static final String LOCAL_IP = "local_ip";
    private static final String ALL_SUBNETS = "0.0.0.0/0";
    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southBoundUtils;
    private final IElanService elanService;

    public TransportZoneNotificationUtil(final DataBroker dbx, final IInterfaceManager interfaceManager,
            final IElanService elanService, final ElanConfig elanConfig) {
        this.dataBroker = dbx;
        this.mdsalUtils = new MdsalUtils(dbx);
        this.elanService = elanService;
        southBoundUtils = new SouthboundUtils(mdsalUtils);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateTransportZone(String zoneName, BigInteger dpnId) {
        boolean noUpdates = true;

        String subnetIp = ALL_SUBNETS;

        // Get the transport zone for the
        InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zoneName));
        TransportZone zone = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, inst);

        if (zone == null) {
            zone = createZone(subnetIp, zoneName);
        }

        try {
            if (addVtep(zone, subnetIp, dpnId) > 0) {
                addTransportZone(zone, dpnId);
                noUpdates = false;
            }

        } catch (Exception e) {
            LOG.error("Failed to add tunnels for dpn {} in zone {}", dpnId, zoneName, e);
        }

        if (noUpdates) {
            LOG.warn("No tunnels were added for dpn {} in zone {}", dpnId, zoneName);
        }
    }

    public boolean shouldCreateVtep(List<VpnInterfaces> vpnInterfaces) {
        if (vpnInterfaces == null || vpnInterfaces.isEmpty()) {
            return false;
        }

        for (VpnInterfaces vpnInterface : vpnInterfaces) {
            String interfaceName = vpnInterface.getInterfaceName();

            ElanInterface elanInt = elanService.getElanInterfaceByElanInterfaceName(interfaceName);
            if (elanInt == null) {
                continue;
            }

            if (checkIfVxlanNetwork(elanInt.getElanInstanceName())) {
                return true;
            }
        }

        return false;
    }

    public boolean checkIfVxlanNetwork(String elanInstanceName) {
        ElanInstance elanInstance = elanService.getElanInstance(elanInstanceName);
        if (elanInstance == null || !ElanUtils.isVxlan(elanInstance)) {
            LOG.debug("Non-VXLAN elanInstance: " + elanInstanceName);
            return false;
        }

        return true;
    }

    private TransportZone createZone(String subnetIp, String zoneName) {
        TransportZoneBuilder tzb = new TransportZoneBuilder();
        tzb.setKey(new TransportZoneKey(zoneName));
        tzb.setTunnelType(TunnelTypeVxlan.class);
        tzb.setZoneName(zoneName);
        List<Subnets> subnets = new ArrayList<>();
        subnets.add(newSubnets(subnetIp));
        tzb.setSubnets(subnets);
        return tzb.build();
    }

    private void addTransportZone(TransportZone zone, BigInteger dpnId) throws TransactionCommitFailedException {
        InstanceIdentifier<TransportZone> path = InstanceIdentifier.builder(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zone.getZoneName())).build();

        SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, path, zone);
        LOG.info("Transport zone {} updated due to dpn {} handling.", zone.getZoneName(), dpnId);
    }

    private int addVtep(TransportZone zone, String subnetIp, BigInteger dpnId) throws Exception {
        Subnets subnets = findSubnets(zone.getSubnets(), subnetIp);
        for (Vteps existingVtep : subnets.getVteps()) {
            if (existingVtep.getDpnId().equals(dpnId)) {
                return 0;
            }
        }

        IpAddress nodeIp = getNodeIP(dpnId);
        VtepsBuilder vtepsBuilder = new VtepsBuilder();
        vtepsBuilder.setDpnId(dpnId);
        vtepsBuilder.setIpAddress(nodeIp);
        vtepsBuilder.setPortname(TUNNEL_PORT);
        subnets.getVteps().add(vtepsBuilder.build());

        return 1;
    }

    // search for relevant subnets for the given subnetIP, add one if it is
    // necessary
    private Subnets findSubnets(List<Subnets> subnets, String subnetIp) {
        for (Subnets subnet : subnets) {
            IpPrefix subnetPrefix = new IpPrefix(subnetIp.toCharArray());
            if (subnet.getPrefix().equals(subnetPrefix)) {
                return subnet;
            }
        }

        Subnets retSubnet = newSubnets(subnetIp);
        subnets.add(retSubnet);

        return retSubnet;
    }

    private Subnets newSubnets(String subnetIp) {
        SubnetsBuilder subnetsBuilder = new SubnetsBuilder();
        subnetsBuilder.setDeviceVteps(new ArrayList<>());
        subnetsBuilder.setGatewayIp(new IpAddress("0.0.0.0".toCharArray()));
        subnetsBuilder.setKey(new SubnetsKey(new IpPrefix(subnetIp.toCharArray())));
        subnetsBuilder.setVlanId(0);
        subnetsBuilder.setVteps(new ArrayList<Vteps>());
        return subnetsBuilder.build();
    }

    private IpAddress getNodeIP(BigInteger dpId) throws Exception {
        Node node = getPortsNode(dpId);
        String localIp = southBoundUtils.getOpenvswitchOtherConfig(node, LOCAL_IP);
        if (localIp == null) {
            throw new Exception("missing local_ip key in ovsdb:openvswitch-other-configs in operational"
                    + " network-topology for node: " + node.getNodeId().getValue());
        }

        return new IpAddress(localIp.toCharArray());
    }

    @SuppressWarnings("unchecked")
    private Node getPortsNode(BigInteger dpnId) throws Exception {
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));
        BridgeRefEntry bridgeRefEntry = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath);
        if (bridgeRefEntry == null) {
            throw new Exception("no bridge ref entry found for dpnId: " + dpnId);
        }

        InstanceIdentifier<Node> nodeId =
                ((InstanceIdentifier<OvsdbBridgeAugmentation>) bridgeRefEntry.getBridgeReference().getValue())
                        .firstIdentifierOf(Node.class);
        Node node = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, nodeId);

        if (node == null) {
            throw new Exception("missing node for dpnId: " + dpnId);
        }
        return node;

    }
}
