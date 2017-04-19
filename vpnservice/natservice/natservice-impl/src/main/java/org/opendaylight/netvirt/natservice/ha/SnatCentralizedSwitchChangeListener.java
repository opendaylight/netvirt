/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
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

    @Inject
    public SnatCentralizedSwitchChangeListener(final DataBroker dataBroker,
            final SnatServiceManager snatServiceManger, NatDataUtil natDataUtil) {
        super(RouterToNaptSwitch.class, SnatCentralizedSwitchChangeListener.class);
        this.dataBroker = dataBroker;
        this.snatServiceManger = snatServiceManger;
        this.natDataUtil = natDataUtil;
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
        LOG.debug("Deleting ", routerToNaptSwitch);
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        Routers router =   natDataUtil.getRouter(routerToNaptSwitch.getRouterName());
        snatServiceManger.notify(router, primarySwitchId, null, SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
        natDataUtil.removeFromRouterMap(router);
    }

    @Override
    protected void update(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch origRouterToNaptSwitch,
            RouterToNaptSwitch updatedRouterToNaptSwitch) {
        LOG.debug("Updating old {} new {}", origRouterToNaptSwitch, updatedRouterToNaptSwitch);
        BigInteger primarySwitchId = origRouterToNaptSwitch.getPrimarySwitchId();
        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, origRouterToNaptSwitch.getRouterName());
        natDataUtil.updateRouterMap(router);
        snatServiceManger.notify(router, primarySwitchId, null, SnatServiceManager.Action.SNAT_ALL_SWITCH_DISBL);
    }

    @Override
    protected void add(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Adding ", routerToNaptSwitch);
        BigInteger primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerToNaptSwitch.getRouterName());
        natDataUtil.addtoRouterMap(router);
        snatServiceManger.notify(router, primarySwitchId, null, SnatServiceManager.Action.SNAT_ALL_SWITCH_ENBL);
    }

    @Override
    protected SnatCentralizedSwitchChangeListener getDataTreeChangeListener() {
        return this;
    }
}
