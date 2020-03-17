/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.serviceutils.upgrade.UpgradeState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SnatExternalRoutersListener extends AsyncDataTreeChangeListenerBase<Routers, SnatExternalRoutersListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SnatExternalRoutersListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IdManagerService idManager;
    private final CentralizedSwitchScheduler  centralizedSwitchScheduler;
    private final NatMode natMode;
    private final UpgradeState upgradeState;
    private final SnatServiceManager natServiceManager;

    @Inject
    public SnatExternalRoutersListener(final DataBroker dataBroker,
                                       final IdManagerService idManager,
                                       final CentralizedSwitchScheduler centralizedSwitchScheduler,
                                       final NatserviceConfig config,
                                       final SnatServiceManager natServiceManager,
                                       final UpgradeState upgradeState) {
        super(Routers.class, SnatExternalRoutersListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.idManager = idManager;
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
        this.upgradeState = upgradeState;
        this.natServiceManager = natServiceManager;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = NatMode.Conntrack;
        }
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        // This class handles ExternalRouters for Conntrack SNAT mode.
        // For Controller SNAT mode, its handled in ExternalRoutersListeners.java
        if (natMode == NatMode.Conntrack) {
            registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
            NatUtil.createGroupIdPool(idManager);
        }
    }

    @Override
    protected InstanceIdentifier<Routers> getWildCardPath() {
        return InstanceIdentifier.create(ExtRouters.class).child(Routers.class);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Routers> identifier, Routers routers) {
        String routerName = routers.getRouterName();
        if (upgradeState.isUpgradeInProgress()) {
            LOG.warn("add event for ext-router {}, but upgrade is in progress.", routerName);
            return;
        }

        LOG.info("add : external router event for {}", routerName);
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        NatUtil.createRouterIdsConfigDS(dataBroker, routerId, routerName);
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (bgpVpnUuid != null) {
            // Router associated to BGPVPN, ignoring it.
            return;
        }
        // Allocate Primary NAPTSwitch for this router
        centralizedSwitchScheduler.scheduleCentralizedSwitch(routers);
    }

    @Override
    protected void update(InstanceIdentifier<Routers> identifier, Routers original, Routers update) {
        String routerName = original.getRouterName();
        Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("update : external router event - Invalid routerId for routerName {}", routerName);
            return;
        }
        LOG.info("update :called for router {} with originalSNATStatus {} and updatedSNATStatus {}",
                routerName, original.isEnableSnat(), update.isEnableSnat());
        if (!upgradeState.isUpgradeInProgress()) {
            centralizedSwitchScheduler.updateCentralizedSwitch(original, update);
        }
        if (!Objects.equals(original.getSubnetIds(), update.getSubnetIds())
                || !Objects.equals(original.getExternalIps(), update.getExternalIps())) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                confTx -> natServiceManager.notify(confTx, update, original, null, null,
                            SnatServiceManager.Action.SNAT_ROUTER_UPDATE)), LOG,
                    "error handling external router update");
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Routers> identifier, Routers router) {
        if (identifier == null || router == null) {
            LOG.error("remove : returning without processing since ext-router is null");
            return;
        }

        LOG.info("remove : external router event for {}", router.getRouterName());
        centralizedSwitchScheduler.releaseCentralizedSwitch(router);
    }

    @Override
    protected SnatExternalRoutersListener getDataTreeChangeListener() {
        return SnatExternalRoutersListener.this;
    }
}
