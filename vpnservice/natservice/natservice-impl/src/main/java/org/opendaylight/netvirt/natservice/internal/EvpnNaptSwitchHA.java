/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvpnNaptSwitchHA {

    private static final Logger LOG = LoggerFactory.getLogger(EvpnNaptSwitchHA.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;
    private final IdManagerService idManager;
    private final EvpnSnatFlowProgrammer evpnSnatFlowProgrammer;

    @Inject
    public EvpnNaptSwitchHA(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
                        final EvpnSnatFlowProgrammer evpnSnatFlowProgrammer,
                        final IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.evpnSnatFlowProgrammer = evpnSnatFlowProgrammer;
        this.idManager = idManager;
    }

    public void evpnRemoveSnatFlowsInOldNaptSwitch(String routerName, long routerId, String vpnName,
                                                   BigInteger naptSwitch, WriteTransaction removeFlowInvTx) {
        //Handling VXLAN Provider type flow removal from old NAPT switch
        Long vpnId = NatUtil.getNetworkVpnIdFromRouterId(dataBroker, routerId);
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.error("evpnRemoveSnatFlowsInOldNaptSwitch : Unable to retrieved vpnId for routerId {}", routerId);
            return;
        }
        String rd = NatUtil.getVpnRd(dataBroker, vpnName);
        if (rd == null) {
            LOG.error("evpnRemoveSnatFlowsInOldNaptSwitch : Could not retrieve RD value from VPN Name {} ", vpnName);
            return;
        }
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            LOG.debug("evpnRemoveSnatFlowsInOldNaptSwitch : L3VNI value is not configured in Internet VPN {} and RD {} "
                    + "Carve-out L3VNI value from OpenDaylight VXLAN VNI Pool and continue to installing "
                    + "NAT flows", vpnName, rd);
            l3Vni = NatOverVxlanUtil.getInternetVpnVni(idManager, vpnName, routerId).longValue();
        }
        String gwMacAddress = NatUtil.getExtGwMacAddFromRouterId(dataBroker, routerId);
        if (gwMacAddress == null) {
            LOG.error("evpnRemoveSnatFlowsInOldNaptSwitch : Unable to Get External Gateway MAC address for "
                    + "External Router ID {} ", routerId);
            return;
        }
        //Remove the L3_GW_MAC_TABLE which forwards the packet to Inbound NAPT Table (table19->44)
        NatEvpnUtil.removeL3GwMacTableEntry(naptSwitch, vpnId, gwMacAddress, mdsalManager, removeFlowInvTx);

        //Remove the PDNAT_TABLE which forwards the packet to Inbound NAPT Table (table25->44)
        NatUtil.removePreDnatToSnatTableEntry(mdsalManager, naptSwitch, removeFlowInvTx);

        //Remove the PSNAT_TABLE which forwards the packet to Outbound NAPT Table (table26->46)
        String flowRef = getFlowRefSnat(naptSwitch, NwConstants.PSNAT_TABLE, routerName);
        FlowEntity flowEntity = NatUtil.buildFlowEntity(naptSwitch, NwConstants.PSNAT_TABLE, flowRef);
        LOG.info("evpnRemoveSnatFlowsInOldNaptSwitch: Remove the flow (table26->46) in table {} "
                + "for the old napt switch with the DPN ID {} and router ID {}",
                NwConstants.PSNAT_TABLE, naptSwitch, routerId);
        mdsalManager.removeFlowToTx(flowEntity, removeFlowInvTx);

        //Remove the Terminating Service table entry which forwards the packet to Inbound NAPT Table (table36->44)
        LOG.info("evpnRemoveSnatFlowsInOldNaptSwitch : Remove the flow (table36->44) in table {} "
                + "for the old napt switch with the DPN ID {} and router ID {}",
                NwConstants.INTERNAL_TUNNEL_TABLE, naptSwitch, routerId);
        evpnSnatFlowProgrammer.removeTunnelTableEntry(naptSwitch, l3Vni, removeFlowInvTx);

        //Remove the INTERNAL_TUNNEL_TABLE entry which forwards the packet to Outbound NAPT Table (table36->46)
        long tunnelId = NatEvpnUtil.getTunnelIdForRouter(idManager, dataBroker, routerName, routerId);

        String tsFlowRef = getFlowRefTs(naptSwitch, NwConstants.INTERNAL_TUNNEL_TABLE, tunnelId);
        FlowEntity tsNatFlowEntity = NatUtil.buildFlowEntity(naptSwitch, NwConstants.INTERNAL_TUNNEL_TABLE, tsFlowRef);
        LOG.info("evpnRemoveSnatFlowsInOldNaptSwitch : Remove the flow in the {} for the active switch "
                + "with the DPN ID {} and router ID {}", NwConstants.INTERNAL_TUNNEL_TABLE, naptSwitch, routerId);
        mdsalManager.removeFlowToTx(tsNatFlowEntity, removeFlowInvTx);

    }

    private String getFlowRefTs(BigInteger dpnId, short tableId, long routerID) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }

    private String getFlowRefSnat(BigInteger dpnId, short tableId, String routerID) {
        return NatConstants.SNAT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID;
    }
}
