/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
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
            snatServiceManger.notify(router, primarySwitchId, null, SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
            natDataUtil.removeFromRouterMap(router);
        }
    }

    @Override
    protected void update(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch origRouterToNaptSwitch,
            RouterToNaptSwitch updatedRouterToNaptSwitch) {
        LOG.debug("Updating old {} new {}", origRouterToNaptSwitch, updatedRouterToNaptSwitch);
        BigInteger origPrimarySwitchId = origRouterToNaptSwitch.getPrimarySwitchId();
        Routers origRouter = NatUtil.getRoutersFromConfigDS(dataBroker, origRouterToNaptSwitch.getRouterName());
        if (origRouter != null) {
            snatServiceManger.notify(origRouter, origPrimarySwitchId, null,
                    SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
            natDataUtil.removeFromRouterMap(origRouter);
        }
        BigInteger updatedPrimarySwitchId = updatedRouterToNaptSwitch.getPrimarySwitchId();
        Routers updatedRouter = NatUtil.getRoutersFromConfigDS(dataBroker, updatedRouterToNaptSwitch.getRouterName());
        if (updatedRouter != null) {
            natDataUtil.updateRouterMap(updatedRouter);
            snatServiceManger.notify(updatedRouter, updatedPrimarySwitchId, null,
                    SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
        }
    }

    @Override
    protected void add(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Adding {}", routerToNaptSwitch);
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        String routerName = routerToNaptSwitch.getRouterName();
        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
        if (router == null) {
            LOG.warn("No router found for NaptSwitch {} for router {}", primarySwitchId, routerName);
            eventCallbacks.onAddOrUpdate(LogicalDatastoreType.CONFIGURATION,
                NatUtil.buildRouterIdentifier(routerName), (unused, newRouter) -> {
                    handleAdd(newRouter, primarySwitchId);
                    return DataTreeEventCallbackRegistrar.NextAction.UNREGISTER;
                }, Duration.ofSeconds(5), iid -> {
                    LOG.error("Router {} not found for primarySwitch {}", routerName, primarySwitchId);
                });
            return;

        }
        if (router != null) {
            handleAdd(router, primarySwitchId);
        }
    }

    private void handleAdd(Routers router, BigInteger primarySwitchId) {
        natDataUtil.addtoRouterMap(router);
        snatServiceManger.notify(router, primarySwitchId, null, SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
    }

    @Override
    protected SnatCentralizedSwitchChangeListener getDataTreeChangeListener() {
        return this;
    }
}
