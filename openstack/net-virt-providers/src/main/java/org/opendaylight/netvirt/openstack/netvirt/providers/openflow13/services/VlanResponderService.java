/*
 * Copyright (c) 2016 NEC Corporation and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.netvirt.openstack.netvirt.api.ConfigurationService;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.api.NodeCacheManager;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.netvirt.openstack.netvirt.api.TenantNetworkManager;
import org.opendaylight.netvirt.openstack.netvirt.api.VlanResponderProvider;
import org.opendaylight.netvirt.openstack.netvirt.providers.ConfigInterface;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronNetwork;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronNetworkCRUD;
import org.opendaylight.netvirt.utils.mdsal.openflow.FlowUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.InstructionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VlanResponderService extends AbstractServiceInstance implements VlanResponderProvider, ConfigInterface {
    private static final Logger LOG = LoggerFactory.getLogger(VlanResponderService.class);
    private volatile ConfigurationService configurationService;
    private volatile NodeCacheManager nodeCacheManager;
    private volatile TenantNetworkManager tenantNetworkManager;
    private volatile Southbound southbound;
    private volatile INeutronNetworkCRUD neutronNetworkCache;
    private Map<String, ArrayList<ImmutablePair<String, Long>>> segmentationOfPortMap = new HashMap<String, ArrayList<ImmutablePair<String, Long>>>();

    public VlanResponderService() {
        super(Service.OUTBOUND_NAT);
    }

    public VlanResponderService(Service service) {
        super(service);
    }

    /**
     * Write or remove the flows for output instructions based on flag value actions.
     * Sample flow: table=100, n_packets=1192, n_bytes=55496, priority=4,in_port=5,dl_src=fa:16:3e:74:a9:2e actions=output:3
     */
    @Override
    public void programProviderNetworkOutput(Long dpidLong, String segmentationId, Long ofPort, Long patchPort,
                                             String macAddress, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, ofPort);
            matchBuilder = MatchUtils.createEthSrcDestMatch(matchBuilder, new MacAddress(macAddress), new MacAddress("01:00:00:00:00:00"), new MacAddress("01:00:00:00:00:00"));
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "InternalBridge_output_" + dpidLong + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(segmentationId);
            if (macAddrOfPortPairList == null) {
                macAddrOfPortPairList = new ArrayList<ImmutablePair<String, Long>>();
                segmentationOfPortMap.put(segmentationId, macAddrOfPortPairList);
            }
            if (write) {
                ImmutablePair<String, Long> ofPortMacAddressPair = new ImmutablePair<>(macAddress, ofPort);
                if (macAddrOfPortPairList.contains(ofPortMacAddressPair)) {
                    return;
                }
                List<Instruction> outputInstructions = new ArrayList<Instruction>();
                int instructionIndex=0;
                for (ImmutablePair<String, Long> macAddressOfPortPair : macAddrOfPortPairList) {
                    createOutputPortInstruction(instructionIndex, dpidLong, macAddressOfPortPair.getRight(), outputInstructions);
                    instructionIndex++;
                }
                // Set the Output Port/Iface
                Instruction outputPortInstruction = createOutputPortInstructions(new InstructionBuilder(),
                        dpidLong, patchPort, outputInstructions).setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
                macAddrOfPortPairList.add(ofPortMacAddressPair);
            } else {
                removeFlow(flowBuilder, nodeBuilder);
                Iterator<ImmutablePair<String, Long>> iterator = macAddrOfPortPairList.iterator();
                while (iterator.hasNext()) {
                    ImmutablePair<String, Long> macAddressOfPortPair = iterator.next();
                    if (macAddress.equals(macAddressOfPortPair.getLeft())) {
                        iterator.remove();
                        break;
                    }
                }
            }
            programExistingProviderNetworkOutput(dpidLong, segmentationId, ofPort, patchPort, macAddress, write);
        } catch (Exception e) {
            LOG.error("Error while writing/removing output instruction flow. dpidLong = {}, patchPort={}, write = {}."
                    +" Caused due to, {}", dpidLong, patchPort, write, e.getMessage());
        }
    }

    /**
     * Write or remove the flows for NORMAL instructions based on flag value actions.
     * Sample flow: table=100, n_packets=1192, n_bytes=55496, priority=4,in_port=5,dl_src=fa:16:3e:74:a9:2e actions=NORMAL
     */
    @Override
    public void programProviderNetworkNormal(Long dpidLong, Long ofPort, String macAddress, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, ofPort);
            MatchUtils.createEthSrcMatch(matchBuilder, new MacAddress(macAddress));
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "InternalBridge_normal_" + dpidLong + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(3).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                Instruction normalInstruction = InstructionUtils.createNormalInstructions(
                        FlowUtils.getNodeName(dpidLong), new InstructionBuilder()).
                        setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, normalInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                removeFlow(flowBuilder, nodeBuilder);
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing normal instruction flow. dpidLong = {}, write = {}."
                    +" Caused due to, {}", dpidLong, write, e.getMessage());
        }
    }

    private void programExistingProviderNetworkOutput(Long dpidLong, String segmentationId, Long currentOfPort, Long patchPort,
            String macAddress, boolean write) {

        String nodeName = OPENFLOW + dpidLong;
        NodeBuilder nodeBuilder = createNodeBuilder(nodeName);
        FlowBuilder flowBuilder = new FlowBuilder();

        MatchBuilder matchBuilder = new MatchBuilder();
        ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(segmentationId);
        for (ImmutablePair<String, Long> macAddrOfPortPair : macAddrOfPortPairList) {
            Long ofPort = macAddrOfPortPair.getRight();
            if (ofPort.equals(currentOfPort)) {
                continue;
            }
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, ofPort);
            matchBuilder = MatchUtils.createEthSrcDestMatch(matchBuilder, new MacAddress(macAddrOfPortPair.getLeft()), new MacAddress("01:00:00:00:00:00"), new MacAddress("01:00:00:00:00:00"));
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "InternalBridge_output_" + dpidLong + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
            .setPriority(4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);

            if (write) {
                Flow flow = this.getFlow(flowBuilder, nodeBuilder);
                // Retrieve the existing instructions
                List<Instruction> existingInstructions = InstructionUtils.extractExistingInstructions(flow);
                Instruction outputPortInstruction =
                        createOutputPortInstructions(new InstructionBuilder(), dpidLong, currentOfPort, existingInstructions)
                                .setOrder(0)
                                .setKey(new InstructionKey(0))
                                .build();
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                List<Instruction> outputInstructions = new ArrayList<Instruction>();
                int instructionIndex = 0;
                for (ImmutablePair<String, Long> ofPortMacAddrPair : macAddrOfPortPairList) {
                    Long cachedOfPort = ofPortMacAddrPair.getRight();
                    if (cachedOfPort.equals(ofPort) || cachedOfPort.equals(currentOfPort)) {
                        continue;
                    }
                    createOutputPortInstruction(instructionIndex, dpidLong, cachedOfPort, outputInstructions);
                    instructionIndex++;
                }
                Instruction outputPortInstruction = createOutputPortInstructions(new InstructionBuilder(),
                        dpidLong, patchPort, outputInstructions).setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            }
        }
    }

    private void createOutputPortInstruction(int index, Long dpidLong, Long ofPort, List<Instruction> outputInstructions) {
        InstructionBuilder ib = new InstructionBuilder();
        NodeConnectorId ncid = new NodeConnectorId("openflow:" + dpidLong + ":" + ofPort);
        LOG.debug("createOutputPortInstruction() Node Connector ID is - Type=openflow: DPID={} inPort={} ",
                dpidLong, ofPort);

        List<Action> actionList = new ArrayList<>();
        ActionBuilder ab = new ActionBuilder();
        OutputActionBuilder oab = new OutputActionBuilder();
        oab.setOutputNodeConnector(ncid);

        ab.setAction(new OutputActionCaseBuilder().setOutputAction(oab.build()).build());
        ab.setOrder(index);
        ab.setKey(new ActionKey(index));
        actionList.add(ab.build());

        // Create an Apply Action
        ApplyActionsBuilder aab = new ApplyActionsBuilder();
        aab.setAction(actionList);
        ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
        outputInstructions.add(ib.build());
    }

    private InstructionBuilder createOutputPortInstructions(InstructionBuilder ib,
            Long dpidLong, Long port ,
            List<Instruction> instructions) {
        NodeConnectorId ncid = new NodeConnectorId(OPENFLOW + dpidLong + ":" + port);
        LOG.debug("createOutputPortInstructions() Node Connector ID is - Type=openflow: DPID={} port={} existingInstructions={}", dpidLong, port, instructions);

        List<Action> actionList = new ArrayList<>();
        ActionBuilder ab = new ActionBuilder();

        List<Action> existingActions;
        for (Instruction instruction : instructions) {
            if (instruction.getInstruction() instanceof ApplyActionsCase) {
                existingActions = (((ApplyActionsCase) instruction.getInstruction()).getApplyActions().getAction());
                // Only include output actions
                for (Action action : existingActions) {
                    if (action.getAction() instanceof OutputActionCase) {
                        actionList.add(action);
                    }
                }
            }
        }
        /* Create output action for this port*/
        OutputActionBuilder oab = new OutputActionBuilder();
        oab.setOutputNodeConnector(ncid);
        ab.setAction(new OutputActionCaseBuilder().setOutputAction(oab.build()).build());
        ab.setOrder(actionList.size());
        ab.setKey(new ActionKey(actionList.size()));
        actionList.add(ab.build());
        // Create an Apply Action
        ApplyActionsBuilder aab = new ApplyActionsBuilder();
        aab.setAction(actionList);
        ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
        return ib;
    }

    /**
     * Write or remove the flows for POP VLAN instructions based on flag value actions.
     * Sample flow: table=100, n_packets=218, n_bytes=15778, priority=4,in_port=3,dl_vlan=100 actions=pop_vlan,NORMAL
     */
    @Override
    public void programProviderNetworkPopVlan(Long dpidLong, String segmentationId,
                                              Long ofPort, Long patchPort, String macAddress,
                                              Map<String, Set<String>> vlanProviderCache, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            // Match Vlan ID
            MatchUtils.createVlanIdMatch(matchBuilder, new VlanId(Integer.valueOf(segmentationId)), true);
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, patchPort);
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "InternalBridge_popVLAN_" + segmentationId + "_" + patchPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                /* Strip vlan and store to tmp instruction space*/
                Instruction popVlanInstruction = InstructionUtils.createPopVlanAndNormalInstructions(
                        new InstructionBuilder()).setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, popVlanInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                Set<String> lstMacAddress = new HashSet<String>();
                if (vlanProviderCache != null && !vlanProviderCache.isEmpty() &&
                        vlanProviderCache.containsKey(segmentationId)) {
                    lstMacAddress = vlanProviderCache.get(segmentationId);
                    lstMacAddress.remove(macAddress);
                }
                if (lstMacAddress == null || lstMacAddress.isEmpty()) {
                    vlanProviderCache.remove(segmentationId);
                }
                boolean isSegmentationIdExist = vlanProviderCache.containsKey(segmentationId);
                if (!isSegmentationIdExist) {
                    removeFlow(flowBuilder, nodeBuilder);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing pop vlan instruction flow. dpidLong = {}, patchPort={}, write = {}."
                    + "Caused due to, {}", dpidLong, patchPort, write, e.getMessage());
        }
    }

    /**
     * Write or remove the flows for push VLAN instructions based on flag value actions.
     * Sample flow: cookie=0x0, duration=4831.827s, table=0, n_packets=1202, n_bytes=56476, priority=4,in_port=3, dl_src=fa:16:3e:74:a9:2e actions=push_vlan:0x8100,set_field:4196 vlan_vid,NORMAL
     */
    @Override
    public void programProviderNetworkPushVlan(Long dpidLong, String segmentationId,
                                               Long patchExtPort, String macAddress,
                                               Map<String, Set<String>> vlanProviderCache, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            MatchUtils.createEthSrcMatch(matchBuilder, new MacAddress(macAddress));
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, patchExtPort);
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ExternalBridge_pushVLAN_" + dpidLong + "_" + segmentationId + "_" + macAddress;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId((short) 0).setKey(new FlowKey(flowId))
                    .setPriority(4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                LOG.debug("In programProviderNetworkPushVlan macAddress:" + macAddress
                        + "segmentationId:" + segmentationId);
                Set<String> lstMacAddress = new HashSet<String>();
                if (vlanProviderCache != null && !vlanProviderCache.isEmpty() && vlanProviderCache.containsKey(segmentationId)) {
                    lstMacAddress = vlanProviderCache.get(segmentationId);
                } else {
                    lstMacAddress = new HashSet<String>();
                    vlanProviderCache.put(segmentationId, lstMacAddress);
                }
                lstMacAddress.add(macAddress);
                InstructionBuilder ib = new InstructionBuilder();
                // Set VLAN ID Instruction
                InstructionUtils.createSetVlanAndNormalInstructions(ib, new VlanId(Integer.valueOf(segmentationId)));
                ib.setOrder(0);
                ib.setKey(new InstructionKey(0));
                Instruction pushVLANInstruction = ib.build();
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, pushVLANInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                removeFlow(flowBuilder, nodeBuilder);
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing push vlan instruction flow. dpidLong = {}, patchPort={}, write = {}."
                    + "Caused due to, {}", dpidLong, patchExtPort, write, e.getMessage());
        }
    }

    /**
     * Write or remove the flows for drop instructions based on flag value actions.
     * Sample flow: table=0, n_packets=0, n_bytes=0, priority=2,in_port=3 actions=drop
     */
    @Override
    public void programProviderNetworkDrop(Long dpidLong, Long patchExtPort,
                                           Map<String, Set<String>> vlanProviderCache, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, patchExtPort);
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ExternalBridge_drop_" + dpidLong + "_" + patchExtPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId((short) 0)
                    .setKey(new FlowKey(flowId)).setPriority(2).setFlowName(flowName)
                    .setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                // Call the InstructionBuilder Methods Containing Actions
                Instruction dropInstruction = InstructionUtils.createDropInstructions(new InstructionBuilder())
                        .setOrder(0)
                        .setKey(new InstructionKey(0))
                        .build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, dropInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                if (vlanProviderCache.isEmpty()) {
                    removeFlow(flowBuilder, nodeBuilder);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing drop instruction flow. dpidLong = {}, patchPort={}, write = {}."
                    + "Caused due to, {}", dpidLong, patchExtPort, write, e.getMessage());
        }
    }

    private void populateSegmentaionCache() {
        try {
            Map<String, String> networkUUIDSegIdMap = new HashMap<String, String>();
            for (Node node : nodeCacheManager.getBridgeNodes()) {
                Node srcBridgeNode = southbound.getBridgeNode(node, configurationService.getIntegrationBridgeName());
                if (srcBridgeNode != null) {
                    List<OvsdbTerminationPointAugmentation> terminationPointOfBridgeList =
                            southbound.getTerminationPointsOfBridge(srcBridgeNode);
                    for (OvsdbTerminationPointAugmentation intf : terminationPointOfBridgeList) {
                        NeutronPort neutronPort = tenantNetworkManager.getTenantPort(intf);
                        if (neutronPort != null && neutronPort.getDeviceOwner().equalsIgnoreCase(Constants.OWNER_ROUTER_GATEWAY)) {
                            final String macAddress = neutronPort.getMacAddress();
                            final String networkUUID = neutronPort.getNetworkUUID();
                            String providerSegmentationId = networkUUIDSegIdMap.get(networkUUID);
                            if (providerSegmentationId == null) {
                                NeutronNetwork neutronNetwork = neutronNetworkCache.getNetwork(networkUUID);
                                providerSegmentationId = neutronNetwork != null ?
                                        neutronNetwork.getProviderSegmentationID() : null;
                            }
                            if (providerSegmentationId == null || providerSegmentationId.isEmpty()
                                    || macAddress == null || macAddress.isEmpty()) {
                                continue;
                            }
                            networkUUIDSegIdMap.put(networkUUID, providerSegmentationId);
                            ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(providerSegmentationId);
                            if (macAddrOfPortPairList == null)
                            {
                                macAddrOfPortPairList = new ArrayList<ImmutablePair<String, Long>>();
                                segmentationOfPortMap.put(providerSegmentationId, macAddrOfPortPairList);
                            }
                            ImmutablePair<String, Long> macAddrOfPortPair = new ImmutablePair<String, Long>(macAddress, intf.getOfport());
                            macAddrOfPortPairList.add(macAddrOfPortPair);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.error("Error while populating segmentation mac-ofport cache, due to {} ", e.getMessage());
        }
    }

    @Override
    public void setDependencies(BundleContext bundleContext, ServiceReference serviceReference) {
        super.setDependencies(bundleContext.getServiceReference(VlanResponderProvider.class.getName()), this);
        configurationService =
                (ConfigurationService) ServiceHelper.getGlobalInstance(ConfigurationService.class, this);
        southbound =
                (Southbound) ServiceHelper.getGlobalInstance(Southbound.class, this);
        nodeCacheManager =
                (NodeCacheManager) ServiceHelper.getGlobalInstance(NodeCacheManager.class, this);
        tenantNetworkManager =
                (TenantNetworkManager) ServiceHelper.getGlobalInstance(TenantNetworkManager.class, this);
    }

    @Override
    public void setDependencies(Object impl) {
        if (impl instanceof INeutronNetworkCRUD) {
            neutronNetworkCache = (INeutronNetworkCRUD)impl;
        }
        populateSegmentaionCache();
    }

}
