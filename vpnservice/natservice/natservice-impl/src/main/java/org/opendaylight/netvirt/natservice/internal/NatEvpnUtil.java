/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatEvpnUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NatEvpnUtil.class);

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addRoutesForVxLanProvType(DataBroker broker,
                                      IBgpManager bgpManager,
                                      IFibManager fibManager,
                                      String vpnName,
                                      String rd,
                                      String prefix,
                                      String nextHopIp,
                                      long l3Vni,
                                      String gwMacAddress,
                                      WriteTransaction writeTx,
                                      RouteOrigin origin,BigInteger dpId) {
        try {
            LOG.info("NAT Service : ADD: Adding Fib entry rd {} prefix {} nextHop {} l3Vni {}", rd, prefix, nextHopIp,
                    l3Vni);
            if (nextHopIp == null) {
                LOG.error("NAT Service : addPrefix failed since nextHopIp cannot be null for prefix {}", prefix);
                return;
            }
            NatUtil.addPrefixToInterface(broker, NatUtil.getVpnId(broker, vpnName), prefix, dpId, /*isNatPrefix*/ true);

            fibManager.addOrUpdateFibEntry(broker, rd, null /*macAddress*/, prefix,
                    Collections.singletonList(nextHopIp), VrfEntry.EncapType.Vxlan, 0 /*label*/, l3Vni,
                    gwMacAddress, origin, writeTx);

            bgpManager.advertisePrefix(rd, null /*macAddress*/, prefix, Collections.singletonList(nextHopIp),
                    VrfEntry.EncapType.Vxlan, 0 /*label*/, l3Vni, gwMacAddress);
            LOG.info("NAT Service : ADD: Added Fib entry rd {} prefix {} nextHop {} l3Vni {}", rd, prefix, nextHopIp,
                    l3Vni);
        } catch (Exception e) {
            LOG.error("NAT Service : Exception {} in add routes for prefix {}", e, prefix);
        }
    }

    static VrfEntry.EncapType getExtNwProviderType(DataBroker broker, String rd) {
        long l3Vni = getL3Vni(broker, rd);
        if (l3Vni != NatConstants.DEFAULT_L3VNI_VALUE ) {
            return VrfEntry.EncapType.Vxlan;
        }
        return VrfEntry.EncapType.Mplsgre;
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
            LOG.error("NAT Service : idManager Failed with exception {} while getting Router_lPort_Tag "
                    + "from pool with key {} ", e.getStackTrace(), routerIdKey);
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
                LOG.error("NAT Service : idManager Failed  {} to release Router_lPort_Tag from pool",
                        rpcResult.getErrors());
            }
            LOG.debug("NAT Service : idManager successfully released Router_lPort_Tag from pool with key {}",
                    routerIdKey);
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : idManager Failed with exception {} while releasing Router_lPort_Tag from pool "
                    + "with key {} ", e.getStackTrace(), routerIdKey);
        }
    }

    static ProviderTypes getExtNwProviderTypeFromRouterName(DataBroker dataBroker, String routerName) {
        ProviderTypes extNwProviderType = null;
        Uuid externalNetworkId = NatUtil.getNetworkIdFromRouterName(dataBroker,routerName);
        if (externalNetworkId == null) {
            LOG.error("NAT Service : Could not retrieve external network UUID for router {}"
                    + " in NatEvpnUtil.getExtNwProviderTypeFromRouterName()", routerName);
            return extNwProviderType;
        }
        extNwProviderType = NatUtil.getProviderTypefromNetworkId(dataBroker, externalNetworkId);
        if (extNwProviderType == null) {
            LOG.error("NAT Service : Could not retrieve provider type for external network {} "
                    + "in NatEvpnUtil.getExtNwProviderTypeFromRouterName()", externalNetworkId);
            return extNwProviderType;
        }
        return extNwProviderType;
    }

    static void makeL3GwMacTableEntry(final BigInteger dpnId, final long vpnId, String gwMacAddress,
                                      List<Instruction> customInstructions, IMdsalApiManager mdsalManager) {
        List<MatchInfo> matchInfo = new ArrayList<>();
        matchInfo.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matchInfo.add(new MatchEthernetDestination(new MacAddress(gwMacAddress)));
        LOG.debug("NAT Service : Create SNAT Reverse Traffic Flow in table {} on DpnId = {} "
                        + "for External Vpn Id = {} and GwMacAddress = {}", NwConstants.INBOUND_NAPT_TABLE, dpnId,
                vpnId, gwMacAddress);
        // Install the flow entry in L3_GW_MAC_TABLE
        String flowRef = NatUtil.getFlowRef(dpnId, NwConstants.L3_GW_MAC_TABLE, vpnId, gwMacAddress);
        Flow l3GwMacTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_GW_MAC_TABLE,
                flowRef, 21, flowRef, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE, matchInfo, customInstructions);

        mdsalManager.installFlow(dpnId, l3GwMacTableFlowEntity);
        LOG.debug("NAT Service : L3_GW_MAC_TABLE entry for SNAT Reverse Traffic flow created Successfully "
                + "on DPN = {}", dpnId);
    }

    static void removeL3GwMacTableEntry(final BigInteger dpnId, final long vpnId, final String gwMacAddress,
                                        IMdsalApiManager mdsalManager) {
        List<MatchInfo> matchInfo = new ArrayList<>();
        matchInfo.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matchInfo.add(new MatchEthernetSource(new MacAddress(gwMacAddress)));
        LOG.debug("NAT Service : Remove SNAT Reverse Traffic Flow in table {} on DpnId = {} "
                        + "for External Vpn Id = {} and gwMacAddress = {}", NwConstants.INBOUND_NAPT_TABLE, dpnId,
                vpnId, gwMacAddress);
        // Remove the flow entry in L3_GW_MAC_TABLE
        String flowRef = NatUtil.getFlowRef(dpnId, NwConstants.L3_GW_MAC_TABLE, vpnId, gwMacAddress);
        Flow l3GwMacTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.L3_GW_MAC_TABLE,
                flowRef, 21, flowRef, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE, matchInfo, null);

        mdsalManager.removeFlow(dpnId, l3GwMacTableFlowEntity);
        LOG.debug("NAT Service : L3_GW_MAC_TABLE entry for SNAT Reverse Traffic flow removed Successfully "
                + "on DPN = {}", dpnId);
    }

}
