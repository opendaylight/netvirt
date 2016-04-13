/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeVxlanOverIpv4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profiles.MonitorProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SouthboundUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SouthboundUtils.class);
    public static final String HWVTEP_TOPOLOGY = "hwvtep:1";
    public static final TopologyId HWVTEP_TOPOLOGY_ID = new TopologyId(new Uri(HWVTEP_TOPOLOGY));
    public static final String TEP_PREFIX = "vxlan_over_ipv4:";
    public static final String BFD_OP_STATE = "state";
    public static final String BFD_STATE_UP = "up";
    // BFD parameters
    static final String BFD_PARAM_ENABLE = "enable";
    static final String BFD_PARAM_MIN_RX = "min_rx";
    static final String BFD_PARAM_MIN_TX = "min_tx";
    static final String BFD_PARAM_DECAY_MIN_RX = "decay_min_rx";
    static final String BFD_PARAM_FORWARDING_IF_RX = "forwarding_if_rx";
    static final String BFD_PARAM_CPATH_DOWN = "cpath_down";
    static final String BFD_PARAM_CHECK_TNL_KEY = "check_tnl_key";

    // BFD Local/Remote Configuration parameters
    static final String BFD_CONFIG_BFD_DST_MAC = "bfd_dst_mac";
    static final String BFD_CONFIG_BFD_DST_IP = "bfd_dst_ip";

    // bfd params
    private static final String BFD_MIN_RX_VAL = "1000";
    private static final String BFD_MIN_TX_VAL = "100";
    private static final String BFD_DECAY_MIN_RX_VAL = "200";
    private static final String BFD_FORWARDING_IF_RX_VAL = "true";
    private static final String BFD_CPATH_DOWN_VAL = "false";
    private static final String BFD_CHECK_TNL_KEY_VAL = "false";

    public static InstanceIdentifier<Node> createPhysicalSwitchInstanceIdentifier(String psNodeIdString) {
        NodeId physicalSwitchNodeId = new NodeId(psNodeIdString);
        InstanceIdentifier<Node> psNodeId = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HWVTEP_TOPOLOGY_ID))
                .child(Node.class,new NodeKey(physicalSwitchNodeId));
        return psNodeId;
    }

    public static InstanceIdentifier<Node> createGlobalNodeInstanceIdentifier(DataBroker dataBroker, String physicalSwitchNodeId) {
        InstanceIdentifier<Node> psNodeId = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HWVTEP_TOPOLOGY_ID))
                .child(Node.class,new NodeKey(new NodeId(physicalSwitchNodeId)));
        Optional<Node> physicalSwitchOptional =
                IfmUtil.read(LogicalDatastoreType.OPERATIONAL, psNodeId, dataBroker);
        if (!physicalSwitchOptional.isPresent()) {
            LOG.debug("physical switch is not present for {}", physicalSwitchNodeId);
            return null;
        }
        Node physicalSwitch = physicalSwitchOptional.get();
        PhysicalSwitchAugmentation physicalSwitchAugmentation = physicalSwitch.getAugmentation(PhysicalSwitchAugmentation.class);
        return (InstanceIdentifier<Node>) physicalSwitchAugmentation.getManagedBy().getValue();
    }

    public static InstanceIdentifier<TerminationPoint> createTerminationPointInstanceIdentifier(NodeKey nodekey,
                                                                                                String portName){
        InstanceIdentifier<TerminationPoint> terminationPointPath = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(HWVTEP_TOPOLOGY_ID))
                .child(Node.class,nodekey)
                .child(TerminationPoint.class, new TerminationPointKey(new TpId(portName)));

        LOG.debug("Termination point InstanceIdentifier generated : {}",terminationPointPath);
        return terminationPointPath;
    }

    public static InstanceIdentifier<TerminationPoint> createTEPInstanceIdentifier
            (InstanceIdentifier<Node> nodeIid,  IpAddress ipAddress) {
        TerminationPointKey localTEP = SouthboundUtils.getTerminationPointKey(ipAddress.getIpv4Address().getValue());
        return createInstanceIdentifier(nodeIid, localTEP);
    }

    public static InstanceIdentifier<TerminationPoint> createInstanceIdentifier(InstanceIdentifier<Node> nodeIid,
                                                                                TerminationPointKey tpKey) {
        return nodeIid.child(TerminationPoint.class, tpKey);
    }

    public static InstanceIdentifier<Tunnels> createTunnelsInstanceIdentifier(InstanceIdentifier<Node> nodeId, IpAddress localIP, IpAddress remoteIp) {
        InstanceIdentifier<TerminationPoint> localTEPInstanceIdentifier =
                createTEPInstanceIdentifier(nodeId, localIP);
        InstanceIdentifier<TerminationPoint> remoteTEPInstanceIdentifier =
                createTEPInstanceIdentifier(nodeId, remoteIp);

        TunnelsKey tunnelsKey = new TunnelsKey(new HwvtepPhysicalLocatorRef(localTEPInstanceIdentifier),
                new HwvtepPhysicalLocatorRef(remoteTEPInstanceIdentifier));
        return InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(HWVTEP_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(nodeId.firstKeyOf(Node.class))).augmentation(PhysicalSwitchAugmentation.class)
                .child(Tunnels.class, tunnelsKey).build();
    }

    public static InstanceIdentifier<Tunnels> createTunnelsInstanceIdentifier(InstanceIdentifier<Node> nodeId,
                                                                              InstanceIdentifier<TerminationPoint> localTEPInstanceIdentifier,
                                                                              InstanceIdentifier<TerminationPoint> remoteTEPInstanceIdentifier) {
        TunnelsKey tunnelsKey = new TunnelsKey(new HwvtepPhysicalLocatorRef(localTEPInstanceIdentifier),
                new HwvtepPhysicalLocatorRef(remoteTEPInstanceIdentifier));

        InstanceIdentifier<Tunnels> tunnelInstanceId = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, new TopologyKey(HWVTEP_TOPOLOGY_ID))
                .child(Node.class, new NodeKey(nodeId.firstKeyOf(Node.class))).augmentation(PhysicalSwitchAugmentation.class)
                .child(Tunnels.class, tunnelsKey).build();
        return tunnelInstanceId;
    }

    public static String getTerminationPointKeyString(String ipAddress) {
        String tpKeyStr = null;
        if(ipAddress != null) {
            tpKeyStr = new StringBuilder(TEP_PREFIX).
                    append(ipAddress).toString();
        }
        return tpKeyStr;
    }

    public static TerminationPointKey getTerminationPointKey(String ipAddress) {
        TerminationPointKey tpKey = null;
        String tpKeyStr = getTerminationPointKeyString(ipAddress);
        if(tpKeyStr != null) {
            tpKey = new TerminationPointKey(new TpId(tpKeyStr));
        }
        return tpKey;
    }

    public static TerminationPoint getTEPFromConfigDS(InstanceIdentifier<TerminationPoint> tpPath,
                                                      DataBroker dataBroker) {
        Optional<TerminationPoint> terminationPointOptional =
                IfmUtil.read(LogicalDatastoreType.CONFIGURATION, tpPath, dataBroker);
        if (!terminationPointOptional.isPresent()) {
            return null;
        }
        return terminationPointOptional.get();
    }

    public static void setDstIp(HwvtepPhysicalLocatorAugmentationBuilder tpAugmentationBuilder, IpAddress ipAddress) {
        IpAddress ip = new IpAddress(ipAddress);
        tpAugmentationBuilder.setDstIp(ip);
    }

    public static void addStateEntry(Interface interfaceInfo,  IfTunnel ifTunnel, WriteTransaction transaction) {
        LOG.debug("adding tep interface state for {}", interfaceInfo.getName());
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus operStatus =
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up;
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus adminStatus =
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up;
        if (interfaceInfo != null && !interfaceInfo.isEnabled()) {
            operStatus = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Down;
        }
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                IfmUtil.buildStateInterfaceId(interfaceInfo.getName());
        List<String> childLowerLayerIfList = new ArrayList<>();
        childLowerLayerIfList.add(0, SouthboundUtils.getTerminationPointKeyString(ifTunnel.getTunnelDestination().getIpv4Address().getValue()));
        InterfaceBuilder ifaceBuilder = new InterfaceBuilder().setAdminStatus(adminStatus)
                .setOperStatus(operStatus).setLowerLayerIf(childLowerLayerIfList);


        if(interfaceInfo != null){
            ifaceBuilder.setType(interfaceInfo.getType());
        }
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceInfo.getName()));
        transaction.put(LogicalDatastoreType.OPERATIONAL, ifStateId,ifaceBuilder.build() , true);
    }

    public static void fillBfdParameters(List<BfdParams> bfdParams, IfTunnel ifTunnel) {
        setBfdParamForEnable(bfdParams, ifTunnel != null ? ifTunnel.isMonitorEnabled() :true);
        bfdParams.add(getBfdParams(BFD_PARAM_MIN_TX, ifTunnel != null ?
                ifTunnel.getMonitorInterval().toString() : BFD_MIN_TX_VAL));
        bfdParams.add(getBfdParams(BFD_PARAM_MIN_RX, BFD_MIN_RX_VAL));
        bfdParams.add(getBfdParams(BFD_PARAM_DECAY_MIN_RX, BFD_DECAY_MIN_RX_VAL));
        bfdParams.add(getBfdParams(BFD_PARAM_FORWARDING_IF_RX, BFD_FORWARDING_IF_RX_VAL));
        bfdParams.add(getBfdParams(BFD_PARAM_CPATH_DOWN, BFD_CPATH_DOWN_VAL));
        bfdParams.add(getBfdParams(BFD_PARAM_CHECK_TNL_KEY, BFD_CHECK_TNL_KEY_VAL));
    }

    public static void setBfdParamForEnable(List<BfdParams> bfdParams, boolean isEnabled) {
        bfdParams.add(getBfdParams(BFD_PARAM_ENABLE, Boolean.toString(isEnabled)));
    }

    public static BfdParams getBfdParams(String key, String value) {
        return new BfdParamsBuilder().setBfdParamKey(key).setKey(new BfdParamsKey(key))
                .setBfdParamValue(value).build();
    }
}