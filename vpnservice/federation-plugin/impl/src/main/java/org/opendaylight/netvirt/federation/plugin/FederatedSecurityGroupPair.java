/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class FederatedSecurityGroupPair {
    public Uuid consumerSecGroupId;
    public Uuid producerSecGroupId;

    public FederatedSecurityGroupPair(Uuid consumerSecGroupId, Uuid producerSecGroupId) {
        this.consumerSecGroupId = consumerSecGroupId;
        this.producerSecGroupId = producerSecGroupId;
    }

    @Override
    public String toString() {
        return "FederatedSecurityGroupPair [consumerSecGroupId=" + consumerSecGroupId + ", producerSecGroupId="
            + producerSecGroupId + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (consumerSecGroupId == null ? 0 : consumerSecGroupId.hashCode());
        result = prime * result + (producerSecGroupId == null ? 0 : producerSecGroupId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FederatedSecurityGroupPair other = (FederatedSecurityGroupPair) obj;
        if (consumerSecGroupId == null) {
            if (other.consumerSecGroupId != null) {
                return false;
            }
        } else if (!consumerSecGroupId.equals(other.consumerSecGroupId)) {
            return false;
        }
        if (producerSecGroupId == null) {
            if (other.producerSecGroupId != null) {
                return false;
            }
        } else if (!producerSecGroupId.equals(other.producerSecGroupId)) {
            return false;
        }
        return true;
    }
}
