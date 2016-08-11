/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import java.math.BigInteger;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.CloudServiceChainConstants;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AddDpnEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.OdlL3vpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RemoveDpnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class VpnToDpnListener implements OdlL3vpnListener {

    private final DataBroker broker;
    private final IMdsalApiManager mdsalMgr;

    private static final Logger logger = LoggerFactory.getLogger(VpnToDpnListener.class);

    public VpnToDpnListener(final DataBroker db, final IMdsalApiManager mdsalManager) {
        this.broker = db;
        this.mdsalMgr = mdsalManager;
    }

    @Override
    public void onAddDpnEvent(AddDpnEvent notification) {

        programLPortDispatcherFlowForScfToVpn(notification.getAddEventData().getDpnId(),
                notification.getAddEventData().getVpnName(),
                notification.getAddEventData().getRd(),
                NwConstants.ADD_FLOW);

    }

    @Override
    public void onRemoveDpnEvent(RemoveDpnEvent notification) {

        programLPortDispatcherFlowForScfToVpn(notification.getRemoveEventData().getDpnId(),
                notification.getRemoveEventData().getVpnName(),
                notification.getRemoveEventData().getRd(),
                NwConstants.DEL_FLOW);

    }

    private void programLPortDispatcherFlowForScfToVpn(BigInteger dpnId, String vpnName, String rd, int addOrRemove) {
        String addedOrRemovedTxt = addOrRemove == NwConstants.ADD_FLOW ? " added " : " removed";
        logger.debug("DpnToVpn {}event received: dpn={}  vpn={}  rd={}", addedOrRemovedTxt, dpnId, vpnName, rd);
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

        Optional<Long> vpnPseudoLportTag = VpnServiceChainUtils.getVpnPseudoLportTag(broker, rd);
        if ( !vpnPseudoLportTag.isPresent() || vpnPseudoLportTag.get() == null ) {
            logger.debug("Dpn to Vpn {} event received: Could not find VpnPseudoLportTag for VPN name={}  rd={}",
                    addedOrRemovedTxt, vpnName, rd);
            return;
        }
        long vpnId = (addOrRemove == NwConstants.ADD_FLOW ) ? VpnServiceChainUtils.getVpnId(broker, vpnName)
                : CloudServiceChainConstants.INVALID_VPN_TAG;
        VpnServiceChainUtils.programLPortDispatcherFlowForScfToVpn(mdsalMgr, vpnId, dpnId,
                vpnPseudoLportTag.get().intValue(), addOrRemove);
    }
}
