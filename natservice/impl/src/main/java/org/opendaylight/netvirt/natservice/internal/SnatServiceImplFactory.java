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
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.inject.AbstractLifecycle;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
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
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final IdManagerService idManager;
    private final NAPTSwitchSelector naptSwitchSelector;
    private final NatMode natMode;
    private final INeutronVpnManager nvpnManager;
    private final ExternalRoutersListener externalRouterListener;
    private final IElanService elanManager;
    private final IInterfaceManager interfaceManager;
    private final IVpnFootprintService vpnFootprintService;
    protected final IFibManager fibManager;
    private final JobCoordinator jobCoordinator;

    @Inject
    public SnatServiceImplFactory(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                                  final ItmRpcService itmManager,
                                  final OdlInterfaceRpcService odlInterfaceRpcService,
                                  final IdManagerService idManager,
                                  final NAPTSwitchSelector naptSwitchSelector,
                                  final NatserviceConfig config,
                                  final INeutronVpnManager nvpnManager,
                                  final ExternalRoutersListener externalRouterListener,
                                  final IElanService elanManager,
                                  final IInterfaceManager interfaceManager,
                                  final IVpnFootprintService vpnFootprintService,
                                  final IFibManager fibManager,
                                  final JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
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
        this.interfaceManager = interfaceManager;
        this.vpnFootprintService = vpnFootprintService;
        this.fibManager = fibManager;
        this.jobCoordinator = jobCoordinator;
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
            return new FlatVlanConntrackBasedSnatService(dataBroker, mdsalManager, itmManager, odlInterfaceRpcService,
                    idManager, naptSwitchSelector, interfaceManager, vpnFootprintService, fibManager);
        }
        return null;
    }

    public Ipv6ForwardingService createFlatVlanIpv6ServiceImpl() {
        return new Ipv6ForwardingService(dataBroker, mdsalManager, itmManager, odlInterfaceRpcService,
                idManager, naptSwitchSelector, interfaceManager, jobCoordinator);
    }

    public AbstractSnatService createVxlanGreSnatServiceImpl() {

        if (natMode == NatMode.Conntrack) {
            NatOverVxlanUtil.validateAndCreateVxlanVniPool(dataBroker, nvpnManager, idManager,
                    NatConstants.ODL_VNI_POOL_NAME);
            return new VxlanGreConntrackBasedSnatService(dataBroker, mdsalManager, itmManager, odlInterfaceRpcService,
                    idManager, naptSwitchSelector, externalRouterListener, elanManager, interfaceManager,
                    vpnFootprintService, fibManager);
        }
        return null;
    }


}
