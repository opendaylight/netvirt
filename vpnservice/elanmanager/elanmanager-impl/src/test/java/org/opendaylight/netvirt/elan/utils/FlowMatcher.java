/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.mockito.ArgumentMatcher;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Instructions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;

public class FlowMatcher extends ArgumentMatcher<Flow> {

    Flow expectedFlow;

    public FlowMatcher(Flow expectedFlow) {
        this.expectedFlow = expectedFlow;
    }

    public boolean sameMatch(Match match1, Match match2 ) {
        if ( !match1.getMetadata().equals(match2.getMetadata()) ) {
            return false;
        }
        // TODO: implement more
        return true;
    }

    public boolean sameInstruction(Instruction inst1, Instruction inst2) {
        return inst1.getInstruction()
                    .getImplementedInterface()
                    .getSimpleName()
                    .equals(inst2.getInstruction()
                                 .getImplementedInterface()
                                 .getSimpleName());
    }

    public boolean containsInstruction(Instruction inst1, List<Instruction> inst2List) {
        for ( Instruction inst2 : inst2List) {
            if ( sameInstruction(inst1, inst2 )) {
                return true;
            }
        }
        return false;
    }

    public boolean sameInstructions(Instructions inst1, Instructions inst2) {
        if ( inst1.getInstruction().size() != inst2.getInstruction().size() ) {
            return false;
        }

        for ( Instruction inst : inst1.getInstruction() ) {
            if ( !containsInstruction(inst, inst2.getInstruction())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matches(Object actualFlow) {
        if ( ! ( actualFlow instanceof Flow ) ) {
            return false;
        }
        Flow flow = (Flow) actualFlow;

        boolean result =
                flow.getId() != null && flow.getId().equals(expectedFlow.getId() )
                && flow.getTableId() == expectedFlow.getTableId()
                && StringUtils.equals(flow.getFlowName(), expectedFlow.getFlowName() )
                && sameInstructions(flow.getInstructions(), expectedFlow.getInstructions())
                && sameMatch(flow.getMatch(), expectedFlow.getMatch() );

        return result;
    }

}
