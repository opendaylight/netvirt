/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import static org.opendaylight.netvirt.federation.plugin.FederationPluginUtils.uuidToCleanStr;

import java.util.Objects;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class FederatedNetworkPair {

    private final String consumerNetworkId;
    private final String producerNetworkId;
    private final String consumerSubnetId;
    private final String producerSubnetId;
    private final String consumerTenantId;
    private final String producerTenantId;

    public FederatedNetworkPair(String localNetworkId, String remoteNetworkId, String localSubnetId,
            String remoteSubnetId, String localTenantId, String remoteTenantId) {
        this.consumerNetworkId = Objects.requireNonNull(localNetworkId, "localNetworkId");
        this.producerNetworkId = Objects.requireNonNull(remoteNetworkId, "remoteNetworkId");
        this.consumerSubnetId = Objects.requireNonNull(localSubnetId, "localSubnetId");
        this.producerSubnetId = Objects.requireNonNull(remoteSubnetId, "remoteSubnetId");
        this.consumerTenantId = Objects.requireNonNull(localTenantId, "localTenantId");
        this.producerTenantId = Objects.requireNonNull(remoteTenantId, "remoteTenantId");
    }

    public FederatedNetworkPair(String localNetworkId, String remoteNetworkId, Uuid localSubnetId, Uuid remoteSubnetId,
            Uuid localTenantId, Uuid remoteTenantId) {
        this.consumerNetworkId = Objects.requireNonNull(localNetworkId, "localNetworkId");
        this.producerNetworkId = Objects.requireNonNull(remoteNetworkId, "remoteNetworkId");
        this.consumerSubnetId = uuidToCleanStr(Objects.requireNonNull(localSubnetId, "localSubnetId"));
        this.producerSubnetId = uuidToCleanStr(Objects.requireNonNull(remoteSubnetId, "remoteSubnetId"));
        this.consumerTenantId = uuidToCleanStr(Objects.requireNonNull(localTenantId, "localTenantId"));
        this.producerTenantId = uuidToCleanStr(Objects.requireNonNull(remoteTenantId, "remoteTenantId"));
    }

    public FederatedNetworkPair(Uuid localNetworkId, Uuid remoteNetworkId, Uuid localSubnetId, Uuid remoteSubnetId,
            Uuid localTenantId, Uuid remoteTenantId) {
        this.consumerNetworkId = uuidToCleanStr(Objects.requireNonNull(localNetworkId, "localNetworkId"));
        this.producerNetworkId = uuidToCleanStr(Objects.requireNonNull(remoteNetworkId, "remoteNetworkId"));
        this.consumerSubnetId = uuidToCleanStr(Objects.requireNonNull(localSubnetId, "localSubnetId"));
        this.producerSubnetId = uuidToCleanStr(Objects.requireNonNull(remoteSubnetId, "remoteSubnetId"));
        this.consumerTenantId = uuidToCleanStr(Objects.requireNonNull(localTenantId, "localTenantId"));
        this.producerTenantId = uuidToCleanStr(Objects.requireNonNull(remoteTenantId, "remoteTenantId"));
    }

    public String getConsumerNetworkId() {
        return consumerNetworkId;
    }

    public String getProducerNetworkId() {
        return producerNetworkId;
    }

    public String getConsumerSubnetId() {
        return consumerSubnetId;
    }

    public String getProducerSubnetId() {
        return producerSubnetId;
    }

    public String getConsumerTenantId() {
        return consumerTenantId;
    }

    public String getProducerTenantId() {
        return producerTenantId;
    }

    @Override
    public String toString() {
        return "FederatedNetworkPair " + "[consumerNetworkId=" + consumerNetworkId + ", producerNetworkId="
                + producerNetworkId + "]" + "[consumerSubnetId=" + consumerSubnetId + ", producerSubnetId="
                + producerSubnetId + "]" + "[consumerTenantId=" + consumerTenantId + ", producerTenantId="
                + producerTenantId + "]";
    }

    public boolean equals(FederatedNetworkPair other) {
        if (!consumerNetworkId.equals(other.consumerNetworkId)) {
            return false;
        }
        if (!producerNetworkId.equals(other.producerNetworkId)) {
            return false;
        }
        if (!consumerSubnetId.equals(other.consumerSubnetId)) {
            return false;
        }
        if (!producerSubnetId.equals(other.producerSubnetId)) {
            return false;
        }
        if (!consumerTenantId.equals(other.consumerTenantId)) {
            return false;
        }
        if (!producerTenantId.equals(other.producerTenantId)) {
            return false;
        }
        return true;
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
        FederatedNetworkPair other = (FederatedNetworkPair) obj;
        return equals(other);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (consumerNetworkId == null ? 0 : consumerNetworkId.hashCode());
        result = prime * result + (consumerSubnetId == null ? 0 : consumerSubnetId.hashCode());
        result = prime * result + (consumerTenantId == null ? 0 : consumerTenantId.hashCode());
        result = prime * result + (producerNetworkId == null ? 0 : producerNetworkId.hashCode());
        result = prime * result + (producerSubnetId == null ? 0 : producerSubnetId.hashCode());
        result = prime * result + (producerTenantId == null ? 0 : producerTenantId.hashCode());
        return result;
    }

}
