/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpn.router.association.rev190502.bgpvpn.router.associations.attributes.BgpvpnRouterAssociations;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpn.router.association.rev190502.bgpvpn.router.associations.attributes.bgpvpn.router.associations.BgpvpnRouterAssociation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Singleton
public class NeutronBgpvpnRouterAssociationChangeListener extends
        AsyncDataTreeChangeListenerBase<BgpvpnRouterAssociation, NeutronBgpvpnRouterAssociationChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronBgpvpnRouterAssociationChangeListener.class);

    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final IdManagerService idManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final NeutronBgpvpnUtils neutronBgpvpnUtils;

    @Inject
    public NeutronBgpvpnRouterAssociationChangeListener(final DataBroker dataBroker,
                                                        final NeutronvpnManager neutronvpnManager,
                                                        final IdManagerService idManager,
                                                        final NeutronvpnUtils neutronvpnUtils,
                                                        final NeutronBgpvpnUtils neutronBgpvpnUtils) {
        super(BgpvpnRouterAssociation.class, NeutronBgpvpnRouterAssociationChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = neutronvpnManager;
        this.idManager = idManager;
        this.neutronvpnUtils = neutronvpnUtils;
        this.neutronBgpvpnUtils = neutronBgpvpnUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<BgpvpnRouterAssociation> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(BgpvpnRouterAssociations.class)
                .child(BgpvpnRouterAssociation.class);
    }

    @Override
    protected NeutronBgpvpnRouterAssociationChangeListener getDataTreeChangeListener() {
        return NeutronBgpvpnRouterAssociationChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<BgpvpnRouterAssociation> identifier, BgpvpnRouterAssociation input) {
        LOG.trace("Adding Bgpvpn router association : key: {}, value={}", identifier, input);
        NeutronConstants.EVENT_LOGGER.info("BGPVPN router association add: AssociationID {}, RouterID {}, BgpvpnID {}",
                input.getUuid().getValue(), input.getRouterId().getValue(), input.getBgpvpnId().getValue());
        Uuid vpnId = input.getBgpvpnId();
        String vpnName = vpnId.getValue();
        Uuid routerId = input.getRouterId();
        NamedSimpleReentrantLock.AcquireResult isLockAcquired = null;
        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try {
            isLockAcquired = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS);
            if (!isLockAcquired.wasAcquired()) {
                LOG.error("Add router association: add association failed for vpn : {} and routerId: {} due to "
                        + "failure in acquiring lock", vpnName, routerId.getValue());
                return;
            }
            VpnInstance vpnInstance = neutronvpnUtils.getVpnInstance((DataBroker) vpnId, null);
            if (vpnInstance != null) {
                if (validateRouteInfo(routerId, vpnId)) {
                    nvpnManager.associateRouterToVpn(vpnId, routerId);
                }
            } else {
                neutronBgpvpnUtils.addUnProcessedRouter(vpnId, routerId);
            }
        } finally {
            if (isLockAcquired.wasAcquired()) {
                vpnLock.getLock(vpnName);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<BgpvpnRouterAssociation> identifier, BgpvpnRouterAssociation original,
                          BgpvpnRouterAssociation update) {
    }

    @Override
    protected void remove(InstanceIdentifier<BgpvpnRouterAssociation> identifier, BgpvpnRouterAssociation input) {
        LOG.trace("Removing Bgpvpn router association : key: {}, value={}", identifier, input);
        NeutronConstants.EVENT_LOGGER.info("BGPVPN router dissociation: AssociationID {}, RouterID {}, BgpvpnID {}",
                input.getUuid().getValue(), input.getRouterId().getValue(), input.getBgpvpnId().getValue());
        Uuid vpnId = input.getBgpvpnId();
        String vpnName = vpnId.getValue();
        Uuid routerId = input.getRouterId();
        NamedSimpleReentrantLock.AcquireResult isLockAcquired = null;
        NamedLocks<String> vpnLock = neutronBgpvpnUtils.getVpnLock();
        try {
            isLockAcquired = vpnLock.tryAcquire(vpnName, NeutronConstants.LOCK_WAIT_TIME, TimeUnit.SECONDS);
            if (!isLockAcquired.wasAcquired()) {
                LOG.error("Remove router association: remove association failed for vpn : {} and routerId: {} due"
                        + " to failure in acquiring lock", vpnName, routerId.getValue());
                return;
            }
            neutronBgpvpnUtils.removeUnProcessedRouter(vpnId, routerId);
            VpnInstance vpnInstance = neutronvpnUtils.getVpnInstance(vpnId, null);
            if (vpnInstance != null) {
                nvpnManager.dissociateRouterFromVpn(vpnId, routerId);
            }
        } finally {
            if (isLockAcquired.wasAcquired()) {
                vpnLock.getLock(vpnName);
            }
        }
    }

    private boolean validateRouteInfo(Uuid routerID, Uuid vpnId) {
        Uuid assocVPNId;
        if ((assocVPNId = neutronvpnUtils.getVpnForRouter(routerID, true)) != null) {
            LOG.warn("VPN router association to VPN {} failed due to router {} already associated to another VPN {}",
                    vpnId.getValue(), routerID.getValue(), assocVPNId.getValue());
            return false;
        }
        return true;
    }

}
