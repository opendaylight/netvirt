/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.simulator.openflow;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.utils.FormatActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.ControllerActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.GroupActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PopMplsActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushMplsActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.controller.action._case.ControllerAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.group.action._case.GroupAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.pop.mpls.action._case.PopMplsAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.push.mpls.action._case.PushMplsAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Instructions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.TcpMatchFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.UdpMatchFields;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.ArpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.TcpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.GeneralAugMatchNodesNodeTableFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.general.rev140714.general.extension.list.grouping.ExtensionList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionLearnNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionResubmitNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.learn.grouping.NxLearn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmit;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.match.rev140714.NxAugMatchNodesNodeTableFlow;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InventoryConfig {
    private static final String FLOW_OUTPUT_FORMAT = "%-5s %-80s";
    private static final String GROUP_OUTPUT_FORMAT = "%-85s";
    private static final String FLOW_COUNT_FORMAT = "%-30s %-5s";
    private static final Logger LOG = LoggerFactory.getLogger(InventoryConfig.class);
    private static final String UNSET = "N/A";
    private final DataBroker dataBroker;

    @Inject
    public InventoryConfig(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public void dumpSwitch(BigInteger dpnId) {
        showFlowHeader();
        showFlows(dpnId);
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public static void showFlowHeader() {
        System.out.println("--------------------------------------------------------------------------------");
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void showFlows(BigInteger dpnId) {
        StringBuilder sb = new StringBuilder();
        int noFlows = 0;
        int noGroups = 0;
        NodeId nodeId = new NodeId("openflow:" + dpnId);
        Node nodeDpn = new NodeBuilder().setId(nodeId).setKey(new NodeKey(nodeId)).build();
        InstanceIdentifier<FlowCapableNode> flowNodeId = InstanceIdentifier.builder(Nodes.class)
            .child(Node.class, nodeDpn.getKey()).augmentation(FlowCapableNode.class).build();
        Optional<FlowCapableNode> flowOptional = read(dataBroker, LogicalDatastoreType.CONFIGURATION,
            flowNodeId);
        if (!flowOptional.isPresent()) {
            LOG.error("Node {} is absent", dpnId);
        }
        FlowCapableNode flowCapableNode = flowOptional.get();
        List<Table> tables = flowCapableNode.getTable();
        Collections.sort(tables, new Comparator<Table>() {
            @Override
            public int compare(Table t1, Table t2) {
                return t1.getId().compareTo(t2.getId());
            }
        });
        for (Table table : tables) {
            List<Flow> flows = table.getFlow();
            for (Flow flow : flows) {
                StringBuilder fsb = new StringBuilder();
                fsb.append("table=").append(flow.getTableId()).append(",priority=").append(flow.getPriority());
                fsb.append(getMatchString(flow.getMatch()));
                fsb.append(" actions=" + getFlowInstructions(flow.getInstructions()));
                sb.setLength(0);
                System.out.println(fsb.toString());

                LOG.trace("Match {} Instructions {}", flow.getMatch(), flow.getInstructions().getInstruction());
                sb.setLength(0);
                noFlows++;
                LOG.trace("STFW: instructions for flow {} are {} and tableId:{}", flow.getId().getValue(),
                    flow.getInstructions(), flow.getTableId());
            }
        }
        Formatter fmt = new Formatter(sb);
        System.out.println(fmt
            .format("--------------------------------------------------------------------------------"));
        sb.setLength(0);
        System.out.println(fmt.format("Total no of flows: %d", noFlows));
        sb.setLength(0);
        System.out.println(fmt
            .format("--------------------------------------------------------------------------------"));
        sb.setLength(0);
        List<Group> groups = flowCapableNode.getGroup();
        if (groups == null) {
            return;
        }
        System.out.println(fmt.format("GROUPS"));
        sb.setLength(0);
        System.out.println(fmt
            .format("--------------------------------------------------------------------------------"));
        sb.setLength(0);
        for (Group group : groups) {
            System.out.println(fmt.format(GROUP_OUTPUT_FORMAT, group.getGroupId()));
            sb.setLength(0);
            noGroups++;
        }
        System.out.println(fmt
            .format("--------------------------------------------------------------------------------"));
        sb.setLength(0);
        System.out.println(fmt
            .format("Total no of groups: %d", noGroups));
        sb.setLength(0);
        System.out.println(fmt
            .format("--------------------------------------------------------------------------------"));
        sb.setLength(0);
        fmt.close();
    }

    private static String getMatchString(Match match) {
        StringBuilder sb = new StringBuilder();
        if (match.getInPort() != null) {
            //TODO: optimize use of split
            String[] split = match.getInPort().getValue().split(":");
            sb.append(",in_port=").append(split[2]);
        }
        if (match.getVlanMatch() != null) {
            sb.append(",vlan_id=").append(match.getVlanMatch().getVlanId().getVlanId().getValue());
        }
        if (match.getTunnel() != null) {
            sb.append(",tun_id=0x").append(match.getTunnel().getTunnelId().toString(16));
        }
        if (match.getProtocolMatchFields() != null) {
            if (match.getProtocolMatchFields().getMplsLabel() != null) {
                sb.append(",mpls_label=0x").append(Long.toHexString(match.getProtocolMatchFields().getMplsLabel()));
            }
        }
        if (match.getMetadata() != null) {
            sb.append(",metadata=0x").append(match.getMetadata().getMetadata().toString(16)).append("/0x")
                .append(match.getMetadata().getMetadataMask().toString(16));
        }
        if (match.getEthernetMatch() != null) {
            EthernetMatch ethMatch = match.getEthernetMatch();
            if (ethMatch.getEthernetType() != null) {
                String dlType = null;
                switch (ethMatch.getEthernetType().getType().getValue().intValue()) {
                    case (2048):
                        sb.append(",ip");
                        break;
                    case (2054):
                        sb.append(",arp");
                        break;
                    case (32821):
                        sb.append(",rarp");
                        break;
                    default:
                        sb.append(",dl_type=").append(ethMatch.getEthernetType().getType().getValue());
                        break;
                }
            }
            if (ethMatch.getEthernetSource() != null) {
                sb.append(",dl_src=").append(ethMatch.getEthernetSource().getAddress().getValue());
            }
            if (ethMatch.getEthernetDestination() != null) {
                sb.append(",dl_dst=").append(ethMatch.getEthernetDestination().getAddress().getValue());
            }
        }
        if (match.getLayer3Match() != null) {
            if (match.getLayer3Match() instanceof Ipv4Match) {
                Ipv4Match ipv4 = (Ipv4Match) match.getLayer3Match();
                if (ipv4.getIpv4Source() != null) {
                    sb.append(",nw_src=").append(ipv4.getIpv4Source().getValue());
                }
                if (ipv4.getIpv4Destination() != null) {
                    sb.append(",nw_dst=").append(ipv4.getIpv4Destination().getValue());
                }
            }
        }
        if (match.getLayer3Match() != null) {
            if (match.getLayer3Match() instanceof ArpMatch) {
                ArpMatch arp = (ArpMatch) match.getLayer3Match();
                if (arp.getArpOp() != null) {
                    sb.append(",arp_op=").append(arp.getArpOp());
                }
                if (arp.getArpSourceHardwareAddress() != null) {
                    sb.append(",arp_sha").append(arp.getArpSourceHardwareAddress().getAddress().getValue());
                }
                if (arp.getArpTargetHardwareAddress() != null) {
                    sb.append(",arp_tha").append(arp.getArpTargetHardwareAddress().getAddress().getValue());
                }
                if (arp.getArpSourceTransportAddress() != null) {
                    sb.append(",arp_spa").append(arp.getArpSourceTransportAddress().getValue());
                }
                if (arp.getArpTargetHardwareAddress() != null) {
                    sb.append(",arp_tpa").append(arp.getArpTargetTransportAddress().getValue());
                }
            }
        }
        if (match.getIpMatch() != null) {
            IpMatch ipMatch = match.getIpMatch();
            if (ipMatch.getIpProtocol() != null) {
                switch (ipMatch.getIpProtocol()) {
                    case (1):
                        sb.append(",icmp");
                        break;
                    case (6):
                        sb.append(",tcp");
                        break;
                    case (17):
                        sb.append(",udp");
                        break;
                    default:
                        sb.append(",ip_proto=").append(ipMatch.getIpProtocol());
                        break;
                }
            }
        }
        if (match.getLayer4Match() != null) {
            if (match.getLayer4Match() instanceof TcpMatch) {
                TcpMatchFields tcpFields = (TcpMatchFields) match.getLayer4Match();
                if (tcpFields.getTcpSourcePort() != null) {
                    sb.append(",tp_src=").append(tcpFields.getTcpSourcePort().getValue());
                }
                if (tcpFields.getTcpDestinationPort() != null) {
                    sb.append(",tp_dst=").append(tcpFields.getTcpDestinationPort().getValue());
                }
            } else if (match.getLayer4Match() instanceof UdpMatch) {
                UdpMatchFields udpFields = (UdpMatchFields) match.getLayer4Match();
                if (udpFields.getUdpSourcePort() != null) {
                    sb.append(",tp_src=").append(udpFields.getUdpSourcePort().getValue());
                }
                if (udpFields.getUdpDestinationPort() != null) {
                    sb.append(",tp_dst=").append(udpFields.getUdpDestinationPort().getValue());
                }
            }
        }
        if (match.getIcmpv4Match() != null) {
            if (match.getIcmpv4Match().getIcmpv4Code() != null) {
                sb.append(",icmp_code=").append(match.getIcmpv4Match().getIcmpv4Code());
            }
            if (match.getIcmpv4Match().getIcmpv4Type() != null) {
                sb.append(",icmp_type=").append(match.getIcmpv4Match().getIcmpv4Type());
            }
        }
        if (match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class) != null) {
            GeneralAugMatchNodesNodeTableFlow nxMatch = match.getAugmentation(GeneralAugMatchNodesNodeTableFlow.class);
            if (nxMatch != null) {
                List<ExtensionList> extList = nxMatch.getExtensionList();
                for (ExtensionList curExt : extList) {
                    NxAugMatchNodesNodeTableFlow nxAug =
                        curExt.getExtension().getAugmentation(NxAugMatchNodesNodeTableFlow.class);
                    if (nxAug != null) {
                        setNxAugString(sb, nxAug);
                    }
                }
            }
        }

        match.toString();
        return sb.toString();
    }

    private static void setNxAugString(StringBuilder sb, NxAugMatchNodesNodeTableFlow nxAug) {

        Class<? extends NxmNxReg> reg = nxAug.getNxmNxReg().getReg();
        if (reg != null) {
            if (reg.equals(NxmNxReg6.class)) {
                sb.append(",reg6=0x").append(Long.toString(nxAug.getNxmNxReg().getValue(), 16));
                if (nxAug.getNxmNxReg().getMask() != null) {
                    sb.append("/0x").append(Long.toString(nxAug.getNxmNxReg().getMask(), 16));
                }
            } else if (reg.equals(NxmNxReg4.class)) {
                sb.append(",reg4=0x").append(Long.toString(nxAug.getNxmNxReg().getValue(), 16));
                if (nxAug.getNxmNxReg().getMask() != null) {
                    sb.append("/0x").append(Long.toString(nxAug.getNxmNxReg().getMask(), 16));
                }
            } else {
                sb.append(",").append(reg.getSimpleName());
            }
        }
        /*
        nxAug.getNxmNxArpSha();
        nxAug.getNxmNxArpTha();
        nxAug.getNxmNxCtState();
        nxAug.getNxmNxCtZone();
        nxAug.getNxmNxEncapEthDst();
        nxAug.getNxmNxEncapEthSrc();
        nxAug.getNxmNxEncapEthType();
        nxAug.getNxmNxNshc1();
        nxAug.getNxmNxNshc2();
        nxAug.getNxmNxNshc3();
        nxAug.getNxmNxNshc4();
        nxAug.getNxmNxNshMdtype();
        nxAug.getNxmNxNshNp();
        nxAug.getNxmNxNsi();
        nxAug.getNxmNxNsp();
        nxAug.getNxmNxTunGpeNp();
        nxAug.getNxmNxTunId();
        nxAug.getNxmNxTunIpv4Dst();
        nxAug.getNxmNxTunIpv4Src();
        nxAug.getNxmOfArpOp();
        nxAug.getNxmOfArpSpa();
        nxAug.getNxmOfArpTpa(); */

        return;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(DataBroker broker,
                                                          LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public void dumpFlowCount(Map<String, Integer> dpnToFlowMap) {
        showFlowCountHeader();
        showFlowCount(dpnToFlowMap);
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void showFlowCountHeader() {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        System.out.println(fmt.format(FLOW_COUNT_FORMAT, "DpnId", "Flow Count"));
        sb.setLength(0);
        System.out.print("------------------------------------------------------------\n");
        sb.setLength(0);
        fmt.close();
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void showFlowCount(Map<String, Integer> dpnToFlowCount) {
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        for (Map.Entry<String, Integer> entry : dpnToFlowCount.entrySet()) {
            System.out.println(fmt.format(FLOW_COUNT_FORMAT, entry.getKey(), entry.getValue()));
            sb.setLength(0);
        }
        fmt.close();
    }

    private String getFlowInstructions(Instructions instructions) {

        String instructionSet = "";
        StringBuffer sb = new StringBuffer();
        Map<Integer, String> orderedInstructions = new TreeMap<>();
        List<Instruction> instructionList = instructions.getInstruction();
        if (instructionList != null) {
            for (Instruction instruction : instructionList) {
                org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.Instruction inst =
                    instruction.getInstruction();
                if (inst instanceof WriteMetadataCase) {
                    WriteMetadataCase writeMetadataCase = (WriteMetadataCase) inst;
                    WriteMetadata writeMetadata = writeMetadataCase.getWriteMetadata();
                    orderedInstructions.put(instruction.getOrder(), "write_metadata:0x"
                        + writeMetadata.getMetadata().toString(16));
                }
                if (inst instanceof GoToTableCase) {
                    GoToTableCase goToTableCase = (GoToTableCase) inst;
                    GoToTable gotoTable = goToTableCase.getGoToTable();
                    orderedInstructions.put(instruction.getOrder(), "goto_table:" + gotoTable.getTableId());
                }
                if (inst instanceof WriteActionsCase) {
                    WriteActionsCase writeActionsCase = (WriteActionsCase) inst;
                    WriteActions writeActions = writeActionsCase.getWriteActions();
                    String actions = getWriteActionsValue(writeActions);
                    if (actions.contains("drop")) {
                        orderedInstructions.put(instruction.getOrder(), actions);
                    } else {
                        orderedInstructions.put(instruction.getOrder(), "write_actions(" + actions + ")");
                    }
                }
                if (inst instanceof ApplyActionsCase) {
                    ApplyActionsCase applyActionsCase = (ApplyActionsCase) inst;
                    ApplyActions applyActions = applyActionsCase.getApplyActions();
                    String actions = getApplyActionsValue(applyActions);
                    orderedInstructions.put(instruction.getOrder(), actions);
                }
            }
            for (Map.Entry<Integer, String> entry : orderedInstructions.entrySet()) {
                sb.append(entry.getValue()).append(",");
            }
        }
        instructionSet = sb.toString();
        if (instructionSet.length() > 0) {
            instructionSet = sb.toString().substring(0, sb.length() - 1);
        }
        return instructionSet;
    }

    private String getWriteActionsValue(WriteActions writeActions) {
        String actions = "";
        StringBuffer sb = new StringBuffer();
        Map<Integer, String> orderedActions = new TreeMap<>();
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actionList =
            writeActions.getAction();
        if (actionList != null) {
            for (Action action : actionList) {
                org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action gotAction =
                    action.getAction();
                if (gotAction instanceof OutputActionCase) {
                    OutputActionCase outputActionCase = (OutputActionCase) gotAction;
                    OutputAction outputAction = outputActionCase.getOutputAction();
                    sb.append(outputAction.getOutputNodeConnector().getValue() + ":"
                        + outputAction.getMaxLength()).append(",");
                }
                if (gotAction instanceof DropActionCase) {
                    DropActionCase dropActionCase = (DropActionCase) gotAction;
                    DropAction dropAction = dropActionCase.getDropAction();
                    sb.append("drop").append(",");
                }
                if (gotAction instanceof PopMplsActionCase) {
                    PopMplsActionCase popMplsActionCase = (PopMplsActionCase) gotAction;
                    PopMplsAction popMplsAction = popMplsActionCase.getPopMplsAction();
                    sb.append("pop_mpls:" + popMplsAction.getEthernetType()).append(",");
                }
                if (gotAction instanceof PushMplsActionCase) {
                    PushMplsActionCase pushMplsActionCase = (PushMplsActionCase) gotAction;
                    PushMplsAction pushMplsAction = pushMplsActionCase.getPushMplsAction();
                    sb.append("push_mpls:" + pushMplsAction.getEthernetType()).append(",");
                }
                if (gotAction instanceof ControllerActionCase) {
                    ControllerActionCase controllerActionCase = (ControllerActionCase) gotAction;
                    ControllerAction controllerAction = controllerActionCase.getControllerAction();
                    sb.append("CONTROLLER:" + controllerAction.getMaxLength()).append(",");
                }
                if (gotAction instanceof GroupActionCase) {
                    GroupActionCase groupActionCase = (GroupActionCase) gotAction;
                    GroupAction groupAction = groupActionCase.getGroupAction();
                    sb.append("group:" + groupAction.getGroupId()).append(",");
                }
            }
        }
        actions = sb.toString();
        if (actions.length() > 0) {
            actions = actions.substring(0, actions.length() - 1);
        }
        return actions;
    }

    private String getApplyActionsValue(ApplyActions applyActions) {
        String actions = "";
        StringBuffer sb = new StringBuffer();
        StringBuffer unknownActions = null;
        Map<Integer, String> orderedActions = new TreeMap<>();
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actionList =
            applyActions.getAction();
        if (actionList != null) {
            for (Action action : actionList) {
                org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action gotAction =
                    action.getAction();
                if (gotAction instanceof OutputActionCase) {
                    OutputActionCase outputActionCase = (OutputActionCase) gotAction;
                    OutputAction outputAction = outputActionCase.getOutputAction();
                    if (outputAction.getOutputNodeConnector().getValue().equals("CONTROLLER")) {
                        sb.append(outputAction.getOutputNodeConnector().getValue() + ":"
                            + outputAction.getMaxLength()).append(",");
                    } else {
                        sb.append("output:" + outputAction.getOutputNodeConnector().getValue()).append(",");
                    }
                } else if (gotAction instanceof DropActionCase) {
                    DropActionCase dropActionCase = (DropActionCase) gotAction;
                    DropAction dropAction = dropActionCase.getDropAction();
                    sb.append("drop").append(",");
                } else if (gotAction instanceof PopMplsActionCase) {
                    PopMplsActionCase popMplsActionCase = (PopMplsActionCase) gotAction;
                    PopMplsAction popMplsAction = popMplsActionCase.getPopMplsAction();
                    sb.append("pop_mpls:" + popMplsAction.getEthernetType()).append(",");
                } else if (gotAction instanceof PushMplsActionCase) {
                    PushMplsActionCase pushMplsActionCase = (PushMplsActionCase) gotAction;
                    PushMplsAction pushMplsAction = pushMplsActionCase.getPushMplsAction();
                    sb.append("push_mpls:" + pushMplsAction.getEthernetType()).append(",");
                } else if (gotAction instanceof ControllerActionCase) {
                    ControllerActionCase controllerActionCase = (ControllerActionCase) gotAction;
                    ControllerAction controllerAction = controllerActionCase.getControllerAction();
                    sb.append("CONTROLLER:" + controllerAction.getMaxLength()).append(",");
                } else if (gotAction instanceof GroupActionCase) {
                    GroupActionCase groupActionCase = (GroupActionCase) gotAction;
                    GroupAction groupAction = groupActionCase.getGroupAction();
                    sb.append("group:" + groupAction.getGroupId()).append(",");
                }
                if (gotAction instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                    NxActionRegLoadNodesNodeTableFlowApplyActionsCase regLoadActionCase =
                        (NxActionRegLoadNodesNodeTableFlowApplyActionsCase) gotAction;
                    NxRegLoad nxRegLoad = regLoadActionCase.getNxRegLoad();
                    FormatActions.getAction(sb, nxRegLoad);
                }
                if (gotAction instanceof NxActionResubmitNodesNodeTableFlowApplyActionsCase) {
                    NxActionResubmitNodesNodeTableFlowApplyActionsCase resubmitActionCase =
                        (NxActionResubmitNodesNodeTableFlowApplyActionsCase) gotAction;
                    NxResubmit nxResubmit = resubmitActionCase.getNxResubmit();
                    FormatActions.getAction(sb, nxResubmit);
                }
                if (gotAction instanceof NxActionLearnNodesNodeTableFlowApplyActionsCase) {
                    NxActionLearnNodesNodeTableFlowApplyActionsCase nxLearnCase =
                        (NxActionLearnNodesNodeTableFlowApplyActionsCase) gotAction;
                    NxLearn nxLearn = nxLearnCase.getNxLearn();
                    FormatActions.getAction(sb, nxLearn);
                } else {
                    // Catch all for ones not supported yet.
                    if (unknownActions == null) {
                        unknownActions = new StringBuffer();
                    }
                    unknownActions.append(gotAction).append(",");
                }
            }
            if (unknownActions != null) {
                LOG.debug("Unsupported actions in use: {}", unknownActions);
            }
        }
        actions = sb.toString();
        if (actions.length() > 0) {
            actions = actions.substring(0, actions.length() - 1);
        }
        return actions;
    }
}
