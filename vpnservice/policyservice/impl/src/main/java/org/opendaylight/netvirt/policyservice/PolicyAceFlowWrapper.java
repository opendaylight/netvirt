/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import java.util.List;

import org.opendaylight.genius.mdsalutil.MatchInfoBase;

public class PolicyAceFlowWrapper {

    private String flowName;
    private List<MatchInfoBase> matches;
    private int priority;

    public PolicyAceFlowWrapper(String flowName, List<MatchInfoBase> matches, int priority) {
        this.flowName = flowName;
        this.matches = matches;
        this.priority = priority;
    }

    public String getFlowName() {
        return flowName;
    }

    public List<MatchInfoBase> getMatches() {
        return matches;
    }

    public int getPriority() {
        return priority;
    }

}
