/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import org.mockito.ArgumentMatcher;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MatchInfo;

public class FlowEntityMatcher extends ArgumentMatcher<FlowEntity> {

    FlowEntity expectedFlow;

    public FlowEntityMatcher(FlowEntity expectedFlow) {
        this.expectedFlow = expectedFlow;
    }

    public boolean sameMatch(MatchInfo match1, MatchInfo match2 ) {
        // TODO: implement this
        return true;
    }

    public boolean sameInstructions(InstructionInfo instructions1, InstructionInfo instructions2) {
        // TODO: implement this
        return true;
    }

    @Override
    public boolean matches(Object actualFlow) {
        if ( ! ( actualFlow instanceof FlowEntity ) ) {
            return false;
        }

        FlowEntity flow = (FlowEntity) actualFlow;

        boolean result =
            flow.getDpnId() == expectedFlow.getDpnId()
            && flow.getFlowBuilder().getMatch().equals(expectedFlow.getFlowBuilder().getMatch())
            && flow.getFlowBuilder().getFlowName().equals(expectedFlow.getFlowBuilder().getFlowName())
            && flow.getFlowBuilder().getInstructions().equals(expectedFlow.getFlowBuilder().getInstructions())
            && flow.getFlowBuilder().getTableId().equals(expectedFlow.getFlowBuilder().getTableId())
            && flow.getFlowBuilder().getPriority().equals(expectedFlow.getFlowBuilder().getPriority());


        return result;
    }

}
