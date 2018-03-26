/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.math.BigInteger;
import java.util.List;
import java.util.SortedSet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.port.subnets.port.subnet.SubnetInfo;

/**
 * The Class AclInterface.
 */
public final class AclInterface {

    /** The interface id. */
    private final String interfaceId;

    /** The l port tag. */
    private final Integer lportTag;

    /** The dp id. */
    private final BigInteger dpId;

    /** Elan tag of the interface. */
    private final Long elanId;

    /** The port security enabled. */
    private final boolean portSecurityEnabled;

    /** The security groups. */
    private final List<Uuid> securityGroups;

    /** The allowed address pairs. */
    private final List<AllowedAddressPairs> allowedAddressPairs;

    /** List to contain subnet IP CIDRs along with subnet gateway IP. */
    List<SubnetInfo> subnetInfo;

    /** The ingress remote acl tags. */
    private final SortedSet<Integer> ingressRemoteAclTags;

    /** The egress remote acl tags. */
    private final SortedSet<Integer> egressRemoteAclTags;

    /** The port is marked for delete. */
    private volatile boolean isMarkedForDelete;

    private AclInterface(Builder builder) {
        this.interfaceId = builder.interfaceId;
        this.lportTag = builder.lportTag;
        this.dpId = builder.dpId;
        this.elanId = builder.elanId;
        this.portSecurityEnabled = builder.portSecurityEnabled;
        this.securityGroups = builder.securityGroups;
        this.allowedAddressPairs = builder.allowedAddressPairs;
        this.subnetInfo = builder.subnetInfo;
        this.ingressRemoteAclTags = builder.ingressRemoteAclTags;
        this.egressRemoteAclTags = builder.egressRemoteAclTags;
        this.isMarkedForDelete = builder.isMarkedForDelete;
    }

    /**
     * Checks if is port security enabled.
     *
     * @return the boolean
     */
    public boolean isPortSecurityEnabled() {
        return portSecurityEnabled;
    }

    /**
     * Gets the interface id.
     *
     * @return the interface id
     */
    public String getInterfaceId() {
        return interfaceId;
    }

    /**
     * Gets the l port tag.
     *
     * @return the l port tag
     */
    public Integer getLPortTag() {
        return lportTag;
    }

    /**
     * Gets the dp id.
     *
     * @return the dp id
     */
    public BigInteger getDpId() {
        return dpId;
    }

    /**
     * Gets elan id.
     *
     * @return elan id of the interface
     */
    public Long getElanId() {
        return elanId;
    }

    /**
     * Gets the security groups.
     *
     * @return the security groups
     */
    public List<Uuid> getSecurityGroups() {
        return securityGroups;
    }

    /**
     * Gets the allowed address pairs.
     *
     * @return the allowed address pairs
     */
    public List<AllowedAddressPairs> getAllowedAddressPairs() {
        return allowedAddressPairs;
    }

    /**
     * Gets the Subnet info.
     *
     * @return the Subnet info
     */
    public List<SubnetInfo> getSubnetInfo() {
        return subnetInfo;
    }

    /**
     * Gets the remote acl tags.
     *
     * @param direction the direction
     * @return the remote acl tags
     */
    public SortedSet<Integer> getRemoteAclTags(Class<? extends DirectionBase> direction) {
        return DirectionEgress.class.equals(direction) ? egressRemoteAclTags : ingressRemoteAclTags;
    }

    /**
     * Gets the egress remote acl tags.
     *
     * @return the egress remote acl tags
     */
    public SortedSet<Integer> getEgressRemoteAclTags() {
        return egressRemoteAclTags;
    }

    /**
     * Gets the ingress remote acl tags.
     *
     * @return the ingress remote acl tags
     */
    public SortedSet<Integer> getIngressRemoteAclTags() {
        return ingressRemoteAclTags;
    }

    /**
     * Retrieve isMarkedForDelete.
     * @return the whether it is marked for delete
     */
    public boolean isMarkedForDelete() {
        return isMarkedForDelete;
    }

