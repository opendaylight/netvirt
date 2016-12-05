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
import java.util.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZonesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransportZoneNotificationUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TransportZoneNotificationUtil.class);
    private static final String INTERFACE_PORT_SEPARATOR = ".";
    private static final String TUNNEL_PORT = "tunnel_port";
    private static final String LOCAL_IP = "local_ip";
    private static final String ALL_SUBNETS = "0.0.0.0/0";
    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;
    private final SouthboundUtils southBoundUtils;
    private final IInterfaceManager interfaceManager;
    private ElanConfig elanConfig;

    public TransportZoneNotificationUtil(DataBroker dbx, IInterfaceManager interfaceManager, ElanConfig elanConfig) {
        this.dataBroker = dbx;
        this.interfaceManager = interfaceManager;
        this.elanConfig = elanConfig;
        this.mdsalUtils = new MdsalUtils(dbx);
        southBoundUtils = new SouthboundUtils(mdsalUtils);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateTransportZone(List<String> elanInterfaces) {
        boolean noUpdates = true;

        InstanceIdentifier<ElanDpnInterfaces> elanDpnInterfacesInst =
                InstanceIdentifier.create(ElanDpnInterfaces.class);

        ElanDpnInterfaces elanDpnInterfaces = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesInst);

        if (elanDpnInterfaces == null || elanDpnInterfaces.getElanDpnInterfacesList() == null
                || elanDpnInterfaces.getElanDpnInterfacesList().isEmpty()) {
            LOG.debug("No elan dpn interfaces found.");
            return;
        }

        for (String elanInterface : elanInterfaces) {
            Optional<ElanDpnInterfacesList> elanDpnInterfacesList =
                    elanDpnInterfaces.getElanDpnInterfacesList().stream().filter(e -> e.getDpnInterfaces().stream()
                            .anyMatch(di -> di.getInterfaces().contains(elanInterface))).findFirst();

            if (!elanDpnInterfacesList.isPresent()) {
                LOG.debug("Elan interface {} not found in elan dpn interfaces.", elanInterface);
                continue;
            }

            Optional<DpnInterfaces> dpnInterfaces = elanDpnInterfacesList.get().getDpnInterfaces().stream()
                    .filter(di -> di.getInterfaces().contains(elanInterface)).findFirst();

            String subnetIp = ALL_SUBNETS;
            BigInteger dpnId = dpnInterfaces.get().getDpId();

            // Get the transport zone for the elan instance
            InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class).child(
                    TransportZone.class, new TransportZoneKey(elanDpnInterfacesList.get().getElanInstanceName()));
            TransportZone zone = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, inst);

            if (zone == null) {
                zone = createZone(subnetIp, elanDpnInterfacesList.get().getElanInstanceName());
            }

            try {
                if (addVtep(zone, subnetIp, dpnId) > 0) {
                    addTransportZone(zone, elanInterface);
                    noUpdates = false;
                }

            } catch (Exception e) {
                LOG.error("Failed to add tunnels for elan interface {}", elanInterface, e);
            }
        }

        if (noUpdates) {
            LOG.warn("No interfaces were found for transport zone update.");
        }
    }

    /**
     * Update/add TransportZone for interface State inter.<br>
     * If Transport zone for given Network doesn't exist, then it will be added.
     * <br>
     * If the TEP of the port's node exists in the TZ, it will not be added.
     *
     * @param interfaceStateFromOperDS
     *            - the interface to update
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateTransportZone(InterfaceInfo interfaceStateFromOperDS) {
        LOG.debug("Updating transport zone for interface {}", interfaceStateFromOperDS.getInterfaceName());
        List<ElanInterface> elanInterfaces = getElanInterfacesFromInterface(interfaceStateFromOperDS);

        // supports VPN aware VMs (multiple elanInterfaces for one interface)
        for (ElanInterface elanInter : elanInterfaces) {
            if (!checkIfVxlanNetwork(elanInter)) {
                continue;
            }

            String subnetIp = ALL_SUBNETS;
            BigInteger dpnId = interfaceStateFromOperDS.getDpId();
            InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class)
                    .child(TransportZone.class, new TransportZoneKey(elanInter.getElanInstanceName()));

            // Get the transport zone for the elan instance
            TransportZone zone = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, inst);

            if (zone == null) {
                zone = createZone(subnetIp, elanInter.getElanInstanceName());
            }

            try {
                if (addVtep(zone, subnetIp, dpnId) > 0) {
                    addTransportZone(zone, interfaceStateFromOperDS.getInterfaceName());
                }

            } catch (Exception e) {
                LOG.error("Failed to add tunnels on interface added to subnet {}", interfaceStateFromOperDS, e);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateTransportZone(BridgeRefEntry bridgeRefEntry) {
        try {
            LOG.debug("Updating transport zone for bridgeRef with dpId {}", bridgeRefEntry.getDpid());
            BigInteger dpId = bridgeRefEntry.getDpid();
            InstanceIdentifier<VpnInstanceOpData> inst = InstanceIdentifier.create(VpnInstanceOpData.class);
            VpnInstanceOpData vpnInstances = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, inst);

            if (vpnInstances != null && vpnInstances.getVpnInstanceOpDataEntry() != null) {
                for (VpnInstanceOpDataEntry vpnInstanceEntry : vpnInstances.getVpnInstanceOpDataEntry()) {
                    if (vpnInstanceEntry.getVpnToDpnList().stream().anyMatch(vtd -> vtd.getDpnId() == dpId)) {
                        updateTransportZone(vpnInstanceEntry);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to add tunnels on bridgeRef with dpId {}", bridgeRefEntry.getDpid(), e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateTransportZone(VpnInstanceOpDataEntry vpnInstance) {
        try {
            LOG.debug("Updating transport zone for vpn instance {}", vpnInstance.getVpnInstanceName());
            String routerId = vpnInstance.getVpnInstanceName();
            InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class)
                    .child(TransportZone.class, new TransportZoneKey(routerId));
            TransportZone zone = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, inst);
            String subnetIp = ALL_SUBNETS;

            if (zone == null) {
                zone = createZone(subnetIp, routerId);
            }

            int addedTeps = 0;
            if (vpnInstance != null && vpnInstance.getVpnToDpnList() != null) {
                boolean shouldCreateVtep = vpnInstance.getVpnToDpnList().stream().anyMatch(v -> shouldCreateVtep(v));
                if (shouldCreateVtep) {
                    for (VpnToDpnList vpnToDpn : vpnInstance.getVpnToDpnList()) {
                        addedTeps += addVtep(zone, subnetIp, vpnToDpn.getDpnId());
                    }
                }
            }

            if (addedTeps > 0) {
                addTransportZone(zone, "router " + routerId);
            }
        } catch (Exception e) {
            LOG.warn("Failed to add tunnels on router added: {}", vpnInstance, e);
        }
    }

    public boolean isAutoTunnelConfigEnabled() {
        Boolean useTZ = true;
        if (elanConfig != null && elanConfig.isUseTransportZone() != null) {
            useTZ = elanConfig.isUseTransportZone();
        }

        LOG.info("isAutoTunnelConfigEnabled: useTz: {}, elanConfig: {}", useTZ, elanConfig);
        return useTZ;
    }

    private boolean shouldCreateVtep(VpnToDpnList vpnToDpn) {
        List<VpnInterfaces> vpnInterfaces = vpnToDpn.getVpnInterfaces();

        if (vpnInterfaces == null || vpnInterfaces.isEmpty()) {
            return false;
        }

        for (VpnInterfaces vpnInterface : vpnInterfaces) {
            String interfaceName = vpnInterface.getInterfaceName();

            InstanceIdentifier<ElanInterface> pathElanInt = InstanceIdentifier.builder(ElanInterfaces.class)
                    .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();

            ElanInterface elanInt = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, pathElanInt);
            if (elanInt == null) {
                continue;
            }

            if (checkIfVxlanNetwork(elanInt)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfVxlanNetwork(ElanInterface elanInter) {
        InstanceIdentifier<ElanInstance> elanInstancePath = InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInter.getElanInstanceName()));
        ElanInstance elanInstance = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, elanInstancePath);

        if (elanInstance == null) {
            LOG.debug("elanInstance {} not found for elanInter", elanInter.getElanInstanceName(), elanInter.getName());
            return false;
        }

        if (!ElanUtils.isVxlan(elanInstance)) {
            LOG.debug("elanInterface in a non-VXLAN elanInstance " + elanInter.getName());
            return false;
        }

        return true;
    }

    /*
     * takes all elan interface ports that are related to the given interface
     * state
     *
     * @param interfaceState - interface state to update
     *
     * @return - list of ports bound to interface
     */
    private List<ElanInterface> getElanInterfacesFromInterface(InterfaceInfo interfaceStateFromOperDS) {
        List<ElanInterface> elanInterList = new ArrayList<>();

        InstanceIdentifier<Interfaces> interPath = InstanceIdentifier.create(Interfaces.class);
        Interfaces interfaces = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, interPath);
        if (interfaces == null) {
            LOG.error("No interfaces in configuration");
            return elanInterList;
        }

        List<Interface> inters = interfaces.getInterface();
        // take all interfaces with parent-interface with physPortId name
        for (Interface inter : inters) {
            ParentRefs parent = inter.getAugmentation(ParentRefs.class);
            if (parent == null || !interfaceStateFromOperDS.getPortName().equals(parent.getParentInterface())) {
                continue;
            }
            String intName = inter.getName();
            InstanceIdentifier<ElanInterface> pathElanInt = InstanceIdentifier.builder(ElanInterfaces.class)
                    .child(ElanInterface.class, new ElanInterfaceKey(intName)).build();
            ElanInterface elanInt = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, pathElanInt);
            if (elanInt == null) {
                LOG.debug("Got Interface State of a non elan interface {}", interfaceStateFromOperDS.getPortName());
                continue;
            }
            elanInterList.add(elanInt);
        }

        return elanInterList;
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

    private void addTransportZone(TransportZone zone, String interName) {
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        TransportZones zones = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, path);
        if (zones == null) {
            // Create the zones and add the transport zone
            List<TransportZone> zoneList = new ArrayList<>();
            zoneList.add(zone);
            zones = new TransportZonesBuilder().setTransportZone(zoneList).build();
        } else {
            // Add the transport zone
            zones.getTransportZone().add(zone);
        }

        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, path, zones);
        LOG.info("Transport zone {} updated due to {} handling.", zone.getZoneName(), interName);
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
