/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;

public final class GroupInfoKey {

    private final BigInteger dpId;
    private final long groupId;

    public GroupInfoKey(BigInteger dpId, long groupId) {
        this.dpId = dpId;
        this.groupId = groupId;
    }

    public long getGroupId() {
        return groupId;
    }

    public BigInteger getDpId() {
        return dpId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dpId == null) ? 0 : dpId.hashCode());
        result = prime * result + (int) (groupId ^ (groupId >>> 32));
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
        GroupInfoKey other = (GroupInfoKey) obj;
        if (dpId == null) {
            if (other.dpId != null)
                return false;
        } else if (!dpId.equals(other.dpId))
            return false;
        if (groupId != other.groupId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "GroupStatisticsKey [dpId=" + dpId + ", groupId=" + groupId + "]";
    }

}
