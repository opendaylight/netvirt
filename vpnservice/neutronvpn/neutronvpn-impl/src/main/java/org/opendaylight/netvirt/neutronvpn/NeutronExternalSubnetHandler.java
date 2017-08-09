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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeGre;
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
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public void handleExternalSubnetAdded(Network network, Uuid subnetId, List<Uuid> routerIds, DataBroker broker) {
        Uuid networkId = network.getUuid();
        int vers = NeutronvpnUtils.getIpVersionForNeutronSubnet(broker, subnetId);
        if (NeutronvpnUtils.getIsExternal(network) && (NeutronvpnUtils.isFlatOrVlanNetwork(network)
                || NeutronvpnUtils.isNetworkOfType(network, NetworkTypeGre.class))) {
            LOG.info("Added external subnet {} part of external network {} will create NAT external subnet",
                    subnetId.getValue(), networkId.getValue());
            nvpnManager.updateSubnetNode(subnetId, null/* routerId */, subnetId, null);
            if (vers != 6) {
                nvpnNatManager.updateOrAddExternalSubnet(networkId, subnetId, routerIds);
            }
            nvpnManager.createVpnInstanceForSubnet(subnetId);

            if (!NeutronvpnUtils.isNetworkOfType(network, NetworkTypeGre.class) || vers != 6) {
                return; /*if not IPv6 mplsOverGre so the below treatment is not needed */
            }
            /*add to all local subnets the (internet)vpnUuid of the external network belonging to the same router*/
            Uuid vpnUuid = NeutronvpnUtils.getVpnForNetwork(broker, networkId);
            if (vpnUuid == null) {
                return;
            }
            Subnetmap subnetmap = NeutronvpnUtils.getSubnetmap(broker, subnetId);
            Uuid routerUuid = subnetmap.getRouterId();
            List<Uuid> subnetUuids = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerUuid);
            if (subnetUuids == null || subnetUuids.size() < 1) {
                return;
            }
            /*then there are a few other subnet attached to its router*/
            for (Uuid uuid : subnetUuids) {
                if (uuid.equals(subnetmap.getId())) {
                    continue;
                }
                Subnetmap subRattached = NeutronvpnUtils.getSubnetmap(broker, uuid);
                if (NeutronvpnUtils.getIpVersionForNeutronSubnet(broker, uuid) != 6
                        && subRattached.getVpnId().equals(vpnUuid)) {
                    nvpnManager.updateSubnetNode(subRattached.getId(),
                        subRattached.getRouterId(), subRattached.getVpnId(), vpnUuid);
                }
            }
        }
    }

    public void handleExternalSubnetRemoved(Network network, Uuid subnetId, DataBroker broker) {
        Uuid networkId = network.getUuid();
        if (NeutronvpnUtils.getIsExternal(network) && NeutronvpnUtils.isFlatOrVlanNetwork(network)
                || NeutronvpnUtils.isNetworkOfType(network, NetworkTypeGre.class)) {
            LOG.info("Removed subnet {} part of external network {} will remove NAT external subnet",
                    subnetId.getValue(), networkId.getValue());
            nvpnManager.removeVpnInstanceForSubnet(subnetId);
            nvpnNatManager.removeExternalSubnet(subnetId);

            /* because nvpnManager.removeVpnInstanceForSubnet is call above
             * then we have to try to remove internetvpnuuid if it is existing*/
            Uuid vpnUuid = NeutronvpnUtils.getVpnForNetwork(broker, networkId);
            if (vpnUuid == null) {
                return;
            }
            Subnetmap subnetmap = NeutronvpnUtils.getSubnetmap(broker, subnetId);
            Uuid routerUuid = subnetmap.getRouterId();
            List<Uuid> subnetUuids = NeutronvpnUtils.getNeutronRouterSubnetIds(broker, routerUuid);
            if (subnetUuids == null || subnetUuids.size() < 1) {
                return;
            }
            /*then there are a few other subnet attached to its router*/
            for (Uuid uuid : subnetUuids) {
                if (uuid.equals(subnetmap.getId())) {
                    continue;
                }
                Subnetmap subRattached = NeutronvpnUtils.getSubnetmap(broker, uuid);
                if (subRattached.getInternetVpnId() != null && subRattached.getInternetVpnId()
                    .equals(vpnUuid)) {
                    nvpnManager.updateSubnetNode(subRattached.getId(),
                        subRattached.getRouterId(), subRattached.getVpnId(), null);
                }
            }
        }
    }
}
