/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SnatServiceImplFactory extends AbstractLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SnatServiceImplFactory.class);

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final ItmRpcService itmManager;
    private final OdlInterfaceRpcService interfaceManager;
    private final IdManagerService idManager;
    private final NaptManager naptManager;
    private final NAPTSwitchSelector naptSwitchSelector;
    private NatMode natMode;
    private final IVpnManager vpnManager;
    private final ExternalRoutersListener externalRouterListener;
    private final IElanService elanManager;

    @Inject
    public SnatServiceImplFactory(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
            final ItmRpcService itmManager,
            final OdlInterfaceRpcService interfaceManager,
            final IdManagerService idManager,
            final NaptManager naptManager,
            final NAPTSwitchSelector naptSwitchSelector,
            final IVpnManager vpnManager,
            final NatserviceConfig config,
            final ExternalRoutersListener externalRouterListener,
            final IElanService elanManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.interfaceManager = interfaceManager;
        this.idManager = idManager;
        this.naptManager = naptManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.vpnManager = vpnManager;
        this.externalRouterListener = externalRouterListener;
        this.elanManager = elanManager;
        if (config != null) {
            this.natMode = config.getNatMode();
        }
    }

    @Override
    protected void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    protected void stop() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public AbstractSnatService createFlatVlanSnatServiceImpl() {

        if (natMode == NatMode.Conntrack) {
            return new FlatVlanConntrackBasedSnatService(dataBroker, mdsalManager, itmManager, interfaceManager,
                    idManager, naptManager, naptSwitchSelector, vpnManager);
        }
        return null;
    }

    public AbstractSnatService createVxlanGreSnatServiceImpl() {

        if (natMode == NatMode.Conntrack) {
            return new VxlanGreConntrackBasedSnatService(dataBroker, mdsalManager, itmManager, interfaceManager,
                    idManager, naptManager, naptSwitchSelector, vpnManager, externalRouterListener, elanManager);
        }
        return null;
    }


}
