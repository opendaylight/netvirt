/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;

public class PolicyAceFlowWrapper {

    public static final boolean PARTIAL = true;
    public static final boolean COMPLETE = false;

    private String flowName;
    private List<MatchInfoBase> matches;
    private int priority;
    private final BigInteger dpId;
    private boolean isPartial;

    public PolicyAceFlowWrapper(String flowName, boolean isPartial) {
        this(flowName, null, NwConstants.TABLE_MISS_PRIORITY, BigInteger.ZERO, isPartial);
    }

    public PolicyAceFlowWrapper(String flowName, List<MatchInfoBase> matches, int priority) {
        this(flowName, matches, priority, BigInteger.ZERO);
    }

    public PolicyAceFlowWrapper(String flowName, List<MatchInfoBase> matches, int priority, BigInteger dpId) {
        this(flowName, matches, priority, dpId, false);
    }

    public PolicyAceFlowWrapper(String flowName, List<MatchInfoBase> matches, int priority, BigInteger dpId,
            boolean isPartial) {
        this.flowName = flowName;
        this.matches = matches;
        this.priority = priority;
        this.dpId = dpId;
        this.isPartial = isPartial;
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

    public BigInteger getDpId() {
        return dpId;
    }

    public boolean isPartial() {
        return isPartial;
    }
}
