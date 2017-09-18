/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchTcpSourcePort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpDestinationPort;
import org.opendaylight.genius.mdsalutil.matches.MatchUdpSourcePort;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.EgressElementCountersRequestConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.statistics.rev170120.IngressElementCountersRequestConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@SuppressWarnings("deprecation")
public class CountersServiceUtils {

    public static final Long COUNTERS_PULL_END = Long.valueOf(100000);
    public static final Long COUNTERS_PULL_START = Long.valueOf(1);
    public static final short COUNTER_TABLE_COUNTER_FLOW_PRIORITY = 50;
    public static final short COUNTER_TABLE_DEFAULT_FLOW_PRIORITY = 1;
    public static final BigInteger COOKIE_COUNTERS_BASE = new BigInteger("7000000", 16);
    public static final short EGRESS_COUNTERS_DEFAULT_FLOW_PRIORITY = 50;
    public static final short INGRESS_COUNTERS_DEFAULT_FLOW_PRIORITY = 2;
    public static final short EGRESS_COUNTERS_SERVICE_INDEX =
            ServiceIndex.getIndex(NwConstants.EGRESS_COUNTERS_SERVICE_NAME, NwConstants.EGRESS_COUNTERS_SERVICE_INDEX);
    public static final short INGRESS_COUNTERS_SERVICE_INDEX = ServiceIndex
            .getIndex(NwConstants.INGRESS_COUNTERS_SERVICE_NAME, NwConstants.INGRESS_COUNTERS_SERVICE_INDEX);

    public static final String COUNTER_FLOW_NAME = "COUNTER";
    public static final String COUNTERS_PULL_NAME = "CountersPull";
    public static final String DEFAULT_EGRESS_COUNTER_FLOW_PREFIX = "Egress_Counters_Default";
    public static final String DEFAULT_INGRESS_COUNTER_FLOW_PREFIX = "Ingress_Counters_Default";
    public static final String EGRESS_COUNTER_RESULT_ID = "Incoming Traffic";
    public static final String INGRESS_COUNTER_RESULT_ID = "Outgoing Traffic";

    public static final InstanceIdentifier<EgressElementCountersRequestConfig> EECRC_IDENTIFIER =
            InstanceIdentifier.builder(EgressElementCountersRequestConfig.class).build();
    public static final InstanceIdentifier<IngressElementCountersRequestConfig> IECRC_IDENTIFIER =
            InstanceIdentifier.builder(IngressElementCountersRequestConfig.class).build();

