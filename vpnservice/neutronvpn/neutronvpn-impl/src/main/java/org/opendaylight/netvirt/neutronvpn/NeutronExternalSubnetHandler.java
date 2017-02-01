/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronExternalSubnetHandler implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronSubnetChangeListener.class);
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;

    public NeutronExternalSubnetHandler(final NeutronvpnManager neutronvpnManager,
            final NeutronvpnNatManager neutronvpnNatMgr) {
        this.nvpnManager = neutronvpnManager;
        this.nvpnNatManager = neutronvpnNatMgr;
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void handleExternalSubnetAdded(Network network, Uuid subnetId, List<Uuid> routerIds,
            WriteTransaction writeConfigTxn) {
        Uuid networkId = network.getUuid();
        if (NeutronvpnUtils.getIsExternal(network) && NeutronvpnUtils.isFlatOrVlanNetwork(network)) {
            LOG.trace("Added external subnet {} part of external network {} will create NAT external subnet",
                    subnetId, networkId);
            nvpnManager.createVpnInstanceForSubnet(subnetId);
            nvpnNatManager.updateOrAddExternalSubnet(networkId, subnetId, routerIds, writeConfigTxn);
        }
    }

    public void handleExternalSubnetUpdated(Network network, Uuid subnetId, List<Uuid> routerIds,
            WriteTransaction writeConfigTxn) {
        Uuid networkId = network.getUuid();
        if (NeutronvpnUtils.getIsExternal(network) && NeutronvpnUtils.isFlatOrVlanNetwork(network)) {
            LOG.trace("Updated subnet {} part of external network {} will update NAT external subnet",
                    subnetId, networkId);
            nvpnNatManager.updateExternalSubnet(networkId, subnetId, routerIds, writeConfigTxn);
        }
    }

    public void handleExternalSubnetRemoved(Network network, Uuid subnetId, WriteTransaction writeConfigTxn) {
        Uuid networkId = network.getUuid();
        if (NeutronvpnUtils.getIsExternal(network) && NeutronvpnUtils.isFlatOrVlanNetwork(network)) {
            LOG.trace("Removed subnet {} part of external network {} will remove NAT external subnet",
                    subnetId, networkId);
            nvpnManager.removeVpnInstanceForSubnet(subnetId);
            nvpnNatManager.removeExternalSubnet(subnetId, writeConfigTxn);
        }
    }
}
