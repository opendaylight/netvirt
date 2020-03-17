/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.listeners.DataTreeEventCallbackRegistrar;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
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
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlatVlanConntrackBasedSnatService extends ConntrackBasedSnatService {

    private static final Logger LOG = LoggerFactory.getLogger(FlatVlanConntrackBasedSnatService.class);

    public FlatVlanConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                             ItmRpcService itmManager, OdlInterfaceRpcService odlInterfaceRpcService,
                                             IdManagerService idManager, NAPTSwitchSelector naptSwitchSelector,
                                             IInterfaceManager interfaceManager,
                                             IVpnFootprintService vpnFootprintService,
                                             IFibManager fibManager, NatDataUtil natDataUtil,
                                             DataTreeEventCallbackRegistrar eventCallbacks) {
        super(dataBroker, mdsalManager, itmManager, idManager, naptSwitchSelector,
                odlInterfaceRpcService, interfaceManager, vpnFootprintService, fibManager ,
                natDataUtil, eventCallbacks);
    }

    @Override
    public boolean addSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 primarySwitchId) {
        if (checkProviderType(confTx, routers)) {
            return false;
        }
        return super.addSnatAllSwitch(confTx, routers, primarySwitchId);
    }

    @Override
    public boolean removeSnatAllSwitch(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId) throws ExecutionException, InterruptedException {
        return !checkProviderType(confTx, routers) && super.removeSnatAllSwitch(confTx, routers, primarySwitchId);
    }

    @Override
    public boolean addSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
        Uint64 primarySwitchId, Uint64 dpnId) {
        return !checkProviderType(confTx, routers) && super.addSnat(confTx, routers, primarySwitchId, dpnId);
    }

    @Override
    public boolean removeSnat(TypedReadWriteTransaction<Configuration> confTx, Routers routers,
            Uint64 primarySwitchId, Uint64 dpnId) throws ExecutionException, InterruptedException {
        return !checkProviderType(confTx, routers) && super.removeSnat(confTx, routers, primarySwitchId, dpnId);
    }

    private boolean checkProviderType(TypedReadWriteTransaction<Configuration> confTx, Routers routers) {
        ProviderTypes extNwProviderType = NatUtil.getProviderTypefromNetworkId(confTx, routers.getNetworkId());
        LOG.debug("FlatVlanConntrackBasedSnatService: ProviderTypes {}", extNwProviderType);
        return extNwProviderType == ProviderTypes.VXLAN || extNwProviderType == ProviderTypes.GRE;
    }
}
