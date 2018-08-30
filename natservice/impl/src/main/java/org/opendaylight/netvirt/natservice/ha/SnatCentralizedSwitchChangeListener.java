/*
 * Copyright (c) 2017, 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;

import java.time.Duration;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.netvirt.natservice.api.SnatServiceManager;
import org.opendaylight.netvirt.natservice.internal.NatConstants;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
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
    private final SnatServiceManager snatServiceManger;
    private final NatDataUtil natDataUtil;
    private final DataTreeEventCallbackRegistrar eventCallbacks;

    @Inject
    public SnatCentralizedSwitchChangeListener(final DataBroker dataBroker,
            final SnatServiceManager snatServiceManger, NatDataUtil natDataUtil,
            final DataTreeEventCallbackRegistrar dataTreeEventCallbackRegistrar) {
        super(RouterToNaptSwitch.class, SnatCentralizedSwitchChangeListener.class);
        this.dataBroker = dataBroker;
        this.snatServiceManger = snatServiceManger;
        this.natDataUtil = natDataUtil;
        this.eventCallbacks = dataTreeEventCallbackRegistrar;
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
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        Routers router = natDataUtil.getRouter(routerToNaptSwitch.getRouterName());
        if (router != null) {
            snatServiceManger.notify(router, null, primarySwitchId, null,
                    SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_DISBL);
            if (routerToNaptSwitch.isEnableSnat()) {
                snatServiceManger.notify(router, null, primarySwitchId, null,
                        SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
            }
            natDataUtil.removeFromRouterMap(router);
        }
    }

    @Override
    protected void update(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch origRouterToNaptSwitch,
            RouterToNaptSwitch updatedRouterToNaptSwitch) {
        LOG.debug("Updating old {} new {}", origRouterToNaptSwitch, updatedRouterToNaptSwitch);
        BigInteger origPrimarySwitchId = origRouterToNaptSwitch.getPrimarySwitchId();
        Routers origRouter = NatUtil.getRoutersFromConfigDS(dataBroker, origRouterToNaptSwitch.getRouterName());
        BigInteger updatedPrimarySwitchId = updatedRouterToNaptSwitch.getPrimarySwitchId();
        Routers updatedRouter = NatUtil.getRoutersFromConfigDS(dataBroker, updatedRouterToNaptSwitch.getRouterName());
        if (origPrimarySwitchId != updatedPrimarySwitchId) {
            if (origRouter != null) {
                snatServiceManger.notify(origRouter, null, origPrimarySwitchId, null,
                        SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_DISBL);
                if (origRouterToNaptSwitch.isEnableSnat()) {
                    snatServiceManger.notify(origRouter, null, origPrimarySwitchId, null,
                            SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
                }
                natDataUtil.removeFromRouterMap(origRouter);
            }
            if (updatedRouter != null) {
                natDataUtil.updateRouterMap(updatedRouter);
                snatServiceManger.notify(updatedRouter, null, updatedPrimarySwitchId, null,
                        SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_ENBL);
                if (updatedRouterToNaptSwitch.isEnableSnat()) {
                    snatServiceManger.notify(updatedRouter, null, updatedPrimarySwitchId, null,
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
                    snatServiceManger.notify(updatedRouter, null, updatedPrimarySwitchId, null,
                            SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
                } else {
                    snatServiceManger.notify(origRouter, null, origPrimarySwitchId, null,
                            SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
                }
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Adding {}", routerToNaptSwitch);
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        String routerName = routerToNaptSwitch.getRouterName();
        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
        long vpnId = NatUtil.getVpnId(dataBroker, routerName);
        final boolean isEnableSnat;
        if (routerToNaptSwitch.isEnableSnat() != null) {
            isEnableSnat = routerToNaptSwitch.isEnableSnat();
        } else {
            isEnableSnat = false;
        }
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.warn("VpnId not unavailable for router {} yet", routerName);
            eventCallbacks.onAddOrUpdate(LogicalDatastoreType.CONFIGURATION,
                NatUtil.getVpnInstanceToVpnIdIdentifier(routerName), (unused, newVpnId) -> {
                    handleAdd(routerName, router, primarySwitchId,isEnableSnat);
                    return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                }, Duration.ofSeconds(5), iid -> {
                    LOG.error("VpnId not found for router {}", routerName);
                });
            return;
        }
        handleAdd(routerName, router, primarySwitchId, isEnableSnat);
    }

    private void handleAdd(String routerName, Routers router, BigInteger primarySwitchId, boolean isSnatEnabled) {
        if (router != null) {
            natDataUtil.addtoRouterMap(router);
            snatServiceManger.notify(router, null, primarySwitchId, null,
                    SnatServiceManager.Action.CNT_ROUTER_ALL_SWITCH_ENBL);
            if (isSnatEnabled) {
                snatServiceManger.notify(router, null, primarySwitchId, null,
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