    private static AtomicLong flowIdInc = new AtomicLong(2);

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
            BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short priority,
            Class<? extends ServiceModeBase> mode) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, mode))
                .child(BoundServices.class, new BoundServicesKey(priority)).build();
    }

    public static MatchInfoBase buildLPortTagMatch(int lportTag, ElementCountersDirection direction) {
        if (ElementCountersDirection.INGRESS.equals(direction)) {
            return new MatchMetadata(MetaDataUtil.getLportTagMetaData(lportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG);
        } else if (ElementCountersDirection.EGRESS.equals(direction)) {
            return new NxMatchRegister(NxmNxReg6.class, MetaDataUtil.getLportTagForReg6(lportTag).longValue(),
                    MetaDataUtil.getLportTagMaskForReg6());
        }
        return null;
    }

    public static List<InstructionInfo> getDispatcherTableResubmitInstructions(List<ActionInfo> actionsInfos,
            ElementCountersDirection direction) {
        short dispatcherTableId = NwConstants.LPORT_DISPATCHER_TABLE;
        if (ElementCountersDirection.EGRESS.equals(direction)) {
            dispatcherTableId = NwConstants.EGRESS_LPORT_DISPATCHER_TABLE;
        }

        List<InstructionInfo> instructions = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(dispatcherTableId));
        instructions.add(new InstructionApplyActions(actionsInfos));
        return instructions;
    }

    public static List<MatchInfoBase> getCounterFlowMatch(ElementCountersRequest ecr, int lportTag,
            ElementCountersDirection direction) {
        List<MatchInfoBase> matches = new ArrayList<>();
        if (ecr.isFilterExist(CountersUtils.ELEMENT_COUNTERS_IP_FILTER_GROUP_NAME, CountersUtils.IP_FILTER_NAME)) {
            String ipFilter = ecr.getFilterFromFilterGroup(CountersUtils.ELEMENT_COUNTERS_IP_FILTER_GROUP_NAME,
                    CountersUtils.IP_FILTER_NAME);
            matches.addAll(buildIpMatches(ipFilter, direction));
        }

        boolean tcpFilterExist = buildTcpMatchIfExists(ecr, matches);

        if (!tcpFilterExist) {
            buildUdpMatchIfExists(ecr, matches);
        }

        matches.add(CountersServiceUtils.buildLPortTagMatch(lportTag, direction));

        return matches;
    }

    public static Short getTableId(ElementCountersDirection direction) {
        if (ElementCountersDirection.INGRESS.equals(direction)) {
            return NwConstants.INGRESS_COUNTERS_TABLE;
        } else if (ElementCountersDirection.EGRESS.equals(direction)) {
            return NwConstants.EGRESS_COUNTERS_TABLE;
        }
        return null;
    }

    public static NodeRef buildNodeRef(BigInteger dpId) {
        return new NodeRef(InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(CountersUtils.OF_PREFIX + dpId))).build());
    }

    public static Flow createFlowOnTable(Match match, int priority, short tableId) {
        return createFlowOnTable(match, priority, tableId, null, null);
    }

    public static Flow createFlowOnTable(Match match, int priority, short tableId, BigInteger cookie,
            Integer timeout) {
        FlowBuilder fb = new FlowBuilder();
        if (match != null) {
            fb.setMatch(match);
        }
        FlowId flowId = createFlowId();
        fb.setTableId(tableId);
        fb.setIdleTimeout(0).setHardTimeout(0);
        fb.setId(flowId);
        if (timeout != null) {
            fb.setHardTimeout(timeout);
        }
        if (cookie != null) {
            fb.setCookie(new FlowCookie(cookie));
        }

        fb.setPriority(priority);
        Flow flow = fb.build();
        return flow;
    }

    public static FlowId createFlowId() {
        return getFlowIdUsingCounter();
    }

    private static FlowId getFlowIdUsingCounter() {
        return new FlowId(String.valueOf(flowIdInc.incrementAndGet()));
    }

    private static boolean buildUdpMatchIfExists(ElementCountersRequest ecr, List<MatchInfoBase> matches) {
        boolean udpFilterExist = false;
        if (ecr.isFilterGroupExist(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME)) {
            udpFilterExist = true;
            matches.add(MatchIpProtocol.UDP);
        }
        if (ecr.isFilterExist(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                CountersUtils.UDP_SRC_PORT_FILTER_NAME)) {
            Integer udpSrcPort =
                    Integer.valueOf(ecr.getFilterFromFilterGroup(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                            CountersUtils.UDP_SRC_PORT_FILTER_NAME));
            matches.add(new MatchUdpSourcePort(udpSrcPort));
        }
        if (ecr.isFilterExist(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                CountersUtils.UDP_DST_PORT_FILTER_NAME)) {
            Integer udpDstPort =
                    Integer.valueOf(ecr.getFilterFromFilterGroup(CountersUtils.ELEMENT_COUNTERS_UDP_FILTER_GROUP_NAME,
                            CountersUtils.UDP_DST_PORT_FILTER_NAME));
            matches.add(new MatchUdpDestinationPort(udpDstPort));
        }

        return udpFilterExist;
    }

    private static boolean buildTcpMatchIfExists(ElementCountersRequest ecr, List<MatchInfoBase> matches) {
        boolean tcpFilterExist = false;
        if (ecr.isFilterGroupExist(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME)) {
            tcpFilterExist = true;
            matches.add(MatchIpProtocol.TCP);
        }
        if (ecr.isFilterExist(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                CountersUtils.TCP_SRC_PORT_FILTER_NAME)) {
            Integer tcpSrcPort =
                    Integer.valueOf(ecr.getFilterFromFilterGroup(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                            CountersUtils.TCP_SRC_PORT_FILTER_NAME));
            matches.add(new MatchTcpSourcePort(Integer.valueOf(tcpSrcPort)));
        }
        if (ecr.isFilterExist(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                CountersUtils.TCP_DST_PORT_FILTER_NAME)) {
            Integer tcpDstPort =
                    Integer.valueOf(ecr.getFilterFromFilterGroup(CountersUtils.ELEMENT_COUNTERS_TCP_FILTER_GROUP_NAME,
                            CountersUtils.TCP_DST_PORT_FILTER_NAME));
            matches.add(new MatchTcpDestinationPort(tcpDstPort));
        }

        return tcpFilterExist;
    }

    private static List<MatchInfoBase> buildIpMatches(String ip, ElementCountersDirection direction) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        IpAddress ipAddress = new IpAddress(ip.toCharArray());
        if (ipAddress.getIpv4Address() != null) {
            flowMatches.add(MatchEthernetType.IPV4);
            flowMatches.add(direction == ElementCountersDirection.EGRESS
                    ? new MatchIpv4Source(ipAddress.getIpv4Address().getValue(), "32")
                    : new MatchIpv4Destination(ipAddress.getIpv4Address().getValue(), "32"));
        } else {
            flowMatches.add(MatchEthernetType.IPV6);
            flowMatches.add(direction == ElementCountersDirection.EGRESS
                    ? new MatchIpv6Source(ipAddress.getIpv6Address().getValue() + "/128")
                    : new MatchIpv6Destination(ipAddress.getIpv6Address().getValue() + "/128"));
        }
        return flowMatches;
    }

}