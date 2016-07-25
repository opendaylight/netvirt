/*
 * Copyright (c) 2015 - 2016 HPE and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.ovsdb.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceStateManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceStateToTransportZoneListener.class);
    private static final String OF_URI_SEPARATOR = ":";
    private static final String TUNNEL_PORT = "tunnel_port";
    private static final String LOCAL_IP = "local_ip";

    private DataBroker dataBroker;
    private NeutronvpnManager nvManager;
    private MdsalUtils mdsalUtils;
    private SouthboundUtils southBoundUtils;

    public InterfaceStateManager(DataBroker dbx, NeutronvpnManager nvManager) {
        this.dataBroker = dbx;
        this.nvManager = nvManager;
        this.mdsalUtils = new MdsalUtils(dbx);
        southBoundUtils = new SouthboundUtils(mdsalUtils);
    }


    /**
     * Update/add TransportZone for interface State inter.<br>
     * If Transport zone for given Network doesn't exist, then it will be added.<br>
     * If the TEP of the port's node exists in the TZ, it will not be added.
     * @param inter
     */
    public void updateTrasportZone(Interface inter) {
        List<Port> ports = getPortsFromInterface(inter);
        //supports VPN aware VMs (multiple ports for one interface)
        for(Port port : ports){
            try{

                String subnetIp = getSubnetIPFromPort(port);

                BigInteger dpnId = getDpnIdFromInterfaceState(inter);

                InstanceIdentifier<TransportZone> inst = InstanceIdentifier.create(TransportZones.class).child(TransportZone.class, new TransportZoneKey(port.getNetworkId().getValue()));
                TransportZone zone = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, inst);

                if (zone != null) {
                    if (!currentPortNodeTepPresent(zone, dpnId)) {
                        addVtep(zone, subnetIp, port, dpnId);
                        addTransportZone(zone, inter.getName());
                    }
                }else{
                    zone = createZone(subnetIp, port.getNetworkId().getValue());
                    addVtep(zone, subnetIp, port, dpnId);
                    addTransportZone(zone, inter.getName());
                }

            }catch(Exception e){
                LOG.error("faild to add tunnels on port added to subnet", e);
            }       
        }
    }


    private BigInteger getDpnIdFromInterfaceState(Interface inter) {
        String lowerLayerIf = inter.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        BigInteger dpId = new BigInteger(getDpnFromNodeConnectorId(nodeConnectorId));
        return dpId;
    }


    private String getSubnetIPFromPort(Port port) throws Exception {
        FixedIps ip = port.getFixedIps().get(0);

        if(ip == null){
            LOG.error("No fixed ip for port " + port.getName());
            throw new Exception("No fixed ip for port" + port.getName());
        }
        Uuid subnetId = ip.getSubnetId();
        Subnetmap subnetmap = nvManager.updateSubnetmapNodeWithPorts(subnetId, port.getUuid(), null);
        String subnetIp = subnetmap.getSubnetIp();
        return subnetIp;
    }

    /**
     * takes all Neutron Ports that are related to the given interface state
     * @param interfaceState
     * @return
     */
    private List<Port> getPortsFromInterface(Interface interfaceState) {
        String physPortId = getPortFromInterfaceName(interfaceState.getName());
        List<Port> portsList = new ArrayList<Port>();

        InstanceIdentifier<Interfaces> interPath = InstanceIdentifier.create(Interfaces.class);
        Interfaces interfaces = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, interPath);
        if(interfaces == null){
            LOG.error("No interfaces in configuration");
            return portsList;
        }
        List<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface> inters = interfaces.getInterface();

        // take all interfaces with parent-interface with physPortId name
        for(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface inter : inters){
            ParentRefs parent = inter.getAugmentation(ParentRefs.class);
            if(parent == null || !physPortId.equals(parent.getParentInterface())){
                continue;
            }
            String parentInt = inter.getName();
            Uuid portUid = new Uuid(parentInt);
            InstanceIdentifier<Port> pathPort = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class, new PortKey(portUid));
            Port port = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, pathPort);

            if(port == null){
                LOG.debug("got Interface State of non NeutronPort instance " + physPortId);
                continue;
            }

            portsList.add(port);
        }

        return portsList;
    }


    private String getPortFromInterfaceName(String name) {
        String[] splitedStr = name.split(OF_URI_SEPARATOR);
        name = splitedStr.length > 1 ? splitedStr[1] : name; 
        return name;
    }


    // TODO: code is used in another places. Should be extracted into utility 
    private String getDpnFromNodeConnectorId(NodeConnectorId portId) {
        String[] split = portId.getValue().split(OF_URI_SEPARATOR);
        return split[1];
    }



    private TransportZone createZone(String subnetIp, String zoneName) {
        TransportZoneBuilder tzb = new TransportZoneBuilder();
        tzb.setKey(new TransportZoneKey(zoneName));
        tzb.setTunnelType(TunnelTypeVxlan.class);
        tzb.setZoneName(zoneName);
        List<Subnets> subnets = new ArrayList<Subnets>();
        subnets.add(newSubnets(subnetIp));
        tzb.setSubnets(subnets);
        return tzb.build();
    }


    private void addTransportZone(TransportZone zone, String interName) {
        InstanceIdentifier<TransportZones> path = InstanceIdentifier.builder(TransportZones.class).build();
        TransportZones zones = mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, path);
        if(zones == null){
            List<TransportZone> zoneList = new ArrayList<>();
            zoneList.add(zone);
            zones = new TransportZonesBuilder().setTransportZone(zoneList).build();
        }else{
            zones.getTransportZone().add(zone);
        }

        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, path, zones);
        LOG.info("updating transport zone {} due to interface state {} handling", zone.getZoneName(), interName);
    }

    private void addVtep(TransportZone zone, String subnetIp, Port port, BigInteger dpId) throws Exception {

        Subnets subnets = findSubnets(zone.getSubnets(), subnetIp);

        IpAddress nodeIp =  getNodeIP(dpId);

        VtepsBuilder vtepsBuilder = new VtepsBuilder();
        vtepsBuilder.setDpnId(dpId);
        vtepsBuilder.setIpAddress(nodeIp);
        vtepsBuilder.setPortname(TUNNEL_PORT);

        subnets.getVteps().add(vtepsBuilder.build());

    }




    // search for relevant subnets for the given subnetIP, add one if it is necessary
    private Subnets findSubnets(List<Subnets> subnets, String subnetIp) {
        Subnets retSubnet = null;
        for(Subnets subnet : subnets){
            IpPrefix subnetPrefix = new IpPrefix(subnetIp.toCharArray());
            if(subnet.getPrefix().equals(subnetPrefix)){
                retSubnet = subnet;
                break;
            }
        }

        if(retSubnet == null){
            retSubnet = newSubnets(subnetIp);
            subnets.add(retSubnet);
        }
        return retSubnet;
    }

    private Subnets newSubnets(String subnetIp) {
        Subnets retSubnet;
        SubnetsBuilder subnetsBuilder = new SubnetsBuilder();
        subnetsBuilder.setDeviceVteps(new ArrayList<>());
        subnetsBuilder.setGatewayIp(new IpAddress("0.0.0.0".toCharArray()));
        subnetsBuilder.setKey(new SubnetsKey(new IpPrefix(subnetIp.toCharArray())));
        subnetsBuilder.setVlanId(0);
        subnetsBuilder.setVteps(new ArrayList<Vteps>());
        retSubnet = subnetsBuilder.build();
        return retSubnet;
    }

    /**
     * check if the node (TEP) is already exist in the TZ 
     * @param zone
     * @param dpId
     * @return
     * @throws Exception
     */
    private boolean currentPortNodeTepPresent(TransportZone zone, BigInteger dpId) throws Exception {
        List<Subnets> subnetsList = zone.getSubnets();
        if(subnetsList!=null){
            for (Subnets subnet : subnetsList) {
                List<Vteps> vtepsList = subnet.getVteps();
                if(vtepsList!=null && !vtepsList.isEmpty()) {
                    for (Vteps vteps : vtepsList) {
                        BigInteger dpnID = vteps.getDpnId();
                        if(dpnID.equals(dpId)){
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    private IpAddress getNodeIP(BigInteger dpId) throws Exception {
        Node node = getPortsNode(dpId);
        String localIp = southBoundUtils.getOpenvswitchOtherConfig(node, LOCAL_IP);
        if(localIp == null){
            throw new Exception("missing local_ip key in ovsdb:openvswitch-other-configs in operational network-topology for node: " + node.getNodeId().getValue());
        }

        return new IpAddress(localIp.toCharArray());
    }

    @SuppressWarnings("unchecked")
    private Node getPortsNode(BigInteger dpnId) throws Exception{
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class).child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));
        BridgeRefEntry bridgeRefEntry = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath);
        if(bridgeRefEntry == null){
            throw new Exception("no bridge ref entry found for dpnId: " + dpnId);
        }


        InstanceIdentifier<Node> nodeId = ((InstanceIdentifier<OvsdbBridgeAugmentation>) bridgeRefEntry.getBridgeReference().getValue()).firstIdentifierOf(Node.class);
        Node node = mdsalUtils.read(LogicalDatastoreType.OPERATIONAL, nodeId);

        if(node == null){
            throw new Exception("missing node for dpnId: " + dpnId);
        }
        return node;

    }

}
