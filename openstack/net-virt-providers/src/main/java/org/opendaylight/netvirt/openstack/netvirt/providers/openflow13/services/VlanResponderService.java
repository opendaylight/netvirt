/*
 * Copyright (c) 2016 NEC Corporation and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int PRIORITY_2 = 2;
    private static final int PRIORITY_4 = 4;
    private static final int PRIORITY_5 = 5;

    private volatile ConfigurationService configurationService;
    private volatile NodeCacheManager nodeCacheManager;
    private volatile TenantNetworkManager tenantNetworkManager;
    private volatile Southbound southbound;
    private volatile INeutronNetworkCRUD neutronNetworkCache;
    private volatile Map<String, ArrayList<ImmutablePair<String, Long>>> segmentationOfPortMap =
            new ConcurrentHashMap<String, ArrayList<ImmutablePair<String, Long>>>();

    public VlanResponderService() {
        super(Service.OUTBOUND_NAT);
    }

    public VlanResponderService(Service service) {
        super(service);
    }

    /**
     * Creates provider network flows for internal bridge.
     *
     * @param dpIdInt dp Id
     * @param segmentationId segmentation id
     * @param patchIntPort patch port of internal bridge
     * @param ofPort of port value
     * @param macAddress mac address
     * @param vlanProviderCache Initial VLAN cache with processing cache
     * @param write - flag to indicate the operation
     */
    @Override
    public void programProviderNetworkRulesInternal(Long dpIdInt, String segmentationId, Long ofPort, Long patchIntPort,
            String macAddress, Map<String, Set<String>> vlanProviderCache, boolean write) {

        programProviderBroadAndMultiCastOfRouter(dpIdInt, segmentationId, ofPort, patchIntPort, macAddress, write);
        programProviderUnicastFlowOfRouters(dpIdInt, segmentationId, ofPort, patchIntPort, macAddress, write);
        programProviderUnicastFlowOfExternal(dpIdInt, segmentationId, ofPort, patchIntPort, macAddress, write);
        programProviderBroadAndMultiCastOfExternal(dpIdInt, segmentationId, ofPort, patchIntPort, macAddress, write);
        programProviderUnicastFlowFromExternal(dpIdInt, segmentationId, ofPort, patchIntPort, macAddress, vlanProviderCache, write);
    }

    /**
     * Creates provider network flows for external bridge.
     *
     * @param dpIdExt dp id
     * @param segmentationId segmentation id
     * @param patchExtPort patch port of external bridge
     * @param macAddress mac address
     * @param vlanProviderCache Initial VLAN cache with processing cache
     * @param write - flag indicate the operation
     */
    @Override
    public void programProviderNetworkRulesExternal(Long dpIdExt,  String segmentationId, Long patchExtPort,
            String macAddress, Map<String, Set<String>> vlanProviderCache, boolean write) {

        programProviderNetworkForExternal(dpIdExt, segmentationId, patchExtPort, macAddress, vlanProviderCache, write);
        programProviderNetworkDrop(dpIdExt, patchExtPort, vlanProviderCache, write);
    }


    /**
     * Write or remove the flows for forward the BC/MC packets of router to patch port and
     * the router ports connected to same external network.
     * Sample flow: table=100, priority=5,in_port=2,dl_src=fa:16:3e:ff:9c:57,dl_dst=01:00:00:00:00:00/01:00:00:00:00:00
     * actions=output:3,push_vlan:0x8100,set_field:4196->vlan_vid,output:1
     */
    private void programProviderBroadAndMultiCastOfRouter(Long dpidLong, String segmentationId, Long ofPort, Long patchPort,
                                             String macAddress, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, ofPort);
            matchBuilder = MatchUtils.createEthSrcDestMatch(matchBuilder, new MacAddress(macAddress),
                    new MacAddress("01:00:00:00:00:00"), new MacAddress("01:00:00:00:00:00"));
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ProviderNetwork_BC_Router_" + dpidLong + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(PRIORITY_5).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
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
                int instructionIndex = 0;
                for (ImmutablePair<String, Long> macAddressOfPortPair : macAddrOfPortPairList) {
                    InstructionUtils.createOutputPortInstruction(instructionIndex, dpidLong, macAddressOfPortPair.getRight(),
                            outputInstructions);
                    instructionIndex++;
                }
                // Set the Output Port/Iface
                Instruction outputPortInstruction = InstructionUtils.createVlanOutputPortInstructions(new InstructionBuilder(),
                        dpidLong, patchPort, outputInstructions, new VlanId(Integer.valueOf(segmentationId)))
                        .setOrder(0).setKey(new InstructionKey(0)).build();
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
            programExistingProviderBroadAndMultiCastOfRouter(dpidLong, segmentationId, ofPort, patchPort, macAddress, write);
        } catch (Exception e) {
            LOG.error("Error while writing/removing broadcast flows. dpidLong = {}, patchPort={}, write = {}."
                    + " Caused due to, {}", dpidLong, patchPort, write, e.getMessage());
        }
    }

    /**
     * Write or remove the existing flows of forward the BC/MC packets of router to patch port and
     * the router ports connected to same external network.
     * Sample flow: table=100, priority=5,in_port=2,dl_src=fa:16:3e:ff:9c:57,dl_dst=01:00:00:00:00:00/01:00:00:00:00:00
     * actions=output:3,push_vlan:0x8100,set_field:4196->vlan_vid,output:1
     */
    private void programExistingProviderBroadAndMultiCastOfRouter(Long dpidLong, String segmentationId, Long currentOfPort, Long patchPort,
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
            matchBuilder = MatchUtils.createEthSrcDestMatch(matchBuilder, new MacAddress(macAddrOfPortPair.getLeft()),
                    new MacAddress("01:00:00:00:00:00"), new MacAddress("01:00:00:00:00:00"));
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ProviderNetwork_BC_Router_" + dpidLong + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
            .setPriority(PRIORITY_5).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);

            if (write) {
                List<Instruction> outputInstructions = new ArrayList<Instruction>();
                int instructionIndex = 0;
                for (ImmutablePair<String, Long> macAddressOfPortPair : macAddrOfPortPairList) {
                    if (ofPort.equals(macAddressOfPortPair.getRight())) {
                        continue;
                    }
                    InstructionUtils.createOutputPortInstruction(instructionIndex, dpidLong, macAddressOfPortPair.getRight(),
                            outputInstructions);
                    instructionIndex++;
                }

                Instruction outputPortInstruction = InstructionUtils.createVlanOutputPortInstructions(new InstructionBuilder(),
                        dpidLong, patchPort, outputInstructions, new VlanId(Integer.valueOf(segmentationId)))
                        .setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
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
                    InstructionUtils.createOutputPortInstruction(instructionIndex, dpidLong, cachedOfPort, outputInstructions);
                    instructionIndex++;
                }
                Instruction outputPortInstruction = InstructionUtils.createVlanOutputPortInstructions(new InstructionBuilder(),
                        dpidLong, patchPort, outputInstructions, new VlanId(Integer.valueOf(segmentationId)))
                        .setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            }
        }
    }

    /**
     * Write or remove the flows for forward the packets from one router to another routers (Unicast flow)
     * Sample flow: table=100, priority=4,in_port=2,dl_dst=fa:16:3e:4b:cc:0a actions=output:8
     */
    private void programProviderUnicastFlowOfRouters(Long dpidLong, String segmentationId, Long ofPort, Long patchPort,
            String macAddress, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(segmentationId);
            if (write) {
                for (ImmutablePair<String, Long> macAddressOfPortPair : macAddrOfPortPairList) {
                    for (ImmutablePair<String, Long> subMacAddressOfPortPair : macAddrOfPortPairList) {
                        if (macAddressOfPortPair.getLeft().equals(subMacAddressOfPortPair.getLeft())
                                || ((!macAddressOfPortPair.getLeft().equals(macAddress) &&
                                        !subMacAddressOfPortPair.getLeft().equals(macAddress)))) {
                            continue;
                        }
                        // Create the OF Match using MatchBuilder
                        MatchBuilder currentMatchBuilder = new MatchBuilder();
                        //Match In Port
                        MatchUtils.createInPortMatch(currentMatchBuilder, dpidLong, macAddressOfPortPair.getRight());
                        currentMatchBuilder = MatchUtils.createDestEthMatch(currentMatchBuilder,
                                new MacAddress(subMacAddressOfPortPair.getLeft()), null);
                        flowBuilder.setMatch(currentMatchBuilder.build());
                        // Add Flow Attributes
                        String currentFlowName = "ProviderNetwork_unicast_router_" + dpidLong + "_" +
                            macAddressOfPortPair.getLeft() + "_" + subMacAddressOfPortPair.getLeft();

                        final FlowId flowId = new FlowId(currentFlowName);
                        flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                                .setPriority(PRIORITY_4).setFlowName(currentFlowName).setHardTimeout(0).setIdleTimeout(0);

                        Instruction outputPortInstruction = InstructionUtils.createOutputPortInstructions(new InstructionBuilder(),
                                dpidLong, subMacAddressOfPortPair.getRight()).setOrder(0).setKey(new InstructionKey(0)).build();
                        // Add InstructionsBuilder to FlowBuilder
                        InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                        writeFlow(flowBuilder, nodeBuilder);
                    }
                }
            } else {
                for (ImmutablePair<String, Long> macAddressOfPortPair : macAddrOfPortPairList) {
                    if (macAddressOfPortPair.getLeft().equals(macAddress)) {
                        continue;
                    }
                     // Create the OF Match using MatchBuilder
                     MatchBuilder matchBuilder = new MatchBuilder();
                     //Match In Port
                     MatchUtils.createInPortMatch(matchBuilder, dpidLong, ofPort);
                     matchBuilder = MatchUtils.createDestEthMatch(matchBuilder, new MacAddress(macAddressOfPortPair.getLeft()), null);
                     flowBuilder.setMatch(matchBuilder.build());
                     // Add Flow Attributes
                     String flowName = "ProviderNetwork_unicast_router_" + dpidLong + "_" + macAddress + "_" + macAddressOfPortPair.getLeft();
                     FlowId flowId = new FlowId(flowName);
                     flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                              .setPriority(PRIORITY_4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);

                    removeFlow(flowBuilder, nodeBuilder);

                    MatchUtils.createInPortMatch(matchBuilder, dpidLong, macAddressOfPortPair.getRight());
                    matchBuilder = MatchUtils.createDestEthMatch(matchBuilder, new MacAddress(macAddress), null);
                    flowBuilder.setMatch(matchBuilder.build());
                    // Add Flow Attributes
                    flowName = "ProviderNetwork_unicast_router_" + dpidLong + "_" + macAddressOfPortPair.getLeft() + "_" + macAddress;
                    flowId = new FlowId(flowName);
                    flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                             .setPriority(PRIORITY_4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);

                    removeFlow(flowBuilder, nodeBuilder);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing unicast flow of routers. dpidLong = {}, patchPort={}, write = {}."
                    + " Caused due to, {}", dpidLong, patchPort, write, e.getMessage());
        }
    }

    /**
     * Write or remove the flows for forwarding the BC/MC packets to all other router ports connected to same external network.
     * Sample flow: table=100, priority=4,in_port=1,dl_vlan=100,dl_dst=01:00:00:00:00:00/01:00:00:00:00:00 actions=pop_vlan,output:2,output:8
     */
    private void programProviderBroadAndMultiCastOfExternal(Long dpidLong, String segmentationId, Long ofPort, Long patchPort,
                                             String macAddress, boolean write) {
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
            matchBuilder = MatchUtils.createDestEthMatch(matchBuilder, new MacAddress("01:00:00:00:00:00"),
                    new MacAddress("01:00:00:00:00:00"));
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ProviderNetwork_BC_External_" + segmentationId + "_" + patchPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(PRIORITY_4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(segmentationId);
                List<Instruction> outputInstructions = new ArrayList<Instruction>();
                int instructionIndex = 1;
                for (ImmutablePair<String, Long> ofPortMacAddrPair : macAddrOfPortPairList) {
                    InstructionUtils.createOutputPortInstruction(instructionIndex, dpidLong,
                            ofPortMacAddrPair.getRight(), outputInstructions);
                    instructionIndex++;
                }
                Instruction outputPortInstruction =
                        InstructionUtils.createPopOutputPortInstructions(new InstructionBuilder(), dpidLong, null, outputInstructions)
                        .setOrder(0)
                        .setKey(new InstructionKey(0))
                        .build();
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(segmentationId);
                if (macAddrOfPortPairList == null || macAddrOfPortPairList.isEmpty()) {
                    removeFlow(flowBuilder, nodeBuilder);
                } else {
                    List<Instruction> outputInstructions = new ArrayList<Instruction>();
                    int instructionIndex = 1;
                    for (ImmutablePair<String, Long> ofPortMacAddrPair : macAddrOfPortPairList) {
                        InstructionUtils.createOutputPortInstruction(instructionIndex, dpidLong, ofPortMacAddrPair.getRight(),
                                outputInstructions);
                        instructionIndex++;
                    }
                    Instruction outputPortInstruction = InstructionUtils.createPopOutputPortInstructions(new InstructionBuilder(),
                            dpidLong, null, outputInstructions).setOrder(0).setKey(new InstructionKey(0)).build();
                    // Add InstructionsBuilder to FlowBuilder
                    InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                    writeFlow(flowBuilder, nodeBuilder);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing BC/MC flow of external interface. dpidLong = {}, patchPort={}, write = {}."
                    +" Caused due to, {}", dpidLong, patchPort, write, e.getMessage());
        }
    }


    /**
     * Write or remove the flows for forwarding unicast packets from router to external Gateway.
     * Sample flow: table=100, priority=2,in_port=2,dl_src=fa:16:3e:ff:9c:57 actions=push_vlan:0x8100,set_field:4196- >vlan_vid,output:1
     */
    private void programProviderUnicastFlowOfExternal(Long dpidLong, String segmentationId, Long ofPort, Long patchPort,
            String macAddress, boolean write) {
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
            String flowName = "ProviderNetwork_unicast_external_" + dpidLong + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(PRIORITY_2).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                // Set the Output Port/Iface
                Instruction outputPortInstruction = InstructionUtils.createPushVlanInstruction(new InstructionBuilder(),
                        dpidLong, patchPort, new VlanId(Integer.valueOf(segmentationId)), new ArrayList<>())
                        .setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                removeFlow(flowBuilder, nodeBuilder);
            }
        } catch (Exception e) {
            LOG.error("Error while writing/removing unicast flow of external gateway. dpidLong = {}, write = {}."
                    +" Caused due to, {}", dpidLong, write, e.getMessage());
        }
    }

    /**
     * Write or remove the flows for forwarding unicast packets from external Gateway to router.
     * Sample flow: table=100, priority=4,in_port=1,dl_vlan=100,dl_dst=fa:16:3e:ff:9c:57 actions=pop_vlan,output:2
     */
    private void programProviderUnicastFlowFromExternal(Long dpidLong, String segmentationId,
                                              Long ofPort, Long patchPort, String macAddress,
                                              Map<String, Set<String>> vlanProviderCache, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            ArrayList<ImmutablePair<String, Long>> macAddrOfPortPairList = segmentationOfPortMap.get(segmentationId);
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            // Match Vlan ID
            MatchUtils.createVlanIdMatch(matchBuilder, new VlanId(Integer.valueOf(segmentationId)), true);
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, patchPort);
            matchBuilder = MatchUtils.createDestEthMatch(matchBuilder, new MacAddress(macAddress), null);
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ProviderNetwork_unicast_ext_int" + segmentationId + "_" + ofPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId(getTable()).setKey(new FlowKey(flowId))
                    .setPriority(PRIORITY_4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                // Set the Output Port/Iface
                Instruction outputPortInstruction = InstructionUtils.createPopOutputPortInstructions(new InstructionBuilder(),
                        dpidLong, ofPort, null).setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                 removeFlow(flowBuilder, nodeBuilder);
               }
        } catch (Exception e) {
            LOG.error("Error while writing/removing unicast flow of external gateway to router. dpidLong = {}, patchPort={}, write = {}."
                    + "Caused due to, {}", dpidLong, patchPort, write, e.getMessage());
        }
    }

    /**
     * Write or remove the flows for external bridge.
     * Sample flow: cookie=0x0, duration=4831.827s, table=0, n_packets=1202, n_bytes=56476, priority=4,in_port=3,
     * dl_src=fa:16:3e:74:a9:2e actions=push_vlan:0x8100,set_field:4196 vlan_vid,NORMAL
     */
    private void programProviderNetworkForExternal(Long dpidLong, String segmentationId,
                                               Long patchExtPort, String macAddress,
                                               Map<String, Set<String>> vlanProviderCache, boolean write) {
        try {
            String nodeName = OPENFLOW + dpidLong;
            NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
            FlowBuilder flowBuilder = new FlowBuilder();
            // Create the OF Match using MatchBuilder
            MatchBuilder matchBuilder = new MatchBuilder();
            //Match Vlan ID
            MatchUtils.createVlanIdMatch(matchBuilder, new VlanId(Integer.valueOf(segmentationId)), true);
            //Match In Port
            MatchUtils.createInPortMatch(matchBuilder, dpidLong, patchExtPort);
            flowBuilder.setMatch(matchBuilder.build());
            // Add Flow Attributes
            String flowName = "ProviderNetwork_pushVLAN_" + dpidLong + "_" + segmentationId;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId((short) 0).setKey(new FlowKey(flowId))
                    .setPriority(PRIORITY_4).setFlowName(flowName).setHardTimeout(0).setIdleTimeout(0);
            if (write) {
                LOG.debug("In programProviderNetworkPushVlan macAddress:" + macAddress
                        + "segmentationId:" + segmentationId);
                Set<String> lstMacAddress;
                if (vlanProviderCache != null && !vlanProviderCache.isEmpty() && vlanProviderCache.containsKey(segmentationId)) {
                    lstMacAddress = vlanProviderCache.get(segmentationId);
                } else {
                    lstMacAddress = new HashSet<>();
                    vlanProviderCache.put(segmentationId, lstMacAddress);
                }
                lstMacAddress.add(macAddress);
                Instruction normalInstruction = InstructionUtils.createNormalInstructions(
                        FlowUtils.getNodeName(dpidLong), new InstructionBuilder()).
                        setOrder(0).setKey(new InstructionKey(0)).build();
                // Add InstructionsBuilder to FlowBuilder
                InstructionUtils.setFlowBuilderInstruction(flowBuilder, normalInstruction);
                writeFlow(flowBuilder, nodeBuilder);
            } else {
                Set<String> lstMacAddress = new HashSet<String>();
                if (vlanProviderCache != null && !vlanProviderCache.isEmpty() && vlanProviderCache.containsKey(segmentationId)) {
                    lstMacAddress = vlanProviderCache.get(segmentationId);
                    lstMacAddress.remove(macAddress);
                }
                if (lstMacAddress == null || lstMacAddress.isEmpty()) {
                    vlanProviderCache.remove(segmentationId);
                }
                boolean isSegmentationIdExist = vlanProviderCache.containsKey(segmentationId);
                if (!isSegmentationIdExist)  {
                    removeFlow(flowBuilder, nodeBuilder);
                }
                //removeFlow(flowBuilder, nodeBuilder);
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
    private void programProviderNetworkDrop(Long dpidLong, Long patchExtPort,
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
            String flowName = "ProviderNetwork_drop_" + dpidLong + "_" + patchExtPort;
            final FlowId flowId = new FlowId(flowName);
            flowBuilder.setId(flowId).setBarrier(true).setTableId((short) 0)
                    .setKey(new FlowKey(flowId)).setPriority(PRIORITY_2).setFlowName(flowName)
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

    private void populateSegmentationCache() {
        try {
            Map<String, String> networkUUIDSegIdMap = new ConcurrentHashMap<String, String>();
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
        populateSegmentationCache();
    }

}
