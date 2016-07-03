/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Icmpv6MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv6MatchBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ipv6ServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceUtils.class);
    public static final int ADD_FLOW = 1;
    public static final int DEL_FLOW = 0;

    private static final String ICMPv6_TO_CONTROLLER_RS_FLOW = "ICMPv6RS";
    private static final int ICMPv6_FLOW_PRIORITY = 100;
    private static int FLOW_HARD_TIMEOUT = 0;
    private static int FLOW_IDLE_TIMEOUT = 0;
    private static final int ICMPv6_TYPE_RS = 133;
    private static final int ICMPv6_TYPE_NS = 135;
    private static final short IPV6SERVICE_TABLE = 16;
    private ConcurrentMap<String, InstanceIdentifier<Flow>> icmpv6FlowMap;

    public Ipv6ServiceUtils() {
        icmpv6FlowMap = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves the object from the datastore.
     * @param broker the data broker.
     * @param datastoreType the data store type.
     * @param path the wild card path.
     * @return the required object.
     */
    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();
        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tx.close();
        }
        return result;
    }

    /**
     * Retrieves the Interface from the datastore.
     * @param broker the data broker
     * @param interfaceName the interface name
     * @return the interface.
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .Interface getInterface(DataBroker broker, String interfaceName) {
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .Interface> optInterface =
                read(broker, LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName));
        if (optInterface.isPresent()) {
            return optInterface.get();
        }
        return null;
    }

    /**
     * Builds the interface identifier.
     * @param interfaceName the interface name.
     * @return the interface identifier.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class)
                .child(
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                                .Interface.class, new InterfaceKey(interfaceName)).build();
    }


    /**
     * Program flow to punt Router Solicitation to Controller.
     * @param nodeName the datapath id.
     * @param broker Databroker.
     * @param addOrRemove boolean flag to indicate if a flow has to be programmed or removed.
     * @return none
     */
    public void programRouterSolicitationEntry(String nodeName, DataBroker broker, int addOrRemove) {
        String flowName = ICMPv6_TO_CONTROLLER_RS_FLOW + "_" + ICMPv6_TYPE_RS;
        if (addOrRemove == ADD_FLOW) {
            programIcmpv6Flow(nodeName, broker, flowName, ICMPv6_TYPE_RS, 0);
        } else {
            removeIcmpv6Flow(nodeName, broker);
        }
    }

    private void removeIcmpv6Flow(String nodeName, DataBroker broker) {
        final InstanceIdentifier<Flow> flowIid = icmpv6FlowMap.get(nodeName);
        if (flowIid == null) {
            LOG.debug("ICMP6 RS Flow is not programmed in the node {}", nodeName);
            return;
        }

        LOG.trace("Removing ICMPv6 Router Solicitation Flow from the node {}", nodeName);

        WriteTransaction write = broker.newWriteOnlyTransaction();
        write.delete(LogicalDatastoreType.CONFIGURATION, flowIid);
        CheckedFuture<Void, TransactionCommitFailedException> checkFuture = write.submit();

        try {
            checkFuture.checkedGet();
            LOG.debug("Transaction success for delete of Flow {}", flowIid);
            icmpv6FlowMap.remove(nodeName);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            write.cancel();
        }
    }

    private void programIcmpv6Flow(String nodeName, DataBroker broker, String flowName,
                                          int icmpType, long ofPort) {
        final InstanceIdentifier<Node> nodeIid = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(nodeName))).build();

        String portName = nodeName + ":" + ofPort;
        final Flow icmpv6ToControllerFlow = createIcmpv6ToControllerFlow(nodeIid, flowName, icmpType, portName, null);
        writeFlow(nodeName, broker, nodeIid, icmpv6ToControllerFlow);
    }

    private Flow createIcmpv6ToControllerFlow(InstanceIdentifier<Node> nodeIid, String flowName,
                                              int icmpType, String portName, Ipv6Address address) {
        Preconditions.checkNotNull(nodeIid);

        MatchBuilder matchBuilder = new MatchBuilder();
        EthernetTypeBuilder ethTypeBuilder = new EthernetTypeBuilder();
        ethTypeBuilder.setType(new EtherType(0x86DDL));
        EthernetMatchBuilder eth = new EthernetMatchBuilder();
        eth.setEthernetType(ethTypeBuilder.build());
        matchBuilder.setEthernetMatch(eth.build());

        IpMatchBuilder ipmatch = new IpMatchBuilder();
        ipmatch.setIpProtocol((short) 58);
        matchBuilder.setIpMatch(ipmatch.build());

        final Icmpv6MatchBuilder icmpv6match = new Icmpv6MatchBuilder();
        icmpv6match.setIcmpv6Type((short) icmpType);
        matchBuilder.setIcmpv6Match(icmpv6match.build());

        if (icmpType == ICMPv6_TYPE_NS) {
            if (address != null) {
                Ipv6MatchBuilder ipv6Match = new Ipv6MatchBuilder();
                ipv6Match.setIpv6NdTarget(address);
                matchBuilder.setLayer3Match(ipv6Match.build());
            } else {
                NodeConnectorId ncId = NodeConnectorId.getDefaultInstance(portName);
                matchBuilder.setInPort(ncId);
            }
        }

        Action sendToControllerAction = new ActionBuilder().setOrder(0)
                .setKey(new ActionKey(0))
                .setAction(
                        new OutputActionCaseBuilder().setOutputAction(
                                new OutputActionBuilder().setMaxLength(0xffff)
                                        .setOutputNodeConnector(new Uri(OutputPortValues.CONTROLLER.toString()))
                                        .build()).build())
                .build();

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(
                ImmutableList.of(sendToControllerAction)).build();
        Instruction sendToControllerInstruction = new InstructionBuilder().setOrder(0)
                .setInstruction(new ApplyActionsCaseBuilder().setApplyActions(applyActions).build())
                .build();

        FlowBuilder icmpv6Flow = new FlowBuilder().setTableId(IPV6SERVICE_TABLE)
                .setFlowName(flowName)
                .setPriority(ICMPv6_FLOW_PRIORITY)
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setIdleTimeout(FLOW_IDLE_TIMEOUT)
                .setHardTimeout(FLOW_HARD_TIMEOUT)
                .setCookie(new FlowCookie(BigInteger.valueOf(0)))
                .setFlags(new FlowModFlags(false, false, false, false, false));
        icmpv6Flow.setMatch(matchBuilder.build());
        icmpv6Flow.setInstructions(new InstructionsBuilder().setInstruction(
                ImmutableList.of(sendToControllerInstruction)).build());
        icmpv6Flow.setId(new FlowId(flowName));
        return icmpv6Flow.build();
    }

    private static InstanceIdentifier<Flow> createFlowIid(Flow flow, InstanceIdentifier<Node> nodeIid) {
        return nodeIid.builder()
                .augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(flow.getTableId()))
                .child(Flow.class, new FlowKey(flow.getId()))
                .build();
    }

    private void writeFlow(String nodeName, DataBroker broker, InstanceIdentifier<Node> nodeIid,
                           Flow icmpv6ToControllerFlow) {
        final InstanceIdentifier<Flow> flowIid = createFlowIid(icmpv6ToControllerFlow, nodeIid);
        WriteTransaction write = broker.newWriteOnlyTransaction();
        write.put(LogicalDatastoreType.CONFIGURATION, flowIid, icmpv6ToControllerFlow, true);
        CheckedFuture<Void, TransactionCommitFailedException> checkFuture = write.submit();

        try {
            checkFuture.checkedGet();
            LOG.debug("Transaction success for write of Flow {}", flowIid);
            icmpv6FlowMap.put(nodeName, flowIid);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            write.cancel();
        }
    }
}
