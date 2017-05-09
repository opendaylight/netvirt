/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlatVlanConntrackBasedSnatService extends ConntrackBasedSnatService {

    private static final Logger LOG = LoggerFactory.getLogger(FlatVlanConntrackBasedSnatService.class);

    public FlatVlanConntrackBasedSnatService(DataBroker dataBroker, IMdsalApiManager mdsalManager,
            ItmRpcService itmManager, OdlInterfaceRpcService interfaceManager, IdManagerService idManager,
            NaptManager naptManager, NAPTSwitchSelector naptSwitchSelector, IVpnManager vpnManager) {
        super(dataBroker, mdsalManager, itmManager, interfaceManager, idManager, naptManager, naptSwitchSelector,
                vpnManager);
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

    @Override
    protected void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId, int addOrRemove) {
        LOG.info("FlatVlanConntrackBasedSnatService: installSnatSpecificEntriesForNaptSwitch for router {}",
                routers.getRouterName());
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        int elanId = NatUtil.getElanInstanceByName(routers.getNetworkId().getValue(), dataBroker)
                .getElanTag().intValue();
        /* Install Outbound NAT entries */

        installSnatMissEntryForPrimrySwch(dpnId, routerId, elanId, addOrRemove);
        installTerminatingServiceTblEntry(dpnId, routerId, elanId, addOrRemove);
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        List<ExternalIps> externalIps = routers.getExternalIps();
        createOutboundTblTrackEntry(dpnId, routerId, extNetId,addOrRemove);
        createOutboundTblEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
        installNaptPfibExternalOutputFlow(routers, dpnId, externalIps, addOrRemove);
        //Install Inbound NAT entries
        installInboundEntry(dpnId, routerId, extNetId, externalIps, elanId, addOrRemove);
        installNaptPfibEntry(dpnId, routerId, addOrRemove);
    }
}
