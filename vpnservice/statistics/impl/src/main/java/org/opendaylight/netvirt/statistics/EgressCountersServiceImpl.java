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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@SuppressWarnings("deprecation")
public class EgressCountersServiceImpl extends AbstractCountersService {

    public EgressCountersServiceImpl(DataBroker db, IInterfaceManager interfaceManager,
            IMdsalApiManager mdsalApiManager) {
        super(db, interfaceManager, mdsalApiManager);
    }

    @Override
    public void bind(String interfaceId) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions
                .add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.EGRESS_COUNTERS_TABLE, ++instructionKey));
        BoundServices serviceInfo =
                CountersServiceUtils.getBoundServices(String.format("%s.%s", "counters-egress", interfaceId),
                        CountersServiceUtils.EGRESS_COUNTERS_SERVICE_INDEX,
                        CountersServiceUtils.EGRESS_COUNTERS_DEFAULT_FLOW_PRIORITY,
                        CountersServiceUtils.COOKIE_COUNTERS_BASE, instructions);
        InstanceIdentifier<BoundServices> serviceId = CountersServiceUtils.buildServiceId(interfaceId,
                CountersServiceUtils.EGRESS_COUNTERS_SERVICE_INDEX, ServiceModeEgress.class);
        MDSALUtil.syncWrite(db, LogicalDatastoreType.CONFIGURATION, serviceId, serviceInfo);
    }

    @Override
    public void installDefaultCounterRules(String interfaceId) {
        BigInteger dpn = interfaceManager.getDpnForInterface(interfaceId);
        String defaultFlowName = CountersServiceUtils.DEFAULT_EGRESS_COUNTER_FLOW_PREFIX + dpn + interfaceId;
        List<MatchInfoBase> defaultFlowMatches = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = CountersServiceUtils.getDispatcherTableResubmitInstructions(actionsInfos,
                ElementCountersDirection.EGRESS);
        addFlow(dpn, NwConstants.EGRESS_COUNTERS_TABLE, defaultFlowName,
                CountersServiceUtils.COUNTER_TABLE_DEFAULT_FLOW_PRIORITY, CountersServiceUtils.COUNTER_FLOW_NAME, 0, 0,
                CountersServiceUtils.COOKIE_COUNTERS_BASE, defaultFlowMatches, instructions);
    }

    @Override
    public void unBindService(String interfaceId) {
        InstanceIdentifier<BoundServices> serviceId = CountersServiceUtils.buildServiceId(interfaceId,
                CountersServiceUtils.EGRESS_COUNTERS_SERVICE_INDEX, ServiceModeEgress.class);
        MDSALUtil.syncDelete(db, LogicalDatastoreType.CONFIGURATION, serviceId);
    }

    @Override
    public void installCounterRules(ElementCountersRequest ecr) {
        int lportTag = ecr.getLportTag();
        List<MatchInfoBase> flowMatches =
                CountersServiceUtils.getCounterFlowMatch(ecr, lportTag, ElementCountersDirection.EGRESS);
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<InstructionInfo> instructions = CountersServiceUtils.getDispatcherTableResubmitInstructions(actionsInfos,
                ElementCountersDirection.EGRESS);

        BigInteger dpn = ecr.getDpn();
        String flowName = createFlowName(ecr, lportTag, dpn);

        addFlow(dpn, NwConstants.EGRESS_COUNTERS_TABLE, flowName,
                CountersServiceUtils.COUNTER_TABLE_COUNTER_FLOW_PRIORITY, CountersServiceUtils.COUNTER_FLOW_NAME, 0, 0,
                CountersServiceUtils.COOKIE_COUNTERS_BASE, flowMatches, instructions);
    }

    @Override
    public void deleteCounterRules(ElementCountersRequest ecr) {
        int lportTag = ecr.getLportTag();
        BigInteger dpn = ecr.getDpn();
        String flowName = createFlowName(ecr, lportTag, dpn);

        deleteFlow(dpn, NwConstants.EGRESS_COUNTERS_TABLE, flowName);
    }

    private String createFlowName(ElementCountersRequest ecr, int lportTag, BigInteger dpn) {
        return "Egress_Counters" + dpn + "_" + lportTag + "_" + ecr.toString().hashCode();
    }
}