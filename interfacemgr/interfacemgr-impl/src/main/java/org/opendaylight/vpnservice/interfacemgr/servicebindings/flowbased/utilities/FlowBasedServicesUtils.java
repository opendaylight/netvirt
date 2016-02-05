/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.utilities;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class FlowBasedServicesUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FlowBasedServicesUtils.class);

    public static ServicesInfo getServicesInfoForInterface(String interfaceName, DataBroker dataBroker) {
        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(interfaceName);
        InstanceIdentifier.InstanceIdentifierBuilder<ServicesInfo> servicesInfoIdentifierBuilder =
                InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, servicesInfoKey);
        Optional<ServicesInfo> servicesInfoOptional = IfmUtil.read(LogicalDatastoreType.CONFIGURATION,
                servicesInfoIdentifierBuilder.build(), dataBroker);

        if (servicesInfoOptional.isPresent()) {
            return servicesInfoOptional.get();
        }

        return null;
    }

    public static NodeConnectorId getNodeConnectorIdFromInterface(Interface iface, DataBroker dataBroker) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(iface.getName(), dataBroker);
        if(ifState != null) {
            List<String> ofportIds = ifState.getLowerLayerIf();
            return new NodeConnectorId(ofportIds.get(0));
        }
        return null;
    }

    public static List<MatchInfo> getMatchInfoForVlanPortAtIngressTable(BigInteger dpId, long portNo, Interface iface) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {dpId, BigInteger.valueOf(portNo)}));
        int vlanId = 0;
        IfL2vlan l2vlan = iface.getAugmentation(IfL2vlan.class);
        if(l2vlan != null && l2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Transparent){
            vlanId = l2vlan.getVlanId() == null ? 0 : l2vlan.getVlanId().getValue();
        }
        if (vlanId > 0) {
            matches.add(new MatchInfo(MatchFieldType.vlan_vid, new long[]{vlanId}));
        }
        return matches;
    }

    public static List<MatchInfo> getMatchInfoForTunnelPortAtIngressTable(BigInteger dpId, long portNo, Interface iface) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[]{dpId, BigInteger.valueOf(portNo)}));
        return matches;
    }

    public static List<MatchInfo> getMatchInfoForDispatcherTable(BigInteger dpId, Interface iface,
                                                                 int interfaceTag, short servicePriority) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getMetaDataForLPortDispatcher(interfaceTag, servicePriority),
                MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));
        return matches;
    }

    public static Long getLPortTag(Interface iface, DataBroker dataBroker) {
        /*ParentRefs parentRefs = iface.getAugmentation(ParentRefs.class);
        String portName = parentRefs.getParentInterface();
        BigInteger dpIdFromInterface = parentRefs.getDatapathNodeIdentifier();
        String portKey = FlowBasedServicesUtils.getInterfaceRefInfo(dpIdFromInterface.toString(), portName);
        if (iface.getType().isAssignableFrom(L2vlan.class)) {
            InterfacesMetaKey interfacesMetaKey = new InterfacesMetaKey(portKey);
            InterfacesInfoKey interfacesInfoKey = new InterfacesInfoKey(iface.getName());
            InterfacesInfo interfacesInfo = VlanInterfaceUtilities.getInterfacesInfoFromConfigDS(interfacesMetaKey,
                    interfacesInfoKey, dataBroker);
            return interfacesInfo.getLporttag();
        } else if (iface.getType().isAssignableFrom(Tunnel.class)) {
            TunnelInterfaceRefInfoKey tunnelInterfaceRefInfoKey = new TunnelInterfaceRefInfoKey(portKey);
            TunnelInterfaceEntries tunnelInterfaceEntries =
                    TunnelInterfaceUtilities.getTunnelInterfaceRefEntriesFromConfigDs(
                            tunnelInterfaceRefInfoKey, iface.getName(), dataBroker);
            return tunnelInterfaceEntries.getLportTag();
        } */
        return 0L;
    }

    public static void installInterfaceIngressFlow(BigInteger dpId, Interface iface,
                                                   BoundServices boundServiceNew,
                                                   WriteTransaction t,
                                                   List<MatchInfo> matches, int lportTag, short tableId) {
        List<Instruction> instructions = boundServiceNew.getAugmentation(StypeOpenflow.class).getInstruction();

        int serviceInstructionsSize = instructions.size();
        List<Instruction> instructionSet = new ArrayList<Instruction>();
        int vlanId = 0;
        IfL2vlan l2vlan = iface.getAugmentation(IfL2vlan.class);
        if(l2vlan != null && l2vlan.getVlanId() != null){
            vlanId = l2vlan.getVlanId().getValue();
        }
        if (vlanId != 0) {
            // incrementing instructionSize and using it as actionKey. Because it won't clash with any other instructions
            int actionKey = ++serviceInstructionsSize;
            instructionSet.add(MDSALUtil.buildAndGetPopVlanActionInstruction(actionKey, ++serviceInstructionsSize));
        }

        if (lportTag != 0L) {
            BigInteger[] metadataValues = IfmUtil.mergeOpenflowMetadataWriteInstructions(instructions);
            short sIndex = boundServiceNew.getServicePriority();
            BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(lportTag,
                    ++sIndex, metadataValues[0]);
            BigInteger metadataMask = MetaDataUtil.getMetaDataMaskForLPortDispatcher(
                    MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                    MetaDataUtil.METADATA_MASK_LPORT_TAG, metadataValues[1]);
            instructionSet.add(MDSALUtil.buildAndGetWriteMetadaInstruction(metadata, metadataMask,
                    ++serviceInstructionsSize));
        }

        if (instructions != null && !instructions.isEmpty()) {
            for (Instruction info : instructions) {
                // Skip meta data write as that is handled already
                if (info.getInstruction() instanceof WriteMetadataCase) {
                    continue;
                }
                instructionSet.add(info);
            }
        }

        String serviceRef = boundServiceNew.getServiceName();
        String flowRef = getFlowRef(dpId, iface.getName(), boundServiceNew, boundServiceNew.getServicePriority());
        StypeOpenflow stypeOpenflow = boundServiceNew.getAugmentation(StypeOpenflow.class);
        Flow ingressFlow = MDSALUtil.buildFlowNew(tableId, flowRef,
                stypeOpenflow.getFlowPriority(), serviceRef, 0, 0,
                stypeOpenflow.getFlowCookie(), matches, instructionSet);
        installFlow(dpId, ingressFlow, t);
    }

    public static void installFlow(BigInteger dpId, Flow flow, WriteTransaction t) {
        FlowKey flowKey = new FlowKey(new FlowId(flow.getId()));
        Node nodeDpn = buildInventoryDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class,flowKey).build();

        t.put(LogicalDatastoreType.CONFIGURATION, flowInstanceId, flow, true);
    }

    public static void removeFlow(String flowRef, BigInteger dpId, WriteTransaction t) {
        LOG.debug("Removing Ingress Flows");
        FlowKey flowKey = new FlowKey(new FlowId(flowRef));
        Node nodeDpn = buildInventoryDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(NwConstants.VLAN_INTERFACE_INGRESS_TABLE)).child(Flow.class, flowKey).build();

        t.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
    }

    private static Node buildInventoryDpnNode(BigInteger dpnId) {
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();

        return nodeDpn;
    }

    public static void installLPortDispatcherFlow(BigInteger dpId, BoundServices boundService, Interface iface,
                                                  WriteTransaction t, int interfaceTag, short currentServiceIndex, short nextServiceIndex) {
        LOG.debug("Installing LPort Dispatcher Flows {}, {}", dpId, iface);
        String serviceRef = boundService.getServiceName();
        List<MatchInfo> matches = FlowBasedServicesUtils.getMatchInfoForDispatcherTable(dpId, iface,
                interfaceTag, currentServiceIndex);

        // Get the metadata and mask from the service's write metadata instruction
        StypeOpenflow stypeOpenFlow = boundService.getAugmentation(StypeOpenflow.class);
        List<Instruction> serviceInstructions = stypeOpenFlow.getInstruction();
        int instructionSize = serviceInstructions.size();
        BigInteger[] metadataValues = IfmUtil.mergeOpenflowMetadataWriteInstructions(serviceInstructions);
        BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(interfaceTag, nextServiceIndex, metadataValues[0]);
        BigInteger metadataMask = MetaDataUtil.getMetaDataMaskForLPortDispatcher(MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                MetaDataUtil.METADATA_MASK_LPORT_TAG, metadataValues[1]);

        // build the final instruction for LPort Dispatcher table flow entry
        List<Instruction> instructions = new ArrayList<Instruction>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(metadata, metadataMask, ++instructionSize));
        if (serviceInstructions != null && !serviceInstructions.isEmpty()) {
            for (Instruction info : serviceInstructions) {
                // Skip meta data write as that is handled already
                if (info.getInstruction() instanceof WriteMetadataCase) {
                    continue;
                }
                instructions.add(info);
            }
        }

        // build the flow and install it
        String flowRef = getFlowRef(dpId, iface.getName(), boundService, currentServiceIndex);
        Flow ingressFlow = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef,
                boundService.getServicePriority(), serviceRef, 0, 0, stypeOpenFlow.getFlowCookie(), matches, instructions);
        installFlow(dpId, ingressFlow, t);
    }

    public static void removeIngressFlow(Interface iface, BoundServices serviceOld, BigInteger dpId, WriteTransaction t) {
        LOG.debug("Removing Ingress Flows");
        String flowKeyStr = getFlowRef(dpId, iface.getName(), serviceOld, serviceOld.getServicePriority());
        FlowKey flowKey = new FlowKey(new FlowId(flowKeyStr));
        Node nodeDpn = buildInventoryDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(NwConstants.VLAN_INTERFACE_INGRESS_TABLE)).child(Flow.class, flowKey).build();

        t.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
    }

    public static void removeLPortDispatcherFlow(BigInteger dpId, Interface iface, BoundServices boundServicesOld, WriteTransaction t, short currentServiceIndex) {
        LOG.debug("Removing LPort Dispatcher Flows {}, {}", dpId, iface);

        StypeOpenflow stypeOpenFlow = boundServicesOld.getAugmentation(StypeOpenflow.class);
        // build the flow and install it
        String flowRef = getFlowRef(dpId, iface.getName(), boundServicesOld, currentServiceIndex);
        FlowKey flowKey = new FlowKey(new FlowId(flowRef));
        Node nodeDpn = buildInventoryDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(stypeOpenFlow.getDispatcherTableId())).child(Flow.class, flowKey).build();

        t.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
    }

    private static String getFlowRef(BigInteger dpnId, String iface, BoundServices service, short currentServiceIndex) {
        return new StringBuffer().append(dpnId).append(NwConstants.VLAN_INTERFACE_INGRESS_TABLE).append(NwConstants.FLOWID_SEPARATOR)
                .append(iface).append(NwConstants.FLOWID_SEPARATOR).append(currentServiceIndex).toString();
    }

    /**
     * This util method returns an array of ServiceInfo in which index 0 will
     * have the immediate lower priority service and index 1 will have the
     * immediate higher priority service among the list of existing serviceInfos
     *
     * @param serviceInfos
     * @param currentServiceInfo
     * @return
     */
 public static BoundServices[] getHighAndLowPriorityService(
            List<BoundServices> serviceInfos, BoundServices currentServiceInfo) {
    BoundServices higher = null; // this will be used to hold the immediate higher service priority with respect to the currentServiceInfo
    BoundServices lower = null; // this will be used to hold the immediate lower service priority with respect to the currentServiceInfo
        if (serviceInfos == null || serviceInfos.isEmpty()) {
            return new BoundServices[]{lower, higher};
        }
        List <BoundServices> availableServiceInfos = new ArrayList<BoundServices>(serviceInfos);
        Collections.sort(availableServiceInfos, new Comparator<BoundServices>() {
            @Override
            public int compare(BoundServices serviceInfo1, BoundServices serviceInfo2) {
                return serviceInfo2.getServicePriority().compareTo(serviceInfo1.getServicePriority());
            }
        });
        for (BoundServices availableServiceInfo: availableServiceInfos) {
            if (currentServiceInfo.getServicePriority() > availableServiceInfo.getServicePriority()) {
                lower = availableServiceInfo;
                break;
            } else {
                higher = availableServiceInfo;
            }
        }
        return new BoundServices[]{lower,higher};
    }

    public static BoundServices getHighestPriorityService(List<BoundServices> serviceInfos) {
        List <BoundServices> availableServiceInfos = new ArrayList<BoundServices>(serviceInfos);
        if (availableServiceInfos.isEmpty()) {
            return null;
        }
        BoundServices highPriorityService = availableServiceInfos.get(0);
        availableServiceInfos.remove(0);
        for (BoundServices availableServiceInfo: availableServiceInfos) {
            if (availableServiceInfo.getServicePriority() > highPriorityService.getServicePriority()) {
                highPriorityService = availableServiceInfo;
            }
        }
        return highPriorityService;
    }

    public static void installVlanFlow(BigInteger dpId, long portNo, Interface iface, 
            WriteTransaction t, List<MatchInfo> matches, int lportTag) {
        int vlanId = 0;
        boolean isVlanTransparent = false;
        IfL2vlan l2vlan = iface.getAugmentation(IfL2vlan.class);
        if(l2vlan != null){
            vlanId = l2vlan.getVlanId() == null ? 0 : l2vlan.getVlanId().getValue();
            isVlanTransparent = l2vlan.getL2vlanMode() == IfL2vlan.L2vlanMode.Transparent;
        }
        int instructionKey = 0;
        BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(lportTag, (short) 0);
        BigInteger metadataMask = MetaDataUtil.getMetaDataMaskForLPortDispatcher();
        List<Instruction> instructions = new ArrayList<Instruction>();
        if (vlanId != 0 && !isVlanTransparent) {
            instructions.add(MDSALUtil.buildAndGetPopVlanActionInstruction(lportTag, instructionKey++));
        }
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(metadata, metadataMask, instructionKey++));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.DHCP_TABLE, instructionKey++));
        int priority =  isVlanTransparent ? 1 : vlanId == 0 ? IfmConstants.FLOW_PRIORITY_FOR_UNTAGGED_VLAN : IfmConstants.FLOW_HIGH_PRIORITY;
        String flowRef = getFlowRef(IfmConstants.VLAN_INTERFACE_INGRESS_TABLE, dpId, iface.getName());
        Flow ingressFlow = MDSALUtil.buildFlowNew(IfmConstants.VLAN_INTERFACE_INGRESS_TABLE, flowRef, priority, flowRef, 0, 0,
                IfmConstants.VLAN_TABLE_COOKIE, matches, instructions);
        installFlow(dpId, ingressFlow, t);
}

    public static String getFlowRef(short tableId, BigInteger dpnId, String infName) {
        return String.format("%d:%s:%s", tableId, dpnId, infName);
    }

    public static void removeIngressFlow(String interfaceName, BigInteger dpId, WriteTransaction t) {
        LOG.debug("Removing Ingress Flows");
        String flowKeyStr = getFlowRef(IfmConstants.VLAN_INTERFACE_INGRESS_TABLE, dpId, interfaceName);
        FlowKey flowKey = new FlowKey(new FlowId(flowKeyStr));
        Node nodeDpn = buildInventoryDpnNode(dpId);
        InstanceIdentifier<Flow> flowInstanceId = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(NwConstants.VLAN_INTERFACE_INGRESS_TABLE)).child(Flow.class, flowKey).build();

        t.delete(LogicalDatastoreType.CONFIGURATION, flowInstanceId);
    }


}
