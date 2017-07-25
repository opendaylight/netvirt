/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev170725.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetVpnAssociationManager
        extends AsyncClusteredDataTreeChangeListenerBase<Subnetmap, SubnetVpnAssociationManager> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetVpnAssociationManager.class);

    private final DataBroker dataBroker;
    private final FederationPluginMgr pluginMgr;
    private final Map<String, String> subnetVpnMap = Maps.newConcurrentMap();

    @Inject
    public SubnetVpnAssociationManager(final DataBroker dataBroker, final FederationPluginMgr pluginMgr) {
        this.dataBroker = dataBroker;
        this.pluginMgr = pluginMgr;
    }

    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    public String getSubnetVpn(String subnetId) {
        return subnetVpnMap.get(subnetId);
    }

    @Override
    protected InstanceIdentifier<Subnetmap> getWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        String subnetName = subnetmap.getId().getValue();
        subnetVpnMap.remove(subnetName);
        LOG.debug("Subnet {} removed", subnetName);

    }

    @Override
    protected void update(InstanceIdentifier<Subnetmap> identifier, Subnetmap origSubnetmap,
            Subnetmap updatedSubnetmap) {
        Uuid subnetId = updatedSubnetmap.getId();
        Uuid origVpnId = origSubnetmap.getVpnId();
        Uuid updatedVpnId = updatedSubnetmap.getVpnId();
        String subnetName = subnetId.getValue();
        String vpnName = null;
        if (origVpnId == null && updatedVpnId != null) {
            vpnName = updatedVpnId.getValue();
            subnetVpnMap.put(subnetName, vpnName);
            LOG.debug("Add subnet {} <-> vpn {} association", subnetName, vpnName);

        } else if (origVpnId != null && updatedVpnId == null) {
            vpnName = origVpnId.getValue();
            subnetVpnMap.remove(subnetName);
            LOG.debug("Remove subnet {} <-> vpn {} association", subnetName, vpnName);

        } else if (origVpnId != null && updatedVpnId != null && !Objects.equal(origVpnId, updatedVpnId)) {
            vpnName = updatedVpnId.getValue();
            subnetVpnMap.put(subnetName, vpnName);
            LOG.debug("Update subnet {} <-> vpn {} association", subnetName, vpnName);
        }

        if (vpnName != null) {
            updateSubnetVpnAssociation(subnetName, vpnName);
        }
    }

    @Override
    protected void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        if (subnetmap.getVpnId() != null) {
            String subnetName = subnetmap.getId().getValue();
            String vpnName = subnetmap.getVpnId().getValue();
            subnetVpnMap.put(subnetName, vpnName);
            LOG.debug("Add subnet {} <-> vpn {} association", subnetName, vpnName);
            updateSubnetVpnAssociation(subnetName, vpnName);
        }
    }

    @Override
    protected SubnetVpnAssociationManager getDataTreeChangeListener() {
        return this;
    }

    private void updateSubnetVpnAssociation(String subnetName, String vpnName) {
        LOG.debug("Updating {} ingress plugins on subnet vpn association for subnet {} and vpn {}",
                pluginMgr.getIngressPlugins().size(), subnetName, vpnName);
        pluginMgr.getIngressPlugins().values()
                .forEach((plugin) -> plugin.subnetVpnAssociationUpdated(subnetName, vpnName));
    }

}
