package org.opendaylight.netvirt.cloudservicechain.matchers;

import org.apache.commons.lang3.StringUtils;
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
        boolean result = true;
        FlowEntity flow = (FlowEntity) actualFlow;
//      flow.getId() != null && flow.getId().equals(expectedFlow.getId() )
//      && flow.getTableId() == expectedFlow.getTableId()
//      && StringUtils.equals(flow.getFlowName(), expectedFlow.getFlowName() )
//      && sameInstructions(flow.getInstructions(), expectedFlow.getInstructions())
//      && sameMatch(flow.getMatch(), expectedFlow.getMatch() );
        return result;
    }

}
