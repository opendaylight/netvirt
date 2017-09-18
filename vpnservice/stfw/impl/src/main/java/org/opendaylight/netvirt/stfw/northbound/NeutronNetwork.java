/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.northbound;

import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3ExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtensionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NeutronNetwork {
    boolean external = false;
    String segmentationId = "1000";
    boolean adminState = true;
    boolean shared = false;
    String status = "ACTIVE";
    Uuid tenantId;
    String name;
    Uuid id;
    int index;
    Class<? extends NetworkTypeBase> netType = NetworkTypeVxlan.class;

    public NeutronNetwork(int index) {
        index = index;
        tenantId = RandomUtils.createUuid();
        id = RandomUtils.createUuid();
        name = "NETWORK_" + index;
    }

    public Network build() {
        NetworkL3ExtensionBuilder networkL3ExtensionBuilder = new NetworkL3ExtensionBuilder().setExternal(external);
        NetworkProviderExtensionBuilder networkProviderExtensionBuilder =
            new NetworkProviderExtensionBuilder().setSegmentationId(segmentationId);
        NetworkBuilder networkBuilder =
            new NetworkBuilder().setAdminStateUp(adminState).setShared(shared).setStatus(status).setTenantId(tenantId);
        networkProviderExtensionBuilder.setNetworkType(netType);
        networkBuilder.addAugmentation(NetworkL3Extension.class, networkL3ExtensionBuilder.build());
        networkBuilder.addAugmentation(NetworkProviderExtension.class, networkProviderExtensionBuilder.build());
        NetworkKey networkKey = new NetworkKey(id);
        networkBuilder.setName(name).setUuid(id).setKey(networkKey);
        return networkBuilder.build();
    }

    public InstanceIdentifier<Network> getNetworkIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(Networks.class).child(Network.class, new NetworkKey(id));
    }

    public Uuid getId() {
        return id;
    }

    public void setId(Uuid id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public String getSegmentationId() {
        return segmentationId;
    }

    public void setSegmentationId(String segmentationId) {
        this.segmentationId = segmentationId;
    }

    public boolean isAdminState() {
        return adminState;
    }

    public void setAdminState(boolean adminState) {
        this.adminState = adminState;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Uuid getTenantId() {
        return tenantId;
    }

    public void setTenantId(Uuid tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
