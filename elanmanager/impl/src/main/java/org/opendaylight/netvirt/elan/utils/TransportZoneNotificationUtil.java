/*
 * Copyright (c) 2017 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elan.cache.ElanInstanceCache;
import org.opendaylight.netvirt.elan.internal.ElanBridgeManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.tunnel.end.points.TzMembership;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TransportZoneNotificationUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TransportZoneNotificationUtil.class);
    private static final String TUNNEL_PORT = "tunnel_port";
    private static final String LOCAL_IP = "local_ip";
    private static final String LOCAL_IPS = "local_ips";
    private static final char IP_NETWORK_ZONE_NAME_DELIMITER = '-';
    private static final String ALL_SUBNETS_GW = "0.0.0.0";
    private static final String ALL_SUBNETS = "0.0.0.0/0";
    private final ManagedNewTransactionRunner txRunner;
    private final SouthboundUtils southBoundUtils;
    private final IElanService elanService;
    private final ElanConfig elanConfig;
    private final ElanBridgeManager elanBridgeManager;
    private final ElanInstanceCache elanInstanceCache;

    @Inject
    public TransportZoneNotificationUtil(final DataBroker dbx,
            final IElanService elanService, final ElanConfig elanConfig, final ElanBridgeManager elanBridgeManager,
            final ElanInstanceCache elanInstanceCache) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dbx);
        this.elanService = elanService;
        this.elanConfig = elanConfig;
        this.elanBridgeManager = elanBridgeManager;
        this.elanInstanceCache = elanInstanceCache;
        southBoundUtils = new SouthboundUtils(new MdsalUtils(dbx));
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

            if (ElanUtils.isVxlanNetworkOrVxlanSegment(
                    elanInstanceCache.get(elanInt.getElanInstanceName()).orNull())) {
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

    private void updateTransportZone(TransportZone zone, BigInteger dpnId, @Nonnull WriteTransaction tx) {
        InstanceIdentifier<TransportZone> path = InstanceIdentifier.builder(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zone.getZoneName())).build();

        tx.merge(LogicalDatastoreType.CONFIGURATION, path, zone);
        LOG.info("Transport zone {} updated due to dpn {} handling.", zone.getZoneName(), dpnId);
    }

    public void updateTransportZone(String zoneNamePrefix, BigInteger dpnId) {
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            Map<String, String> localIps = getDpnLocalIps(dpnId, tx);
            if (!localIps.isEmpty()) {
                LOG.debug("Will use local_ips for transport zone update for dpn {} and zone name prefix {}", dpnId,
                        zoneNamePrefix);
                for (Entry<String, String> entry : localIps.entrySet()) {
                    String localIp = entry.getKey();
                    String underlayNetworkName = entry.getValue();
                    String zoneName = getTzNameForUnderlayNetwork(zoneNamePrefix, underlayNetworkName);
                    updateTransportZone(zoneName, dpnId, localIp, tx);
                }
            } else {
                updateTransportZone(zoneNamePrefix, dpnId, getDpnLocalIp(dpnId, tx), tx);
            }
        }), LOG, "Error updating transport zone");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void updateTransportZone(String zoneName, BigInteger dpnId, @Nullable String localIp,
            @Nonnull ReadWriteTransaction tx) throws ReadFailedException {
        InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zoneName));

        // FIXME: Read this through a cache
        TransportZone zone = tx.read(LogicalDatastoreType.CONFIGURATION, inst).checkedGet().orNull();

        if (zone == null) {
            zone = createZone(ALL_SUBNETS, zoneName);
        }

        try {
            if (addVtep(zone, ALL_SUBNETS, dpnId, localIp)) {
                updateTransportZone(zone, dpnId, tx);
            }
        } catch (Exception e) {
            LOG.error("Failed to add tunnels for dpn {} in zone {}", dpnId, zoneName, e);
        }
    }

    private void deleteTransportZone(TransportZone zone, BigInteger dpnId, @Nonnull WriteTransaction tx) {
        InstanceIdentifier<TransportZone> path = InstanceIdentifier.builder(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zone.getZoneName())).build();
        tx.delete(LogicalDatastoreType.CONFIGURATION, path);
        LOG.info("Transport zone {} deleted due to dpn {} handling.", zone.getZoneName(), dpnId);
    }

    public void deleteTransportZone(String zoneNamePrefix, BigInteger dpnId) {
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            Map<String, String> localIps = getDpnLocalIps(dpnId, tx);
            if (!localIps.isEmpty()) {
                LOG.debug("Will use local_ips for transport zone delete for dpn {} and zone name prefix {}", dpnId,
                        zoneNamePrefix);
                for (String underlayNetworkName : localIps.values()) {
                    String zoneName = getTzNameForUnderlayNetwork(zoneNamePrefix, underlayNetworkName);
                    deleteTransportZone(zoneName, dpnId, tx);
                }
            } else {
                deleteTransportZone(zoneNamePrefix, dpnId, tx);
            }
        }), LOG, "Error deleting transport zone");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void deleteTransportZone(String zoneName, BigInteger dpnId, @Nonnull ReadWriteTransaction tx)
            throws ReadFailedException {
        InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zoneName));

        // FIXME: Read this through a cache
        TransportZone zone = tx.read(LogicalDatastoreType.CONFIGURATION, inst).checkedGet().orNull();
        if (zone != null) {
            try {
                deleteTransportZone(zone, dpnId, tx);
            } catch (Exception e) {
                LOG.error("Failed to remove tunnels for dpn {} in zone {}", dpnId, zoneName, e);
            }
        }
    }


    /**
     * Update transport zones based on local_ips TEP ips mapping to underlay
     * networks.<br>
     * Deleted local_ips will be removed from the VTEP list of the corresponding
     * transport zones.<br>
     * Added local_ips will be added to all transport zones currently associated
     * with the TEP<br>
     * local_ips for whom the underlay network mapping has been changed will be
     * updated in the VTEP lists of the corresponding transport zones.
     *
     * @param origNode
     *            original OVS node
     * @param updatedNode
     *            updated OVS node
     * @param managerNodeId
     *            uuid of the OVS manager node
     */
    public void handleOvsdbNodeUpdate(Node origNode, Node updatedNode, String managerNodeId) {
        Map<String,
                String> origLocalIpMap = java.util.Optional
                        .ofNullable(elanBridgeManager.getOpenvswitchOtherConfigMap(origNode, LOCAL_IPS))
                        .orElse(Collections.emptyMap());
        Map<String,
                String> updatedLocalIpMap = java.util.Optional
                        .ofNullable(elanBridgeManager.getOpenvswitchOtherConfigMap(updatedNode, LOCAL_IPS))
                        .orElse(Collections.emptyMap());
        MapDifference<String, String> mapDiff = Maps.difference(origLocalIpMap, updatedLocalIpMap);
        if (mapDiff.areEqual()) {
            return;
        }

        java.util.Optional<BigInteger> dpIdOpt = elanBridgeManager.getDpIdFromManagerNodeId(managerNodeId);
        if (!dpIdOpt.isPresent()) {
            LOG.debug("No DPN id found for node {}", managerNodeId);
            return;
        }

        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            BigInteger dpId = dpIdOpt.get();
            Optional<DPNTEPsInfo> dpnTepsInfoOpt = getDpnTepsInfo(dpId, tx);
            if (!dpnTepsInfoOpt.isPresent()) {
                LOG.debug("No DPNTEPsInfo found for DPN id {}", dpId);
                return;
            }

            List<TunnelEndPoints> tunnelEndPoints = dpnTepsInfoOpt.get().getTunnelEndPoints();
            if (tunnelEndPoints == null || tunnelEndPoints.isEmpty()) {
                LOG.debug("No tunnel endpoints defined for DPN id {}", dpId);
                return;
            }

            Set<String> zonePrefixes = new HashSet<>();
            Map<String, List<String>> tepTzMap = tunnelEndPoints.stream().collect(Collectors
                    .toMap(tep -> String.valueOf(tep.getIpAddress().getValue()), this::getTepTransportZoneNames));
            LOG.trace("Transport zone prefixes {}", tepTzMap);

            handleRemovedLocalIps(mapDiff.entriesOnlyOnLeft(), dpId, zonePrefixes, tepTzMap, tx);
            handleChangedLocalIps(mapDiff.entriesDiffering(), dpId, zonePrefixes, tepTzMap, tx);
            handleAddedLocalIps(mapDiff.entriesOnlyOnRight(), dpId, zonePrefixes, tx);
        }), LOG, "Error handling OVSDB node update");
    }

    private void handleAddedLocalIps(Map<String, String> addedEntries, BigInteger dpId, Set<String> zonePrefixes,
            ReadWriteTransaction tx) throws ReadFailedException {
        if (addedEntries == null || addedEntries.isEmpty()) {
            LOG.trace("No added local_ips found for DPN {}", dpId);
            return;
        }

        LOG.debug("Added local_ips {} on DPN {}", addedEntries.keySet(), dpId);
        for (Map.Entry<String, String> addedEntry : addedEntries.entrySet()) {
            String ipAddress = addedEntry.getKey();
            String underlayNetworkName = addedEntry.getValue();
            for (String zonePrefix : zonePrefixes) {
                String zoneName = getTzNameForUnderlayNetwork(zonePrefix, underlayNetworkName);
                updateTransportZone(zoneName, dpId, ipAddress, tx);
            }
        }
    }

    private void handleChangedLocalIps(Map<String, ValueDifference<String>> changedEntries, BigInteger dpId,
            Set<String> zonePrefixes, Map<String, List<String>> tepTzMap, @Nonnull ReadWriteTransaction tx)
            throws ReadFailedException {
        if (changedEntries == null || changedEntries.isEmpty()) {
            LOG.trace("No changed local_ips found for DPN {}", dpId);
            return;
        }

        LOG.debug("Changing underlay network mapping for local_ips {} on DPN {}", changedEntries.keySet(), dpId);
        for (Map.Entry<String, ValueDifference<String>> changedEntry : changedEntries.entrySet()) {
            String ipAddress = changedEntry.getKey();
            ValueDifference<String> underlayNetworkDiff = changedEntry.getValue();
            List<String> zoneNames = tepTzMap.get(ipAddress);
            if (zoneNames != null) {
                for (String zoneName : zoneNames) {
                    String removedUnderlayNetwork = underlayNetworkDiff.leftValue();
                    String addedUnderlayNetwork = underlayNetworkDiff.rightValue();
                    Optional<String> zonePrefixOpt = getZonePrefixForUnderlayNetwork(zoneName, removedUnderlayNetwork);
                    if (zonePrefixOpt.isPresent()) {
                        String zonePrefix = zonePrefixOpt.get();
                        removeVtep(zoneName, dpId, tx);
                        zonePrefixes.add(zonePrefix);
                        String newZoneName = getTzNameForUnderlayNetwork(zonePrefix, addedUnderlayNetwork);
                        updateTransportZone(newZoneName, dpId, ipAddress, tx);
                    }
                }
            }
        }
    }

    private void handleRemovedLocalIps(Map<String, String> removedEntries, BigInteger dpId, Set<String> zonePrefixes,
            Map<String, List<String>> tepTzMap, @Nonnull WriteTransaction tx) {
        if (removedEntries == null || removedEntries.isEmpty()) {
            LOG.trace("No removed local_ips found on DPN {}", dpId);
            return;
        }

        LOG.debug("Removed local_ips {} for DPN {}", removedEntries.keySet(), dpId);
        removedEntries.forEach((ipAddress, underlayNetworkName) -> {
            List<String> zoneNames = tepTzMap.get(ipAddress);
            if (zoneNames != null) {
                for (String zoneName : zoneNames) {
                    Optional<String> zonePrefix = getZonePrefixForUnderlayNetwork(zoneName, underlayNetworkName);
                    if (zonePrefix.isPresent()) {
                        removeVtep(zoneName, dpId, tx);
                        zonePrefixes.add(zonePrefix.get());
                    }
                }
            }
        });
    }

    private List<String> getTepTransportZoneNames(TunnelEndPoints tep) {
        List<TzMembership> tzMembershipList = tep.getTzMembership();
        if (tzMembershipList == null) {
            LOG.debug("No TZ membership exist for TEP ip {}", tep.getIpAddress().getValue());
            return Collections.emptyList();
        }

        return tzMembershipList.stream().map(TzMembership::getZoneName).distinct()
                .collect(Collectors.toList());
    }

    private Optional<DPNTEPsInfo> getDpnTepsInfo(BigInteger dpId, ReadTransaction tx) {
        InstanceIdentifier<DPNTEPsInfo> identifier = InstanceIdentifier.builder(DpnEndpoints.class)
                .child(DPNTEPsInfo.class, new DPNTEPsInfoKey(dpId)).build();
        try {
            return tx.read(LogicalDatastoreType.CONFIGURATION, identifier).checkedGet();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read DPNTEPsInfo for DPN id {}", dpId);
            return Optional.absent();
        }
    }

    /**
     * Tries to add a vtep for a transport zone.
     *
     * @return Whether a vtep was added or not.
     */
    private boolean addVtep(TransportZone zone, String subnetIp, BigInteger dpnId, @Nullable String localIp) {
        List<Subnets> zoneSubnets = zone.getSubnets();
        if (zoneSubnets == null) {
            return false;
        }

        Subnets subnets = getOrAddSubnet(zoneSubnets, subnetIp);
        for (Vteps existingVtep : subnets.getVteps()) {
            if (existingVtep.getDpnId().equals(dpnId)) {
                return false;
            }
        }

        if (localIp != null) {
            IpAddress nodeIp = new IpAddress(localIp.toCharArray());
            VtepsBuilder vtepsBuilder = new VtepsBuilder().setDpnId(dpnId).setIpAddress(nodeIp)
                    .setPortname(TUNNEL_PORT).setOptionOfTunnel(elanConfig.isUseOfTunnels());
            subnets.getVteps().add(vtepsBuilder.build());
            return true;
        }

        return false;
    }

    private void removeVtep(String zoneName, BigInteger dpId, @Nonnull WriteTransaction tx) {
        InstanceIdentifier<Vteps> path = InstanceIdentifier.builder(TransportZones.class)
                .child(TransportZone.class, new TransportZoneKey(zoneName))
                .child(Subnets.class, new SubnetsKey(new IpPrefix(ALL_SUBNETS.toCharArray())))
                .child(Vteps.class, new VtepsKey(dpId, TUNNEL_PORT)).build();
        tx.delete(LogicalDatastoreType.CONFIGURATION, path);
    }

    // search for relevant subnets for the given subnetIP, add one if it is
    // necessary
    private Subnets getOrAddSubnet(@Nonnull List<Subnets> subnets, @Nonnull String subnetIp) {
        IpPrefix subnetPrefix = new IpPrefix(subnetIp.toCharArray());

        for (Subnets subnet : subnets) {
            if (subnet.getPrefix().equals(subnetPrefix)) {
                return subnet;
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
                .setVteps(new ArrayList<>());
        return subnetsBuilder.build();
    }

    private String getDpnLocalIp(BigInteger dpId, ReadTransaction tx) throws ReadFailedException {
        Optional<Node> node = getPortsNode(dpId, tx);

        if (node.isPresent()) {
            String localIp = southBoundUtils.getOpenvswitchOtherConfig(node.get(), LOCAL_IP);
            if (localIp == null) {
                LOG.error("missing local_ip key in ovsdb:openvswitch-other-configs in operational"
                        + " network-topology for node: " + node.get().getNodeId().getValue());
            } else {
                return localIp;
            }
        }

        return null;
    }

    @Nonnull
    private Map<String, String> getDpnLocalIps(BigInteger dpId, ReadTransaction tx) throws ReadFailedException {
        // Example of local IPs from other_config:
        // local_ips="10.0.43.159:MPLS,11.11.11.11:DSL,ip:underlay-network"
        return getPortsNode(dpId, tx).toJavaUtil().map(
            node -> elanBridgeManager.getOpenvswitchOtherConfigMap(node, LOCAL_IPS)).orElse(Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Optional<Node> getPortsNode(BigInteger dpnId, ReadTransaction tx) throws ReadFailedException {
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));

        // FIXME: Read this through a cache
        Optional<BridgeRefEntry> optionalBridgeRefEntry =
                tx.read(LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath).checkedGet();
        if (!optionalBridgeRefEntry.isPresent()) {
            LOG.error("no bridge ref entry found for dpnId: " + dpnId);
            return Optional.absent();
        }

        InstanceIdentifier<Node> nodeId =
                optionalBridgeRefEntry.get().getBridgeReference().getValue().firstIdentifierOf(Node.class);

        // FIXME: Read this through a cache
        Optional<Node> optionalNode = tx.read(LogicalDatastoreType.OPERATIONAL, nodeId).checkedGet();
        if (!optionalNode.isPresent()) {
            LOG.error("missing node for dpnId: " + dpnId);
        }
        return optionalNode;
    }

    private String getTzNameForUnderlayNetwork(String zoneNamePrefix, String underlayNetworkName) {
        return zoneNamePrefix + IP_NETWORK_ZONE_NAME_DELIMITER + underlayNetworkName;
    }

    private Optional<String> getZonePrefixForUnderlayNetwork(String zoneName, String underlayNetworkName) {
        String[] zoneParts = zoneName.split(IP_NETWORK_ZONE_NAME_DELIMITER + underlayNetworkName);
        return zoneParts.length == 2 ? Optional.of(zoneParts[0]) : Optional.absent();
    }
}
