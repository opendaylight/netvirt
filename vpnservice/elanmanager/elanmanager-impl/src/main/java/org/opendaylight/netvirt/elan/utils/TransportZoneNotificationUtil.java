/*
 * Copyright (c) 2017 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;

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
    private static final String ALL_SUBNETS_GW = "0.0.0.0";
    private static final String ALL_SUBNETS = "0.0.0.0/0";
    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southBoundUtils;
    private final IElanService elanService;
    private final ElanConfig elanConfig;

    public TransportZoneNotificationUtil(final DataBroker dbx, final IInterfaceManager interfaceManager,
            final IElanService elanService, final ElanConfig elanConfig) {
        this.dataBroker = dbx;
        this.mdsalUtils = new MdsalUtils(dbx);
        this.elanService = elanService;
        this.elanConfig = elanConfig;
        southBoundUtils = new SouthboundUtils(mdsalUtils);
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

            if (ElanUtils.isVxlanNetwork(dataBroker, elanInt.getElanInstanceName())) {
                return true;
            } else {
                LOG.debug("Non-VXLAN elanInstance: " + elanInt.getElanInstanceName());
            }
        }

        return false;
    }

    private TransportZone createZone(String subnetIp, String zoneName) {
        List<Subnets> subnets = new ArrayList<>();
        subnets.add(buildSubnets(subnetIp));
        TransportZoneBuilder tzb = new TransportZoneBuilder().setKey(new TransportZoneKey(zoneName))
                .setTunnelType(TunnelTypeVxlan.class).setZoneName(zoneName).setSubnets(subnets);
        return tzb.build();
    }

    private void updateTransportZone(TransportZone zone, BigInteger dpnId) throws TransactionCommitFailedException {
        InstanceIdentifier<TransportZone> path = InstanceIdentifier.builder(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zone.getZoneName())).build();

        SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, path, zone);
        LOG.info("Transport zone {} updated due to dpn {} handling.", zone.getZoneName(), dpnId);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateTransportZone(String zoneName, BigInteger dpnId) {
        InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zoneName));

        // FIXME: Read this through a cache
        TransportZone zone = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, inst);

        if (zone == null) {
            zone = createZone(ALL_SUBNETS, zoneName);
        }

        try {
            if (addVtep(zone, ALL_SUBNETS, dpnId)) {
                updateTransportZone(zone, dpnId);
            }
        } catch (Exception e) {
            LOG.error("Failed to add tunnels for dpn {} in zone {}", dpnId, zoneName, e);
        }
    }

    /**
     * Tries to add a vtep for a transport zone.
     *
     * @return Whether a vtep was added or not.
     */
    private boolean addVtep(TransportZone zone, String subnetIp, BigInteger dpnId) throws Exception {
        Subnets subnets = getOrAddSubnet(zone.getSubnets(), subnetIp);
        for (Vteps existingVtep : subnets.getVteps()) {
            if (existingVtep.getDpnId().equals(dpnId)) {
                return false;
            }
        }

        Optional<IpAddress> nodeIp = getNodeIP(dpnId);

        if (nodeIp.isPresent()) {
            VtepsBuilder vtepsBuilder =
                    new VtepsBuilder().setDpnId(dpnId).setIpAddress(nodeIp.get()).setPortname(TUNNEL_PORT).
                    setOptionOfTunnel(elanConfig.isUseOfTunnels());
            subnets.getVteps().add(vtepsBuilder.build());

            return true;
        }

        return false;
    }

    // search for relevant subnets for the given subnetIP, add one if it is
    // necessary
    private Subnets getOrAddSubnet(List<Subnets> subnets, String subnetIp) {
        IpPrefix subnetPrefix = new IpPrefix(subnetIp.toCharArray());

        if (subnets != null) {
            for (Subnets subnet : subnets) {
                if (subnet.getPrefix().equals(subnetPrefix)) {
                    return subnet;
                }
            }
        }

        Subnets retSubnet = buildSubnets(subnetIp);
        subnets.add(retSubnet);

        return retSubnet;
    }

    private Subnets buildSubnets(String subnetIp) {
        SubnetsBuilder subnetsBuilder = new SubnetsBuilder().setDeviceVteps(new ArrayList<>())
                .setGatewayIp(new IpAddress(ALL_SUBNETS_GW.toCharArray()))
                .setKey(new SubnetsKey(new IpPrefix(subnetIp.toCharArray()))).setVlanId(0)
                .setVteps(new ArrayList<Vteps>());
        return subnetsBuilder.build();
    }

    private Optional<IpAddress> getNodeIP(BigInteger dpId) throws Exception {
        Optional<Node> node = getPortsNode(dpId);

        if (node.isPresent()) {
            String localIp = southBoundUtils.getOpenvswitchOtherConfig(node.get(), LOCAL_IP);
            if (localIp == null) {
                LOG.error("missing local_ip key in ovsdb:openvswitch-other-configs in operational"
                        + " network-topology for node: " + node.get().getNodeId().getValue());
            } else {
                return Optional.of(new IpAddress(localIp.toCharArray()));
            }
        }

        return Optional.absent();
    }

    @SuppressWarnings("unchecked")
    private Optional<Node> getPortsNode(BigInteger dpnId) throws Exception {
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));

        // FIXME: Read this through a cache
        BridgeRefEntry bridgeRefEntry = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath);
        if (bridgeRefEntry == null) {
            LOG.error("no bridge ref entry found for dpnId: " + dpnId);
            return Optional.absent();
        }

        InstanceIdentifier<Node> nodeId =
                ((InstanceIdentifier<OvsdbBridgeAugmentation>) bridgeRefEntry.getBridgeReference().getValue())
                        .firstIdentifierOf(Node.class);

        // FIXME: Read this through a cache
        Node node = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, nodeId);

        if (node == null) {
            LOG.error("missing node for dpnId: " + dpnId);
            return Optional.absent();
        }

        return Optional.of(node);
    }
}
