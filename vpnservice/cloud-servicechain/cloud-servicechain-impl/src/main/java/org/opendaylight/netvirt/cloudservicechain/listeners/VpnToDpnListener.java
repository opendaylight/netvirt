/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.netvirt.cloudservicechain.VPNServiceChainHandler;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class VpnToDpnListener implements OdlL3vpnListener {

    private final DataBroker broker;
    private final IMdsalApiManager mdsalMgr;
    private final VPNServiceChainHandler vpnScHandler;

    private static final Logger logger = LoggerFactory.getLogger(VpnToDpnListener.class);

    public VpnToDpnListener(final DataBroker db, final IMdsalApiManager mdsalManager,
                            VPNServiceChainHandler vpnServiceChainHandler) {
        this.broker = db;
        this.mdsalMgr = mdsalManager;
        this.vpnScHandler = vpnServiceChainHandler;
    }

    @Override
    public void onAddDpnEvent(AddDpnEvent notification) {
        programVpnScfFlowsOnDpn(notification.getAddEventData().getDpnId(),
                                notification.getAddEventData().getVpnName(),
                                notification.getAddEventData().getRd(),
                                NwConstants.ADD_FLOW);

    }

    @Override
    public void onRemoveDpnEvent(RemoveDpnEvent notification) {
        programVpnScfFlowsOnDpn(notification.getRemoveEventData().getDpnId(),
                                notification.getRemoveEventData().getVpnName(),
                                notification.getRemoveEventData().getRd(),
                                NwConstants.DEL_FLOW);
    }

    private void programVpnScfFlowsOnDpn(BigInteger dpnId, String vpnName, String rd, int addOrRemove) {
        String addedOrRemovedTxt = addOrRemove == NwConstants.ADD_FLOW ? " added " : " removed";
        logger.debug("DpnToVpn {} event received: dpn={}  vpn={}  rd={}", addedOrRemovedTxt, dpnId, vpnName, rd);
        if ( dpnId == null ) {
            logger.warn("Dpn to Vpn {} event received, but no DPN specified in event", addedOrRemovedTxt);
            return;
        }

        if ( vpnName == null ) {
            logger.warn("Dpn to Vpn {} event received, but no VPN specified in event", addedOrRemovedTxt);
            return;
        }

        if ( rd == null ) {
            logger.warn("Dpn to Vpn {} event received, but no RD specified in event", addedOrRemovedTxt);
            return;
        }

        Optional<VpnToPseudoPortData> optVpnToPseudoPortInfo = VpnServiceChainUtils.getVpnPseudoPortData(broker, rd);

        if ( !optVpnToPseudoPortInfo.isPresent() ) {
            logger.debug("Dpn to Vpn {} event received: Could not find VpnPseudoLportTag for VPN name={}  rd={}",
                    addedOrRemovedTxt, vpnName, rd);
            return;
        }

        VpnToPseudoPortData vpnToPseudoPortInfo = optVpnToPseudoPortInfo.get();

        // Vpn2Scf flows (LFIB + LportDispatcher)
        // TODO: Should we filter out by bgp origin
        List<VrfEntry> allVpnVrfEntries = VpnServiceChainUtils.getAllVrfEntries(broker, rd);
        vpnScHandler.programVpnToScfPipelineOnDpn(dpnId, allVpnVrfEntries,
                                                  vpnToPseudoPortInfo.getScfTableId(),
                                                  vpnToPseudoPortInfo.getScfTag(),
                                                  vpnToPseudoPortInfo.getVpnLportTag().intValue(),
                                                  addOrRemove);

        // Scf2Vpn flow (LportDispatcher)
        long vpnId = addOrRemove == NwConstants.ADD_FLOW ? VpnServiceChainUtils.getVpnId(broker, vpnName)
                : CloudServiceChainConstants.INVALID_VPN_TAG;
        VpnServiceChainUtils.programLPortDispatcherFlowForScfToVpn(mdsalMgr, vpnId, dpnId,
                                                                   vpnToPseudoPortInfo.getVpnLportTag().intValue(),
                                                                   addOrRemove);
    }
}
