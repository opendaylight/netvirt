/*
 * Copyright (c) 2017, 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.math.BigInteger;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.netvirt.natservice.internal.NatConstants;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CentralizedSwitchChangeListener detect changes in switch:router mapping and
 * update flows accordingly.
 */
@Singleton
public class SnatCentralizedSwitchChangeListener
        extends AsyncDataTreeChangeListenerBase<RouterToNaptSwitch, SnatCentralizedSwitchChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(SnatCentralizedSwitchChangeListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final SnatServiceManager snatServiceManger;
    private final NatDataUtil natDataUtil;
    private final DataTreeEventCallbackRegistrar eventCallbacks;

    @Inject
    public SnatCentralizedSwitchChangeListener(final DataBroker dataBroker,
            final SnatServiceManager snatServiceManger, NatDataUtil natDataUtil, final NatserviceConfig config,
            final DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar) {
        super(RouterToNaptSwitch.class, SnatCentralizedSwitchChangeListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.snatServiceManger = snatServiceManger;
        this.natDataUtil = natDataUtil;
        this.eventCallbacks = dataTreeEventCallbackRegistrar;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            LOG.info("NAT mode configured default as Controller as config is missing");
            this.natMode = NatMode.Controller;
        }
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<RouterToNaptSwitch> getWildCardPath() {
        return InstanceIdentifier.create(NaptSwitches.class).child(RouterToNaptSwitch.class);
    }

    @Override
    protected void remove(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Deleting {}", routerToNaptSwitch);
        if (natMode == NatMode.Controller) {
            LOG.info("Not Processing this remove() event for NAT Mode configured in Controller Mode");
            return;
        }
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        Routers router = natDataUtil.getRouter(routerToNaptSwitch.getRouterName());
        if (router != null) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                confTx -> snatServiceManger.notify(confTx, router, null, primarySwitchId, null,
                    SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL)), LOG,
                "error handling SNAT centralized switch removal");
            natDataUtil.removeFromRouterMap(router);
        }
    }

    @Override
    protected void update(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch origRouterToNaptSwitch,
            RouterToNaptSwitch updatedRouterToNaptSwitch) {
        LOG.debug("Updating old {} new {}", origRouterToNaptSwitch, updatedRouterToNaptSwitch);
        if (natMode == NatMode.Controller) {
            LOG.info("Not Processing this update() event for NAT Mode configured in Controller Mode");
            return;
        }
        BigInteger origPrimarySwitchId = origRouterToNaptSwitch.getPrimarySwitchId();
        BigInteger updatedPrimarySwitchId = updatedRouterToNaptSwitch.getPrimarySwitchId();
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, confTx -> {
            Routers origRouter = NatUtil.getRoutersFromConfigDS(confTx, origRouterToNaptSwitch.getRouterName());
            Routers updatedRouter = NatUtil.getRoutersFromConfigDS(confTx, updatedRouterToNaptSwitch.getRouterName());
            if (origPrimarySwitchId != updatedPrimarySwitchId) {
                if (origRouter != null) {
                    snatServiceManger.notify(confTx, origRouter, null, origPrimarySwitchId, null,
                            SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_DISBL);
                    if (origRouterToNaptSwitch.isEnableSnat()) {
                        snatServiceManger.notify(confTx, origRouter, null, origPrimarySwitchId, null,
                                SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
                    }
                    natDataUtil.removeFromRouterMap(origRouter);
                }
                if (updatedRouter != null) {
                    natDataUtil.updateRouterMap(updatedRouter);
                    snatServiceManger.notify(confTx, updatedRouter, null, updatedPrimarySwitchId, null,
                            SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_ENBL);
                    if (updatedRouterToNaptSwitch.isEnableSnat()) {
                        snatServiceManger.notify(confTx, updatedRouter, null, updatedPrimarySwitchId, null,
                                SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
                    }
                }
            } else {
                boolean origIsSnatEnabled = false;
                boolean updatedIsSnatEnabled = false;
                if (origRouterToNaptSwitch.isEnableSnat() != null) {
                    origIsSnatEnabled = origRouterToNaptSwitch.isEnableSnat();
                }
                if (updatedRouterToNaptSwitch.isEnableSnat() != null) {
                    updatedIsSnatEnabled = updatedRouterToNaptSwitch.isEnableSnat();
                }
                if (origIsSnatEnabled != updatedIsSnatEnabled) {
                    if (updatedRouterToNaptSwitch.isEnableSnat()) {
                        snatServiceManger.notify(confTx, updatedRouter, null, updatedPrimarySwitchId, null,
                                SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
                    } else {
                        snatServiceManger.notify(confTx, origRouter, null, origPrimarySwitchId, null,
                                SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
                    }
                }
            }
        }), LOG, "Error handling SNAT centralized switch update");
    }

    @Override
    protected void add(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Adding {}", routerToNaptSwitch);
        if (natMode == NatMode.Controller) {
            LOG.info("Not Processing this add() event for NAT Mode configured in Controller Mode");
            return;
        }
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        String routerName = routerToNaptSwitch.getRouterName();
        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
        final boolean isEnableSnat;
        if (routerToNaptSwitch.isEnableSnat() != null) {
            isEnableSnat = routerToNaptSwitch.isEnableSnat();
        } else {
            isEnableSnat = false;
        }
        long vpnId = NatUtil.getVpnId(dataBroker, routerName);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.warn("VpnId not unavailable for router {} yet", routerName);
            eventCallbacks.onAddOrUpdate(LogicalDatastoreType.CONFIGURATION,
                NatUtil.getVpnInstanceToVpnIdIdentifier(routerName), (unused, newVpnId) -> {
                    ListenableFutures.addErrorLogging(
                        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                            innerConfTx -> handleAdd(innerConfTx, routerName, router, primarySwitchId,
                                    isEnableSnat)), LOG,
                        "Error handling router addition");
                    return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                }, Duration.ofSeconds(5), iid -> LOG.error("VpnId not found for router {}", routerName));
            return;
        }
        ListenableFutures.addErrorLogging(
            txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION,
                confTx -> handleAdd(confTx, routerName, router, primarySwitchId,
                        isEnableSnat)), LOG, "Error handling router addition");
    }

    private void handleAdd(TypedReadWriteTransaction<Datastore.Configuration> confTx,
            String routerName, Routers router, BigInteger primarySwitchId, boolean isSnatEnabled)
            throws ExecutionException, InterruptedException {
        if (router != null) {
            natDataUtil.addtoRouterMap(router);
            snatServiceManger.notify(confTx, router, null, primarySwitchId, null,
                    SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_ENBL);
            if (isSnatEnabled) {
                snatServiceManger.notify(confTx, router, null, primarySwitchId, null,
                        SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
            }
        } else {
            LOG.error("Router {} not found for primarySwitch {}", routerName, primarySwitchId);
        }
    }

    @Override
    protected SnatCentralizedSwitchChangeListener getDataTreeChangeListener() {
        return this;
    }
}
