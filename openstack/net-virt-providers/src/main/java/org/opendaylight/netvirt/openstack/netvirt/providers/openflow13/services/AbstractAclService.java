/*
 * Copyright (c) 2017 NEC Corporation and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import com.google.common.collect.Lists;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityGroupCacheManger;
import org.opendaylight.netvirt.openstack.netvirt.api.SecurityServicesManager;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityRuleCRUD;
import org.opendaylight.netvirt.utils.mdsal.openflow.ActionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.InstructionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;

import java.util.List;

/**
 * Base Class for AclService that provide methods to program Security Group flows
 */
public class AbstractAclService extends AbstractServiceInstance {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAclService.class);
    protected volatile SecurityServicesManager securityServicesManager;
    protected volatile SecurityGroupCacheManger securityGroupCacheManger;
    protected volatile INeutronSecurityRuleCRUD neutronSecurityRule;
    protected static final int PORT_RANGE_MIN = 1;
    protected static final int PORT_RANGE_MAX = 65535;
    protected static final NeutronSecurityRule TCP_SECURITY_GROUP_RULE_ANY = new NeutronSecurityRule();
    protected static final NeutronSecurityRule UDP_SECURITY_GROUP_RULE_ANY = new NeutronSecurityRule();
    protected static final NeutronSecurityRule ICMP_SECURITY_GROUP_RULE_ANY = new NeutronSecurityRule();

    static {
        TCP_SECURITY_GROUP_RULE_ANY.setSecurityRuleProtocol(MatchUtils.TCP);
        UDP_SECURITY_GROUP_RULE_ANY.setSecurityRuleProtocol(MatchUtils.UDP);
        ICMP_SECURITY_GROUP_RULE_ANY.setSecurityRuleProtocol(MatchUtils.ICMP);
    }


    protected AbstractAclService(Service service) {
        super(service);
    }

    protected FlowBuilder addInstructionWithConntrackCommit(FlowBuilder flowBuilder, boolean isDrop) {
        InstructionBuilder instructionBuilder = null;
        if (securityServicesManager.isConntrackEnabled()) {
            Action conntrackAction = ActionUtils.nxConntrackAction(1, 0L, 0, (short) 0xff);
            instructionBuilder = InstructionUtils
                    .createInstructionBuilder(ActionUtils.conntrackActionBuilder(conntrackAction), 1, false);
        }
        return addPipelineInstruction(flowBuilder, instructionBuilder, isDrop);
    }

    protected FlowBuilder addPipelineInstruction(FlowBuilder flowBuilder, InstructionBuilder instructionBuilder,
                                                 boolean isDrop) {
        InstructionBuilder pipeLineIndstructionBuilder = createPipleLineInstructionBuilder(isDrop);
        List<Instruction> instructionsList = Lists.newArrayList();
        instructionsList.add(pipeLineIndstructionBuilder.build());
        if (null != instructionBuilder) {
            instructionsList.add(instructionBuilder.build());
        }
        InstructionsBuilder isb = new InstructionsBuilder();
        isb.setInstruction(instructionsList);
        flowBuilder.setInstructions(isb.build());
        return flowBuilder;
    }

    protected InstructionBuilder createPipleLineInstructionBuilder(boolean drop) {
        InstructionBuilder ib = this.getMutablePipelineInstructionBuilder();
        if (drop) {
            InstructionUtils.createDropInstructions(ib);
        }
        ib.setOrder(0);
        List<Instruction> instructionsList = Lists.newArrayList();
        ib.setKey(new InstructionKey(0));
        instructionsList.add(ib.build());
        return ib;
    }

    /**
     * Add or remove flow to the node.
     *
     * @param flowBuilder the flow builder
     * @param nodeBuilder the node builder
     * @param write       whether it is a write
     */
    protected void syncFlow(FlowBuilder flowBuilder, NodeBuilder nodeBuilder,
                            boolean write) {
        if (write) {
            writeFlow(flowBuilder, nodeBuilder);
        } else {
            removeFlow(flowBuilder, nodeBuilder);
        }
    }

    protected void syncSecurityRuleFlow(FlowBuilder flowBuilder, NodeBuilder nodeBuilder,
                                        boolean write) {
        LOG.info("syncSecurityRuleFlow {} {}", flowBuilder.build(), write);
        if (securityServicesManager.isConntrackEnabled()) {
            syncFlow(flowBuilder, nodeBuilder, write);
        } else {
            if (write) {
                writeFlowWithCounter(flowBuilder, nodeBuilder);
            } else {
                removeFlowWithCounter(flowBuilder, nodeBuilder);
            }
        }
    }

    protected void writeFlowWithCounter(FlowBuilder flowBuilder, NodeBuilder nodeBuilder) {
        Flow flow = getFlow(flowBuilder, nodeBuilder);
        if (flow == null || flow.getCookie() == null) {
            flowBuilder.setCookie(new FlowCookie(BigInteger.ONE));
        } else {
            flowBuilder.setCookie(new FlowCookie(flow.getCookie().getValue().add(BigInteger.ONE)));
        }
        LOG.debug("writeFlowWithCounter {} ", flowBuilder.getCookie());
        writeFlow(flowBuilder, nodeBuilder);
    }

    protected void removeFlowWithCounter(FlowBuilder flowBuilder, NodeBuilder nodeBuilder) {
        Flow flow = getFlow(flowBuilder, nodeBuilder);
        if (flow != null && flow.getCookie() != null) {
            LOG.debug("removeFlowWithCounter {} ", flowBuilder.getCookie());
            FlowCookie flowCookie = flow.getCookie();
            BigInteger cookie = flowCookie.getValue();
            int compareValue = cookie.compareTo(BigInteger.ONE);
            if (compareValue == 0) {
                removeFlow(flowBuilder, nodeBuilder);
            } else if (compareValue == 1) {
                flowBuilder.setCookie(new FlowCookie(flow.getCookie().getValue().subtract(BigInteger.ONE)));
                writeFlow(flowBuilder, nodeBuilder);
            }
        } else {
            removeFlow(flowBuilder, nodeBuilder);
        }
    }

    protected List<NeutronSecurityRule> getSecurityRulesforGroup(NeutronSecurityGroup securityGroup) {
        List<NeutronSecurityRule> securityRules = new ArrayList<>();
        List<NeutronSecurityRule> rules = neutronSecurityRule.getAllNeutronSecurityRules();
        for (NeutronSecurityRule securityRule : rules) {
            if (securityGroup.getID().equals(securityRule.getSecurityRuleGroupID())) {
                securityRules.add(securityRule);
            }
        }
        return securityRules;
    }

    protected void addConntrackMatch(MatchBuilder matchBuilder, int state, int mask) {
        if (securityServicesManager.isConntrackEnabled()) {
            MatchUtils.addCtState(matchBuilder, state, mask);
        }

    }

    protected FlowBuilder addInstructionWithConntrackRecirc(FlowBuilder flowBuilder) {
        InstructionBuilder instructionBuilder = null;
        if (securityServicesManager.isConntrackEnabled()) {
            Action conntrackAction = ActionUtils.nxConntrackAction(0, 0L, 0, (short) 0x0);
            instructionBuilder = InstructionUtils
                    .createInstructionBuilder(ActionUtils.conntrackActionBuilder(conntrackAction), 1, false);
            List<Instruction> instructionsList = Lists.newArrayList();
            instructionsList.add(instructionBuilder.build());
            InstructionsBuilder isb = new InstructionsBuilder();
            isb.setInstruction(instructionsList);
            flowBuilder.setInstructions(isb.build());
        }
        return flowBuilder;
    }
}
