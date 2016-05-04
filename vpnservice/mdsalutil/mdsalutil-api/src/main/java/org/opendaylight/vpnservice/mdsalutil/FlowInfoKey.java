/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;

public final class FlowInfoKey {

    private final BigInteger dpId;
    private final short tableId;
    private final Match matches;
    private final String flowId;

    public FlowInfoKey(BigInteger dpId, short tableId, Match matches, String flowId) {
        this.dpId = dpId;
        this.tableId = tableId;
        this.matches = matches;
        this.flowId = flowId;
    }

    public short getTableId() {
        return tableId;
    }

    public Match getMatches() {
        return matches;
    }

    public BigInteger getDpId() {
        return dpId;
    }

    public String getFlowId() {
        return flowId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dpId == null) ? 0 : dpId.hashCode());
        result = prime * result + ((matches == null) ? 0 : matches.hashCode());
        result = prime * result + tableId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FlowInfoKey other = (FlowInfoKey) obj;
        if (dpId == null) {
            if (other.dpId != null)
                return false;
        } else if (!dpId.equals(other.dpId))
            return false;
        if (matches == null) {
            if (other.matches != null)
                return false;
        } else if (!matches.equals(other.matches))
            return false;
        if (tableId != other.tableId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "FlowStatisticsKey [dpId=" + dpId + ", tableId=" + tableId + ", matches=" + matches + "]";
    }

}
