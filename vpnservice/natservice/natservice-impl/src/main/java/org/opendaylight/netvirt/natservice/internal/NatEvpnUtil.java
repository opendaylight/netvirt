/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatEvpnUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NatEvpnUtil.class);

    static long getLPortTagForRouter(String routerIdKey, IdManagerService idManager) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(routerIdKey)
                .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : ID manager failed while allocating lport_tag for router {}."
                    + "Exception {}", routerIdKey, e);
        }
        return 0;
    }

    public static void releaseLPortTagForRouter(DataBroker dataBroker, IdManagerService idManager, String routerName) {

        String rd = NatUtil.getVpnRd(dataBroker, routerName);
        long l3Vni = NatEvpnUtil.getL3Vni(dataBroker, rd);
        if (!NatEvpnUtil.isL3VpnOverVxLan(l3Vni)) {
            return;
        }
        ReleaseIdInput getIdInput = new ReleaseIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(routerName)
                .build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(getIdInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("NAT Service : ID manager failed while releasing allocated lport_tag for router {}."
                        + "Exception {} ", routerName, rpcResult.getErrors());
                return;
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : ID manager failed while releasing allocated lport_tag for router {}."
                    + "Exception {}", routerName, e);
        }
    }

    public static long getTunnelIdForRouter(IdManagerService idManager, DataBroker dataBroker, String routerName,
                                            long routerId) {
        /* Only if the router is part of an L3VPN-Over-VXLAN, Router_lPort_Tag which will be carved out per router
         from 'interfaces' POOL and used as tunnel_id. Otherwise we will continue to use router-id as the tunnel-id
         in the following Flows.

         1) PSNAT_TABLE (on Non-NAPT) -> Send to Remote Group
         2) INTERNAL_TUNNEL_TABLE (on NAPT) -> Send to OUTBOUND_NAPT_TABLE */
        String rd = NatUtil.getVpnRd(dataBroker, routerName);
        long l3Vni = getL3Vni(dataBroker, rd);
        if (isL3VpnOverVxLan(l3Vni)) {
            long routerLportTag = getLPortTagForRouter(routerName, idManager);
            if (routerLportTag != 0) {
                LOG.trace("NAT Service : Successfully allocated Router_lPort_Tag = {} from ID Manager for "
                        + "Router ID = {}", routerLportTag, routerId);
                return routerLportTag;
            } else {
                LOG.warn("NAT Service : Failed to allocate Router_lPort_Tag from ID Manager for Router ID = {} "
                        + "Continue to use router-id as tunnel-id", routerId);
                return routerId;
            }
        }
        return routerId;
    }

    static long getL3Vni(DataBroker broker, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpData(broker, rd);
        if (vpnInstanceOpDataEntry != null && vpnInstanceOpDataEntry.getL3vni() != null) {
            return vpnInstanceOpDataEntry.getL3vni();
        }
        return NatConstants.DEFAULT_L3VNI_VALUE;
    }

    private static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    private static boolean isL3VpnOverVxLan(Long l3Vni) {
        return (l3Vni != null && l3Vni != 0);
    }
}
