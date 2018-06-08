/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.List;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronExternalSubnetHandler implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronExternalSubnetHandler.class);
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;

    @Inject
    public NeutronExternalSubnetHandler(final NeutronvpnManager neutronvpnManager,
            final NeutronvpnNatManager neutronvpnNatMgr) {
        this.nvpnManager = neutronvpnManager;
        this.nvpnNatManager = neutronvpnNatMgr;
    }

    @Override
    @PreDestroy
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void handleExternalSubnetAdded(Network network, Uuid subnetId, List<Uuid> routerIds) {
        Uuid networkId = network.getUuid();
        if (NeutronvpnUtils.getIsExternal(network) && NeutronvpnUtils.isFlatOrVlanNetwork(network)) {
            LOG.info("Added external subnet {} part of external network {} will create NAT external subnet",
                    subnetId.getValue(), networkId.getValue());
            nvpnNatManager.updateOrAddExternalSubnet(networkId, subnetId, routerIds);
            nvpnManager.updateSubnetNode(subnetId, null/* routerId */, subnetId, null /* internet-vpn-id */);
            nvpnManager.createVpnInstanceForSubnet(subnetId);
        }
    }

    public void handleExternalSubnetRemoved(Network network, Uuid subnetId) {
        Uuid networkId = network.getUuid();
        if (NeutronvpnUtils.getIsExternal(network) && NeutronvpnUtils.isFlatOrVlanNetwork(network)) {
            LOG.info("Removed subnet {} part of external network {} will remove NAT external subnet",
                    subnetId.getValue(), networkId.getValue());
            nvpnManager.removeVpnInstanceForSubnet(subnetId);
            nvpnNatManager.removeExternalSubnet(networkId, subnetId);
        }
    }
}