    /**
     * Sets isMarkedForDelete.
     * @param isMarkedForDelete boolean value
     */
    public void setIsMarkedForDelete(boolean isMarkedForDelete) {
        this.isMarkedForDelete = isMarkedForDelete;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Boolean.hashCode(portSecurityEnabled);
        result = prime * result + (dpId == null ? 0 : dpId.hashCode());
        result = prime * result + (interfaceId == null ? 0 : interfaceId.hashCode());
        result = prime * result + (lportTag == null ? 0 : lportTag.hashCode());
        result = prime * result + (securityGroups == null ? 0 : securityGroups.hashCode());
        result = prime * result + (allowedAddressPairs == null ? 0 : allowedAddressPairs.hashCode());
        result = prime * result + Boolean.hashCode(isMarkedForDelete);
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
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
        AclInterface other = (AclInterface) obj;
        if (portSecurityEnabled != other.portSecurityEnabled) {
            return false;
        }
        if (dpId == null) {
            if (other.dpId != null) {
                return false;
            }
        } else if (!dpId.equals(other.dpId)) {
            return false;
        }
        if (interfaceId == null) {
            if (other.interfaceId != null) {
                return false;
            }
        } else if (!interfaceId.equals(other.interfaceId)) {
            return false;
        }
        if (lportTag == null) {
            if (other.lportTag != null) {
                return false;
            }
        } else if (!lportTag.equals(other.lportTag)) {
            return false;
        }
        if (securityGroups == null) {
            if (other.securityGroups != null) {
                return false;
            }
        } else if (!securityGroups.equals(other.securityGroups)) {
            return false;
        }
        if (allowedAddressPairs == null) {
            if (other.allowedAddressPairs != null) {
                return false;
            }
        } else if (!allowedAddressPairs.equals(other.allowedAddressPairs)) {
            return false;
        }
        if (isMarkedForDelete != other.isMarkedForDelete) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "AclInterface [interfaceId=" + interfaceId + ", lportTag=" + lportTag + ", dpId=" + dpId + ", elanId="
                + elanId + ", portSecurityEnabled=" + portSecurityEnabled + ", securityGroups=" + securityGroups
                + ", allowedAddressPairs=" + allowedAddressPairs + ", subnetInfo=" + subnetInfo
                + ", ingressRemoteAclTags=" + ingressRemoteAclTags + ", egressRemoteAclTags=" + egressRemoteAclTags
                + ", isMarkedForDelete=" + isMarkedForDelete + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(AclInterface from) {
        return new Builder(from);
    }

    public static final class Builder {
        private String interfaceId;
        private Integer lportTag;
        private BigInteger dpId;
        private Long elanId;
        private boolean portSecurityEnabled;
        private List<Uuid> securityGroups;
        private List<AllowedAddressPairs> allowedAddressPairs;
        private List<SubnetInfo> subnetInfo;
        private SortedSet<Integer> ingressRemoteAclTags;
        private SortedSet<Integer> egressRemoteAclTags;
        private boolean isMarkedForDelete;

        private Builder() {
        }

        private Builder(AclInterface from) {
            this.interfaceId = from.interfaceId;
            this.lportTag = from.lportTag;
            this.dpId = from.dpId;
            this.elanId = from.elanId;
            this.portSecurityEnabled = from.portSecurityEnabled;
            this.securityGroups = from.securityGroups;
            this.allowedAddressPairs = from.allowedAddressPairs;
            this.subnetInfo = from.subnetInfo;
            this.ingressRemoteAclTags = from.ingressRemoteAclTags;
            this.egressRemoteAclTags = from.egressRemoteAclTags;
            this.isMarkedForDelete = from.isMarkedForDelete;
        }

        public Builder portSecurityEnabled(boolean value) {
            this.portSecurityEnabled = value;
            return this;
        }

        public Builder interfaceId(String value) {
            this.interfaceId = value;
            return this;
        }

        public Builder lPortTag(Integer value) {
            this.lportTag = value;
            return this;
        }

        public Builder dpId(BigInteger value) {
            this.dpId = value;
            return this;
        }

        public Builder elanId(Long value) {
            this.elanId = value;
            return this;
        }

        public Builder securityGroups(List<Uuid> list) {
            this.securityGroups = list == null ? null : ImmutableList.copyOf(list);
            return this;
        }

        public Builder allowedAddressPairs(List<AllowedAddressPairs> list) {
            this.allowedAddressPairs = list == null ? null : ImmutableList.copyOf(list);
            return this;
        }

        public Builder subnetInfo(List<SubnetInfo> list) {
            this.subnetInfo = list == null ? null : ImmutableList.copyOf(list);
            return this;
        }

        public Builder ingressRemoteAclTags(SortedSet<Integer> list) {
            this.ingressRemoteAclTags = list == null ? null : ImmutableSortedSet.copyOf(list);
            return this;
        }

        public Builder egressRemoteAclTags(SortedSet<Integer> list) {
            this.egressRemoteAclTags = list == null ? null : ImmutableSortedSet.copyOf(list);
            return this;
        }

        public Builder isMarkedForDelete(boolean value) {
            this.isMarkedForDelete = value;
            return this;
        }

        public AclInterface build() {
            return new AclInterface(this);
        }
    }
}
