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
        LOG.debug("NAT Service : Get Router_lPort_Tag from ID Manager for Non-NAPT Switch to NAPT Switch to use "
                + "Tunnel ID");
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(routerIdKey)
                .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : ID manager failed with exception {} while getting Router_lPort_Tag "
                    + "with key {} ", e.getStackTrace(), routerIdKey);
        }
        return 0;
    }

    static void releaseLPortTagForRouter(String routerIdKey, IdManagerService idManager) {
        LOG.debug("NAT Service : Release Router_lPort_Tag ID Pool for Non-NAPT Switch to NAPT Switch to use Tunnel ID");
        ReleaseIdInput getIdInput = new ReleaseIdInputBuilder()
                .setPoolName(IfmConstants.IFM_IDPOOL_NAME).setIdKey(routerIdKey)
                .build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(getIdInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("NAT Service : ID manager failed  with error {} to release Router_lPort_Tag ",
                        rpcResult.getErrors());
            }
            LOG.debug("NAT Service : Successfully released Router_lPort_Tag from ID manager with key {}",
                    routerIdKey);
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : ID manager with exception {} while releasing Router_lPort_Tag "
                    + "for Router {} ", e.getStackTrace(), routerIdKey);
        }
    }

    public static long getTunnelIdForRouter(IdManagerService idManager, DataBroker dataBroker, String routerName,
                                            long routerId) {
        /* Non-NAPT to NAPT communication, tunnel id will be setting with Router_lPort_Tag which will be carved
           out per router only if the router is associated with l3VpnOverVxlan else we will be continuing to use
           existing approach as router-id as tunnel-id(26->Group on Non-NAPT, 36->46 on NAPT) */
        String rd = NatUtil.getVpnRd(dataBroker, routerName);
        long l3Vni = getL3Vni(dataBroker, rd);
        if (isL3VpnOverVxLan(l3Vni)) {
            long routerLportTag = NatEvpnUtil.getLPortTagForRouter(routerName, idManager);
            if (routerLportTag != 0) {
                LOG.trace("NAT Service : Successfully allocated Router_lPort_Tag = {} from ID Manager for "
                        + "Router ID = {}", routerLportTag, routerId);
                return routerLportTag;
            } else {
                LOG.trace("NAT Service : Failed to allocate Router_lPort_Tag from ID Manager for Router ID = {} "
                        + "Continue to use router-id as tunnel-id", routerId);
                return routerId;
            }
        }
        return routerId;
    }

    static long getL3Vni(DataBroker broker, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpData(broker, rd);
        if (vpnInstanceOpDataEntry == null) {
            return NatConstants.DEFAULT_L3VNI_VALUE;
        }
        Long l3Vni = vpnInstanceOpDataEntry.getL3vni();
        if (l3Vni == null || l3Vni == NatConstants.DEFAULT_L3VNI_VALUE) {
            return NatConstants.DEFAULT_L3VNI_VALUE;
        }
        return l3Vni;
    }

    static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    static boolean isL3VpnOverVxLan(Long l3Vni) {
        return (l3Vni != null && l3Vni != 0);
    }
}
