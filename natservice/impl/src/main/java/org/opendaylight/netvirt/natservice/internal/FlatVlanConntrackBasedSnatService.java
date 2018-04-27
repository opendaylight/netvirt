/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.natservice.ha.NatDataUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlatVlanConntrackBasedSnatService extends ConntrackBasedSnatService {

    private static final Logger LOG = LoggerFactory.getLogger(FlatVlanConntrackBasedSnatService.class);

    public FlatVlanConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                             ItmRpcService itmManager, OdlInterfaceRpcService odlInterfaceRpcService,
                                             IdManagerService idManager, NAPTSwitchSelector naptSwitchSelector,
                                             IInterfaceManager interfaceManager,
                                             IVpnFootprintService vpnFootprintService,
                                             IFibManager fibManager, NatDataUtil natDataUtil) {
        super(dataBroker, mdsalManager, itmManager, idManager, naptSwitchSelector,
                odlInterfaceRpcService, interfaceManager, vpnFootprintService, fibManager ,
                natDataUtil);
    }

    @Override
    public boolean handleSnatAllSwitch(Routers routers, BigInteger primarySwitchId,  int addOrRemove) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("FlatVlanConntrackBasedSnatService: handleSnatAllSwitch ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.VXLAN || extNwProviderType == ProviderTypes.GRE) {
            return false;
        }
        return super.handleSnatAllSwitch(routers, primarySwitchId, addOrRemove);
    }

    @Override
    public boolean handleSnat(Routers routers, BigInteger primarySwitchId, BigInteger dpnId,  int addOrRemove) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, routers.getNetworkId());
        LOG.debug("FlatVlanConntrackBasedSnatService: handleSnat ProviderTypes {}", extNwProviderType);
        if (extNwProviderType == ProviderTypes.VXLAN || extNwProviderType == ProviderTypes.GRE) {
            return false;
        }
        return super.handleSnat(routers, primarySwitchId, dpnId, addOrRemove);
    }
}
