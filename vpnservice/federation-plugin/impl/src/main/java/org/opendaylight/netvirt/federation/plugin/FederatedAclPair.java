/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class FederatedAclPair {
    public Uuid consumerAclId;
    public Uuid producerAclId;

    public FederatedAclPair(Uuid consumerSecGroupId, Uuid producerSecGroupId) {
        this.consumerAclId = consumerSecGroupId;
        this.producerAclId = producerSecGroupId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (consumerAclId == null ? 0 : consumerAclId.hashCode());
        result = prime * result + (producerAclId == null ? 0 : producerAclId.hashCode());
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
        FederatedAclPair other = (FederatedAclPair) obj;
        if (consumerAclId == null) {
            if (other.consumerAclId != null) {
                return false;
            }
        } else if (!consumerAclId.equals(other.consumerAclId)) {
            return false;
        }
        if (producerAclId == null) {
            if (other.producerAclId != null) {
                return false;
            }
        } else if (!producerAclId.equals(other.producerAclId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "FederatedAclPair [consumerAclId=" + consumerAclId + ", producerAclId=" + producerAclId + "]";
    }
}
