/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.federation.plugin;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class FederatedNetworkPair {

    public String consumerNetworkId;
    public String producerNetworkId;
    public String consumerSubnetId;
    public String producerSubnetId;
    public String consumerTenantId;
    public String producerTenantId;

    public FederatedNetworkPair(String localNetworkId, String remoteNetworkId, String localSubnetId,
            String remoteSubnetId, String localTenantId, String remoteTenantId) {
        this.consumerNetworkId = localNetworkId;
        this.producerNetworkId = remoteNetworkId;
        this.consumerSubnetId = localSubnetId;
        this.producerSubnetId = remoteSubnetId;
        this.consumerTenantId = localTenantId;
        this.producerTenantId = remoteTenantId;
    }

    public FederatedNetworkPair(String localNetworkId, String remoteNetworkId, Uuid localSubnetId, Uuid remoteSubnetId,
            Uuid localTenantId, Uuid remoteTenantId) {
        this.consumerNetworkId = localNetworkId;
        this.producerNetworkId = remoteNetworkId;
        this.consumerSubnetId = FederationPluginUtils.uuidToCleanStr(localSubnetId);
        this.producerSubnetId = FederationPluginUtils.uuidToCleanStr(remoteSubnetId);
        this.consumerTenantId = FederationPluginUtils.uuidToCleanStr(localTenantId);
        this.producerTenantId = FederationPluginUtils.uuidToCleanStr(remoteTenantId);
    }

    public FederatedNetworkPair(Uuid localNetworkId, Uuid remoteNetworkId, Uuid localSubnetId, Uuid remoteSubnetId,
            Uuid localTenantId, Uuid remoteTenantId) {
        this.consumerNetworkId = FederationPluginUtils.uuidToCleanStr(localNetworkId);
        this.producerNetworkId = FederationPluginUtils.uuidToCleanStr(remoteNetworkId);
        this.consumerSubnetId = FederationPluginUtils.uuidToCleanStr(localSubnetId);
        this.producerSubnetId = FederationPluginUtils.uuidToCleanStr(remoteSubnetId);
        this.consumerTenantId = FederationPluginUtils.uuidToCleanStr(localTenantId);
        this.producerTenantId = FederationPluginUtils.uuidToCleanStr(remoteTenantId);
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
    public int hashCode() {
        return this.toString().hashCode();
    }
}
