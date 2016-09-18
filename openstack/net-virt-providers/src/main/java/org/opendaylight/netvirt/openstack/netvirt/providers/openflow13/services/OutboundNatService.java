/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import org.opendaylight.netvirt.openstack.netvirt.api.OutboundNatProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.StatusCode;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.Status;
import org.opendaylight.netvirt.openstack.netvirt.providers.ConfigInterface;
import org.opendaylight.netvirt.utils.mdsal.openflow.ActionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.FlowUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.InstructionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.address.address.Ipv4Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;

import com.google.common.collect.Lists;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundNatService extends AbstractServiceInstance implements OutboundNatProvider, ConfigInterface {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundNatService.class);

    public OutboundNatService() {
        super(Service.OUTBOUND_NAT);
    }

    public OutboundNatService(Service service) {
        super(service);
    }

    @Override
    public Status programIpRewriteRule(Long dpidLong,
                                       String matchSegmentationId,
                                       String matchDestMacAddress,
                                       InetAddress matchSrcAddress,
                                       String rewriteSrcMacAddress,
                                       String rewriteDestMacAddress,
                                       InetAddress rewriteSrcAddress,
                                       Long OutPort,
                                       Action action) {
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        FlowBuilder flowBuilder = new FlowBuilder();
        String flowName = "OutboundNAT_" + matchSegmentationId + "_" + matchSrcAddress.getHostAddress();
        FlowUtils.initFlowBuilder(flowBuilder, flowName, getTable()).setPriority(512);

        MatchBuilder matchBuilder = new MatchBuilder();
        MatchUtils.createDmacIpSaMatch(matchBuilder,
                matchDestMacAddress,
                MatchUtils.iPv4PrefixFromIPv4Address(matchSrcAddress.getHostAddress()),
                matchSegmentationId);
        flowBuilder.setMatch(matchBuilder.build());

        if (action.equals(Action.ADD)) {
            // Instructions List Stores Individual Instructions
            InstructionsBuilder isb = new InstructionsBuilder();
            List<Instruction> instructions = Lists.newArrayList();
            List<Instruction> instructions_tmp = Lists.newArrayList();
            InstructionBuilder ib = new InstructionBuilder();

            ApplyActionsBuilder aab = new ApplyActionsBuilder();
            ActionBuilder ab = new ActionBuilder();
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actionList =
                    Lists.newArrayList();

            // Set source Mac address
            ab.setAction(ActionUtils.setDlSrcAction(new MacAddress(rewriteSrcMacAddress)));
            ab.setOrder(0);
            ab.setKey(new ActionKey(0));
            actionList.add(ab.build());

            // DecTTL
            ab.setAction(ActionUtils.decNwTtlAction());
            ab.setOrder(1);
            ab.setKey(new ActionKey(1));
            actionList.add(ab.build());

            // Set Destination Mac address
            ab.setAction(ActionUtils.setDlDstAction(new MacAddress(rewriteDestMacAddress)));
            ab.setOrder(2);
            ab.setKey(new ActionKey(2));
            actionList.add(ab.build());

            // Set source Ip address
            Ipv4Builder ipb = new Ipv4Builder().setIpv4Address(
                    MatchUtils.iPv4PrefixFromIPv4Address(rewriteSrcAddress.getHostAddress()));
            ab.setAction(ActionUtils.setNwSrcAction(ipb.build()));
            ab.setOrder(3);
            ab.setKey(new ActionKey(3));
            actionList.add(ab.build());

            // Create Apply Actions Instruction
            aab.setAction(actionList);
            ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));
            instructions_tmp.add(ib.build());

            // Set the Output Port/Iface
            ib = new InstructionBuilder();
            InstructionUtils.addOutputPortInstructions(ib, dpidLong, OutPort, instructions_tmp);
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));
            instructions.add(ib.build());

            flowBuilder.setInstructions(isb.setInstruction(instructions).build());

            writeFlow(flowBuilder, nodeBuilder);
        } else {
            removeFlow(flowBuilder, nodeBuilder);
        }

        // ToDo: WriteFlow/RemoveFlow should return something we can use to check success
        return new Status(StatusCode.SUCCESS);
    }

    @Override
    public Status programIpRewriteExclusion(Long dpid, String segmentationId, String excludedCidr,
                                            Action action) {
        String ipAddress = excludedCidr.substring(0, excludedCidr.indexOf("/"));
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            return new Status(StatusCode.BADREQUEST);
        }
        if (inetAddress instanceof Inet6Address) {
            // WORKAROUND: For now ipv6 is not supported
            // TODO: implement ipv6 cidr case
            LOG.debug("ipv6 cidr is not implemented yet. cidr {}",
                      excludedCidr);
            return new Status(StatusCode.NOTIMPLEMENTED);
        }

        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpid);
        FlowBuilder flowBuilder = new FlowBuilder();
        String flowName = "OutboundNATExclusion_" + segmentationId + "_" + excludedCidr;
        FlowUtils.initFlowBuilder(flowBuilder, flowName, getTable()).setPriority(1024);

        MatchBuilder matchBuilder = new MatchBuilder();
        MatchUtils.createTunnelIDMatch(matchBuilder, new BigInteger(segmentationId));

        MatchUtils.createDstL3IPv4Match(matchBuilder, new Ipv4Prefix(excludedCidr));
        flowBuilder.setMatch(matchBuilder.build());

        if (action.equals(Action.ADD)) {
            // Instructions List Stores Individual Instructions
            InstructionsBuilder isb = new InstructionsBuilder();
            List<Instruction> instructions = Lists.newArrayList();
            InstructionBuilder ib;

            // Goto Next Table
            ib = getMutablePipelineInstructionBuilder();
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));
            instructions.add(ib.build());

            flowBuilder.setInstructions(isb.setInstruction(instructions).build());

            writeFlow(flowBuilder, nodeBuilder);
        } else {
            removeFlow(flowBuilder, nodeBuilder);
        }

        // ToDo: WriteFlow/RemoveFlow should return something we can use to check success
        return new Status(StatusCode.SUCCESS);
    }

    /**
     * Writing following flow in the internal bridge
     * table=100, n_packets=1192, n_bytes=55496, priority=4,in_port=5,dl_src=fa:16:3e:74:a9:2e actions=output:3
     */
    @Override
    public void provideNetworkOutput(Long dpidLong, Long ofPort, Long patchPort, String macAddress, boolean write) {
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
        String flowName = "OutboundNATVLAN_" + dpidLong + "_" + ofPort;
        final FlowId flowId = new FlowId(flowName);
        flowBuilder
                .setId(flowId)
                .setBarrier(true)
                .setTableId(getTable())
                .setKey(new FlowKey(flowId))
                .setPriority(4)
                .setFlowName(flowName)
                .setHardTimeout(0)
                .setIdleTimeout(0);
        if (write) {
            // Set the Output Port/Iface
            Instruction outputPortInstruction =
                    InstructionUtils.createOutputPortInstructions(new InstructionBuilder(), dpidLong, patchPort)
                            .setOrder(0)
                            .setKey(new InstructionKey(0))
                            .build();
            // Add InstructionsBuilder to FlowBuilder
            InstructionUtils.setFlowBuilderInstruction(flowBuilder, outputPortInstruction);
            writeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully written into internal bridge.");
        } else {
            removeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully removed from internal bridge.");
        }
    }

    /**
     * Writing following flow in the internal bridge
     * table=100, n_packets=218, n_bytes=15778, priority=4,in_port=3,dl_vlan=100 actions=pop_vlan,output:5
     */
    @Override
    public void provideNetworkPopVlan(Long dpidLong, String segmentationId, Long ofPort, Long patchPort, boolean write) {
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
        String flowName = "OutboundNATVLAN_" + segmentationId + "_" + patchPort;
        final FlowId flowId = new FlowId(flowName);
        flowBuilder
                .setId(flowId)
                .setBarrier(true)
                .setTableId(getTable())
                .setKey(new FlowKey(flowId))
                .setPriority(4)
                .setFlowName(flowName)
                .setHardTimeout(0)
                .setIdleTimeout(0);
        if (write) {
            /* Strip vlan and store to tmp instruction space*/
            Instruction stripVlanInstruction = InstructionUtils.createPopVlanInstructions(new InstructionBuilder())
                    .setOrder(0)
                    .setKey(new InstructionKey(0))
                    .build();
            // Set the Output Port/Iface
            Instruction setOutputPortInstruction =
                    InstructionUtils.addOutputPortInstructions(new InstructionBuilder(), dpidLong, ofPort,
                            Collections.singletonList(stripVlanInstruction))
                            .setOrder(1)
                            .setKey(new InstructionKey(0))
                            .build();

            // Add InstructionsBuilder to FlowBuilder
            InstructionUtils.setFlowBuilderInstruction(flowBuilder, setOutputPortInstruction);
            writeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully written into internal bridge");
        } else {
            removeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully removed from internal bridge");
        }
    }

    /**
     * Writing following flow in the external bridge
     * cookie=0x0, duration=4831.827s, table=0, n_packets=1202, n_bytes=56476, priority=4,in_port=3,dl_src=fa:16:3e:74:a9:2e  actions=push_vlan:0x8100,set_field:4196 vlan_vid,NORMAL
     * 
     */
    @Override
    public void provideNetworkPushVlan(Long dpidLong, String segmentationId, Long patchExtPort, String macAddress, boolean write) {
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
        String flowName = "OutboundNATVLAN_" + dpidLong + "_" + segmentationId;
        final FlowId flowId = new FlowId(flowName);
        flowBuilder
                .setId(flowId)
                .setBarrier(true)
                .setTableId((short) 0)
                .setKey(new FlowKey(flowId))
                .setPriority(4)
                .setFlowName(flowName)
                .setHardTimeout(0)
                .setIdleTimeout(0);
        if (write) {
            InstructionBuilder ib = new InstructionBuilder();
            // Set VLAN ID Instruction
            InstructionUtils.createSetVlanAndNormalInstructions(ib, new VlanId(Integer.valueOf(segmentationId)));
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));
            Instruction pushVLANInstruction = ib.build();
            InstructionUtils.setFlowBuilderInstruction(flowBuilder, pushVLANInstruction);
            writeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully written into external bridge.");
        } else {
            removeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully removed from external bridge");
        }
    }

    /**
     * Writing following flow in the external bridge
     * table=0, n_packets=0, n_bytes=0, priority=2,in_port=3 actions=drop
     */
    @Override
    public void provideNetworkDrop(Long dpidLong, Long patchExtPort, boolean write) {
        String nodeName = OPENFLOW + dpidLong;
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(nodeName);
        FlowBuilder flowBuilder = new FlowBuilder();
        // Create the OF Match using MatchBuilder
        MatchBuilder matchBuilder = new MatchBuilder();
      //Match In Port
        MatchUtils.createInPortMatch(matchBuilder, dpidLong, patchExtPort);
        flowBuilder.setMatch(matchBuilder.build());
        // Add Flow Attributes
        String flowName = "OutboundNATVLAN_" + dpidLong + "_" + patchExtPort;
        final FlowId flowId = new FlowId(flowName);
        flowBuilder
                .setId(flowId)
                .setBarrier(true)
                .setTableId((short) 0)
                .setKey(new FlowKey(flowId))
                .setPriority(2)
                .setFlowName(flowName)
                .setHardTimeout(0)
                .setIdleTimeout(0);
        if (write) {
            // Call the InstructionBuilder Methods Containing Actions
            Instruction dropInstruction = InstructionUtils.createDropInstructions(new InstructionBuilder())
                    .setOrder(0)
                    .setKey(new InstructionKey(0))
                    .build();
            // Add InstructionsBuilder to FlowBuilder
            InstructionUtils.setFlowBuilderInstruction(flowBuilder, dropInstruction);
            writeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully written into external bridge");
        } else {
            removeFlow(flowBuilder, nodeBuilder);
            LOG.trace("Flow successfully removed from internal bridge");
        }
    }

    @Override
    public void setDependencies(BundleContext bundleContext, ServiceReference serviceReference) {
        super.setDependencies(bundleContext.getServiceReference(OutboundNatProvider.class.getName()), this);
    }

    @Override
    public void setDependencies(Object impl) {

    }
}
