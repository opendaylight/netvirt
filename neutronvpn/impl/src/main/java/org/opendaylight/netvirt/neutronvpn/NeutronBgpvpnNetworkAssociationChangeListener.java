/*
 * Copyright (c) 2020 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.AcquireResult;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpn.network.association.rev190502.bgpvpn.network.associations.attributes.BgpvpnNetworkAssociations;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpn.network.association.rev190502.bgpvpn.network.associations.attributes.bgpvpn.network.associations.BgpvpnNetworkAssociation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronBgpvpnNetworkAssociationChangeListener
        extends AbstractAsyncDataTreeChangeListener<BgpvpnNetworkAssociation> {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnNetworkAssociationChangeListener.class);

    private final NeutronvpnManager nvpnManager;
    private final IdManagerService idManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final NeutronBgpvpnUtils neutronBgpvpnUtils;

    @Inject
    public NeutronBgpvpnNetworkAssociationChangeListener(final DataBroker dataBroker,
            final NeutronvpnManager neutronvpnManager, final IdManagerService idManager,
            final NeutronvpnUtils neutronvpnUtils, final NeutronBgpvpnUtils neutronBgpvpnUtils) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Neutron.class).child(BgpvpnNetworkAssociations.class)
                        .child(BgpvpnNetworkAssociation.class),
                Executors.newSingleThreadExecutor("NeutronBgpvpnNetworkAssociationChangeListener", LOG));

        this.nvpnManager = neutronvpnManager;
        this.idManager = idManager;
        this.neutronvpnUtils = neutronvpnUtils;
        this.neutronBgpvpnUtils = neutronBgpvpnUtils;
        init();
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void add(InstanceIdentifier<BgpvpnNetworkAssociation> identifier, BgpvpnNetworkAssociation input) {
        LOG.trace("Adding Bgpvpn network association : key: {}, value={}", identifier, input);
        Uuid vpnId = input.getBgpvpnId();
        String vpnName = vpnId.getValue();
        Uuid networkId = input.getNetworkId();
        List<Uuid> networks = new ArrayList<>();
        networks.add(networkId);

        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try (AcquireResult lock = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
            if (!lock.wasAcquired()) {
                LOG.error("Add network association: add association failed for vpn : {} and networkId: {} due to "
                        + "failure in acquiring lock", vpnName, networkId.getValue());
                return;
            }
            VpnInstance vpnInstance = neutronvpnUtils.getVpnInstance(vpnId);
            if (vpnInstance != null) {
                List<String> errorMessages = nvpnManager.associateNetworksToVpn(vpnId, networks);
                if (!errorMessages.isEmpty()) {
                    LOG.error("BgpvpnNetworkAssociation add: associate network id {} to vpn {} failed due to {}",
                            networkId.getValue(), vpnId.getValue(), errorMessages);
                }
            } else {
                neutronBgpvpnUtils.addUnProcessedNetwork(vpnId, networkId);
            }
        }
    }

    @Override
    public void update(InstanceIdentifier<BgpvpnNetworkAssociation> identifier, BgpvpnNetworkAssociation original,
            BgpvpnNetworkAssociation update) {

    }

    @Override
    public void remove(InstanceIdentifier<BgpvpnNetworkAssociation> identifier, BgpvpnNetworkAssociation input) {
        LOG.trace("Removing Bgpvpn network association : key: {}, value={}", identifier, input);

        Uuid vpnId = input.getBgpvpnId();
        String vpnName = vpnId.getValue();
        Uuid networkId = input.getNetworkId();
        List<Uuid> networks = new ArrayList<>();
        networks.add(networkId);

        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try (AcquireResult lock = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
            if (!lock.wasAcquired()) {
                LOG.error("Remove network association: remove association failed for vpn : {} and networkId: {} due "
                        + "to failure in acquiring lock", vpnName, networkId.getValue());
                return;
            }
            neutronBgpvpnUtils.removeUnProcessedNetwork(vpnId, networkId);
            VpnInstance vpnInstance = neutronvpnUtils.getVpnInstance(vpnId);
            if (vpnInstance != null) {
                List<String> errorMessages = nvpnManager.dissociateNetworksFromVpn(vpnId, networks);
                if (!errorMessages.isEmpty()) {
                    LOG.error("BgpvpnNetworkAssociation remove: dissociate network id {} to vpn {} failed due to {}",
                            networkId.getValue(), vpnName, errorMessages);
                }
            }
        }
    }
}
