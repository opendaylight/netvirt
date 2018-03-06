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
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
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
    private final NAPTSwitchSelector naptSwitchSelector;
    private final NatMode natMode;
    private final INeutronVpnManager nvpnManager;
    private final ExternalRoutersListener externalRouterListener;
    private final IElanService elanManager;
    private final IVpnFootprintService vpnFootprintService;

    @Inject
    public SnatServiceImplFactory(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
            final ItmRpcService itmManager,
            final OdlInterfaceRpcService interfaceManager,
            final IdManagerService idManager,
            final NAPTSwitchSelector naptSwitchSelector,
            final NatserviceConfig config,
            final INeutronVpnManager nvpnManager,
            final ExternalRoutersListener externalRouterListener,
            final IElanService elanManager,
            final IVpnFootprintService vpnFootprintService) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.interfaceManager = interfaceManager;
        this.idManager = idManager;
        this.naptSwitchSelector = naptSwitchSelector;
        if (config != null) {
            this.natMode = config.getNatMode();
        } else {
            this.natMode = null;
        }
        this.nvpnManager = nvpnManager;
        this.externalRouterListener = externalRouterListener;
        this.elanManager = elanManager;
        this.vpnFootprintService = vpnFootprintService;
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
                    idManager, naptSwitchSelector, vpnFootprintService);
        }
        return null;
    }

    public AbstractSnatService createVxlanGreSnatServiceImpl() {

        if (natMode == NatMode.Conntrack) {
            NatOverVxlanUtil.validateAndCreateVxlanVniPool(dataBroker, nvpnManager, idManager,
                    NatConstants.ODL_VNI_POOL_NAME);
            return new VxlanGreConntrackBasedSnatService(dataBroker, mdsalManager, itmManager, interfaceManager,
                    idManager, naptSwitchSelector, externalRouterListener, elanManager, vpnFootprintService);
        }
        return null;
    }


}
