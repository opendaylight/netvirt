/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class DhcpServiceUtils {

    private static final Logger logger = LoggerFactory.getLogger(DhcpServiceUtils.class);

    public static Interface getInterfaceFromConfigDS(String interfaceName, DataBroker dataBroker) {
        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        InstanceIdentifier<Interface> interfaceId = getInterfaceIdentifier(interfaceKey);
        Optional<Interface> interfaceOptional = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, interfaceId, dataBroker);
        if (!interfaceOptional.isPresent()) {
            return null;
        }
        return interfaceOptional.get();
    }

    private static InstanceIdentifier<Interface> getInterfaceIdentifier(InterfaceKey interfaceKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> interfaceInstanceIdentifierBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, interfaceKey);
        return interfaceInstanceIdentifierBuilder.build();
    }

    public static void setupDhcpFlowEntry(BigInteger dpId, short tableId, String vmMacAddress, int addOrRemove, IMdsalApiManager mdsalUtil) {
        if (dpId == null || dpId.equals(DHCPMConstants.INVALID_DPID) || vmMacAddress == null) {
            return;
        }
        List<MatchInfo> matches = getDhcpMatch(vmMacAddress);

        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();

        // Punt to controller
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller,
                new String[] {}));
        instructions.add(new InstructionInfo(InstructionType.write_actions,
                actionsInfos));
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress),
                    DHCPMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                    DHCPMConstants.COOKIE_DHCP_BASE, matches, null);
            logger.trace("Removing DHCP Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            mdsalUtil.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress),DHCPMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                    DHCPMConstants.COOKIE_DHCP_BASE, matches, instructions);
            logger.trace("Installing DHCP Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            mdsalUtil.installFlow(flowEntity);
        }
    }

    private static String getDhcpFlowRef(BigInteger dpId, long tableId, String vmMacAddress) {
        return new StringBuffer().append(DHCPMConstants.FLOWID_PREFIX)
                .append(dpId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR)
                .append(vmMacAddress).toString();
    }

    public static void setupDhcpDropAction(BigInteger dpId, short tableId, String vmMacAddress, int addOrRemove, IMdsalApiManager mdsalUtil) {
        if (dpId == null || dpId.equals(DHCPMConstants.INVALID_DPID) || vmMacAddress == null) {
            return;
        }
        List<MatchInfo> matches = getDhcpMatch(vmMacAddress);

        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.write_actions, actionsInfos));
        // Drop Action
        actionsInfos.add(new ActionInfo(ActionType.drop_action,
                new String[] {}));
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress),
                    DHCPMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                    DHCPMConstants.COOKIE_DHCP_BASE, matches, null);
            logger.trace("Removing DHCP Drop Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            mdsalUtil.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    getDhcpFlowRef(dpId, tableId, vmMacAddress),DHCPMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                    DHCPMConstants.COOKIE_DHCP_BASE, matches, instructions);
            logger.trace("Installing DHCP Drop Flow DpId {}, vmMacAddress {}", dpId, vmMacAddress);
            mdsalUtil.installFlow(flowEntity);
        }
    }

    private static List<MatchInfo> getDhcpMatch(String vmMacAddress) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new MatchInfo(MatchFieldType.ip_proto,
                new long[] { IPProtocols.UDP.intValue() }));
        matches.add(new MatchInfo(MatchFieldType.udp_src,
                new long[] { DHCPMConstants.dhcpClientPort }));
        matches.add(new MatchInfo(MatchFieldType.udp_dst,
                new long[] { DHCPMConstants.dhcpServerPort }));
        matches.add(new MatchInfo(MatchFieldType.eth_src,
                new String[] { vmMacAddress }));
        return matches;
    }

    public static List<BigInteger> getListOfDpns(DataBroker broker) {
        List<BigInteger> dpnsList = new LinkedList<BigInteger>();
        InstanceIdentifier<Nodes> nodesInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).build();
        Optional<Nodes> nodesOptional = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, nodesInstanceIdentifier);
        if (!nodesOptional.isPresent()) {
            return dpnsList;
        }
        Nodes nodes = nodesOptional.get();
        List<Node> nodeList = nodes.getNode();
        for (Node node : nodeList) {
            NodeId nodeId = node.getId();
            if (nodeId == null) {
                continue;
            }
            BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeId);
            dpnsList.add(dpnId);
        }
        return dpnsList;
    }

    public static List<BigInteger> getDpnsForElan(String elanInstanceName, DataBroker broker) {
        List<BigInteger> elanDpns = new LinkedList<BigInteger>();
        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInstanceIdentifier = InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();
        Optional<ElanDpnInterfacesList> elanDpnOptional = MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInstanceIdentifier);
        if (elanDpnOptional.isPresent()) {
            List<DpnInterfaces> dpns = elanDpnOptional.get().getDpnInterfaces();
            for (DpnInterfaces dpnInterfaces : dpns) {
                elanDpns.add(dpnInterfaces.getDpId());
            }
        }
        return elanDpns;
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface getInterfaceFromOperationalDS(String interfaceName, DataBroker dataBroker) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey interfaceKey = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey(interfaceName);
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceId = InstanceIdentifier.builder(InterfacesState.class).child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.class, interfaceKey).build();
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceOptional = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, interfaceId, dataBroker);
        if (!interfaceOptional.isPresent()) {
            return null;
        }
        return interfaceOptional.get();
    }


    public static String getSegmentationId(Uuid networkId, DataBroker broker) {
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(Networks.class).child
                (Network.class, new NetworkKey(networkId));
        Optional<Network> optionalNetwork = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (!optionalNetwork.isPresent()) {
            return null;
        }
        Network network = optionalNetwork.get();
        String segmentationId = NeutronUtils.getSegmentationIdFromNeutronNetwork(network);
        return segmentationId;
    }

    public static String getNodeIdFromDpnId(BigInteger dpnId) {
        return MDSALUtil.NODE_PREFIX + MDSALUtil.SEPARATOR + dpnId.toString();
    }

    public static String getTrunkPortMacAddress(String parentRefName,
            DataBroker broker) {
        InstanceIdentifier<Port> portInstanceIdentifier = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
        Optional<Port> trunkPort = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, portInstanceIdentifier);
        if (!trunkPort.isPresent()) {
            logger.warn("Trunk port {} not available for sub-port", parentRefName);
            return null;
        }
        return trunkPort.get().getMacAddress();
    }
}