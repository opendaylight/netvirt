/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;

@Singleton
public class PolicyServiceFlowUtil {

    private final IMdsalApiManager mdsalManager;

    @Inject
    public PolicyServiceFlowUtil(final IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public List<InstructionInfo> getPolicyClassifierInstructions(long policyClassifierId) {
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionWriteMetadata(MetaDataUtil.getPolicyClassifierMetaData(policyClassifierId),
                MetaDataUtil.METADATA_MASK_POLICY_CLASSIFER_ID));
        instructions.add(new InstructionGotoTable(NwConstants.EGRESS_POLICY_ROUTING_TABLE));
        return instructions;
    }

    public void updateFlowToTx(BigInteger dpId, short tableId, String flowName, int priority, BigInteger cookie,
            List<? extends MatchInfoBase> matches, List<InstructionInfo> instructions, int addOrRemove,
            WriteTransaction tx) {
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowName, priority, flowName, 0, 0, cookie,
                matches, instructions);
        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.addFlowToTx(flowEntity, tx);
        } else {
            mdsalManager.removeFlowToTx(flowEntity, tx);
        }
    }
}
