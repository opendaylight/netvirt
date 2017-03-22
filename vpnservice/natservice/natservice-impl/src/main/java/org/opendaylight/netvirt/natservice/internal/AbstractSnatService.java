/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.netvirt.natservice.api.SnatServiceListener;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSnatService implements SnatServiceListener {

    protected final DataBroker dataBroker;
    protected final IMdsalApiManager mdsalManager;
    protected final IdManagerService idManager;
    protected final NaptManager naptManager;
    protected final NAPTSwitchSelector naptSwitchSelector;
    protected final ItmRpcService itmManager;
    protected final OdlInterfaceRpcService interfaceManager;
    private final IVpnManager vpnManager;
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSnatService.class);

    public AbstractSnatService(final DataBroker dataBroker, final IMdsalApiManager mdsalManager,
            final ItmRpcService itmManager,
            final OdlInterfaceRpcService interfaceManager,
            final IdManagerService idManager,
            final NaptManager naptManager,
            final NAPTSwitchSelector naptSwitchSelector,
            final IVpnManager vpnManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.itmManager = itmManager;
        this.interfaceManager = interfaceManager;
        this.idManager = idManager;
        this.naptManager = naptManager;
        this.naptSwitchSelector = naptSwitchSelector;
        this.vpnManager = vpnManager;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    public void close() {

        LOG.debug("AbstractSnatService Closed");
    }

    @Override
    public boolean handleSnatAllSwitch(Routers routers, BigInteger primarySwitchId,  int addOrRemove) {
        LOG.debug("AbstractSnatService : Handle Snat in all switches");
        String routerName = routers.getRouterName();
        installRouterGwFlows(routers, primarySwitchId, NwConstants.ADD_FLOW);
        List<BigInteger> switches = naptSwitchSelector.getDpnsForVpn(routerName);
        if (switches != null) {
            for (BigInteger dpnId : switches) {
                handleSnat(routers, primarySwitchId, dpnId, addOrRemove);
            }
        }
        return true;
    }

    @Override
    public boolean handleSnat(Routers routers, BigInteger primarySwitchId, BigInteger dpnId,  int addOrRemove) {

        // Handle non NAPT switches and NAPT switches separately
        if (!dpnId.equals(primarySwitchId)) {
            LOG.debug("AbstractSnatService : Handle non NAPT switch {}", dpnId);
            installSnatCommonEntriesForNonNaptSwitch(routers, primarySwitchId, dpnId, addOrRemove);
            installSnatSpecificEntriesForNonNaptSwitch(routers, dpnId, addOrRemove);
        } else {
            LOG.debug("AbstractSnatService : Handle NAPT switch {}", dpnId);
            installSnatCommonEntriesForNaptSwitch(routers, dpnId, addOrRemove);
            installSnatSpecificEntriesForNaptSwitch(routers, dpnId, addOrRemove);

        }
        return true;
    }

    protected void installSnatCommonEntriesForNaptSwitch(Routers routers, BigInteger dpnId,  int addOrRemove) {
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        Long extNetId = NatUtil.getVpnId(dataBroker, routers.getNetworkId().getValue());
        installDefaultFibRouteForSNAT(dpnId, routerId, addOrRemove);
        List<ExternalIps> externalIps = routers.getExternalIps();
        installInboundFibEntry(dpnId, extNetId,  externalIps, addOrRemove);
    }

    protected void installSnatCommonEntriesForNonNaptSwitch(Routers routers, BigInteger primarySwitchId,
            BigInteger dpnId, int addOrRemove) {
        String routerName = routers.getRouterName();
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        installDefaultFibRouteForSNAT(dpnId, routerId, addOrRemove);
        installSnatMissEntry(dpnId, routerId, routerName, primarySwitchId, addOrRemove);
    }

    protected abstract void installSnatSpecificEntriesForNaptSwitch(Routers routers, BigInteger dpnId,
            int addOrRemove);

    protected abstract void installSnatSpecificEntriesForNonNaptSwitch(Routers routers, BigInteger dpnId,
            int addOrRemove);

    protected void installInboundFibEntry(BigInteger dpnId, Long extNetId, List<ExternalIps> externalIps,
            int addOrRemove) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNetId), MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIpv4Destination(externalIps.get(0).getIpAddress(), "32"));

        ArrayList<ActionInfo> listActionInfo = new ArrayList<>();
        ArrayList<InstructionInfo> instructionInfo = new ArrayList<>();
        listActionInfo.add(new ActionNxResubmit(NwConstants.INBOUND_NAPT_TABLE));
        instructionInfo.add(new InstructionApplyActions(listActionInfo));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, extNetId);
        flowRef = flowRef + "inbound" + externalIps;
        syncFlow(dpnId, NwConstants.L3_FIB_TABLE, flowRef, NatConstants.SNAT_FIB_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructionInfo, addOrRemove);
    }

    protected void installRouterGwFlows(Routers router, BigInteger primarySwitchId, int addOrRemove) {
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        List<ExternalIps> externalIps = router.getExternalIps();
        List<String> externalIpsSting = new ArrayList<>();
        for (ExternalIps externalIp : externalIps) {
            externalIpsSting.add(externalIp.getIpAddress());
        }
        vpnManager.setupRouterGwMacFlow(router.getRouterName(), router.getExtGwMacAddress(), primarySwitchId,
                router.getNetworkId(), writeTx, addOrRemove);
        vpnManager.setupArpResponderFlowsToExternalNetworkIps(router.getRouterName(), externalIpsSting,
                router.getExtGwMacAddress(), primarySwitchId,
                router.getNetworkId(), writeTx, addOrRemove);
        writeTx.submit();
    }

    protected void installSnatMissEntry(BigInteger dpnId, Long routerId, String routerName, BigInteger primarySwitchId,
            int addOrRemove) {
        LOG.debug("AbstractSnatService : Installing SNAT miss entry in switch {}", dpnId);
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        String ifNamePrimary = getTunnelInterfaceName(dpnId, primarySwitchId);
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        if (ifNamePrimary != null) {
            LOG.debug("AbstractSnatService : On Non- Napt switch , Primary Tunnel interface is {}", ifNamePrimary);
            listActionInfoPrimary = NatUtil.getEgressActionsForInterface(interfaceManager, ifNamePrimary, routerId);
        }
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        listBucketInfo.add(0, bucketPrimary);
        LOG.debug("AbstractSnatService : installSnatMissEntry called for dpnId {} with primaryBucket {} ", dpnId,
                listBucketInfo.get(0));
        // Install the select group
        long groupId = createGroupId(getGroupIdKey(routerName));
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, routerName, GroupTypes.GroupAll,
                listBucketInfo);
        LOG.debug("AbstractSnatService : installing the SNAT to NAPT GroupEntity:{}", groupEntity);
        mdsalManager.installGroup(groupEntity);
        // Install miss entry pointing to group
        LOG.debug("AbstractSnatService : buildSnatFlowEntity is called for dpId {}, routerName {} and groupId {}",
                dpnId, routerName, groupId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchEthernetType(0x0800L));
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(routerId), MetaDataUtil.METADATA_MASK_VRFID));


        List<ActionInfo> actionsInfo = new ArrayList<>();
        actionsInfo.add(new ActionSetFieldTunnelId(BigInteger.valueOf(routerId)));
        LOG.debug("AbstractSnatService : Setting the tunnel to the list of action infos {}", actionsInfo);
        actionsInfo.add(new ActionGroup(groupId));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfo));
        String flowRef = getFlowRef(dpnId, NwConstants.PSNAT_TABLE, routerId);
        syncFlow(dpnId, NwConstants.PSNAT_TABLE, flowRef,  NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected void installDefaultFibRouteForSNAT(BigInteger dpnId, Long extNetId, int addOrRemove) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(extNetId),
                MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.PSNAT_TABLE));

        String flowRef = getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, extNetId);
        syncFlow(dpnId, NwConstants.L3_FIB_TABLE, flowRef, NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef,
                NwConstants.COOKIE_SNAT_TABLE, matches, instructions, addOrRemove);
    }

    protected BigInteger getPrimaryNaptSwitch(String routerName, long routerId) {
        // Allocate Primary Napt Switch for this router
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterId(dataBroker, routerId);
        if (primarySwitchId != null && !primarySwitchId.equals(BigInteger.ZERO)) {
            LOG.debug("AbstractSnatService : Primary NAPT switch with DPN ID {} is already elected for router",
                    primarySwitchId, routerName);
            return primarySwitchId;
        }

        primarySwitchId = naptSwitchSelector.selectNewNAPTSwitch(routerName);
        LOG.debug("AbstractSnatService : Primary NAPT switch DPN ID {}", primarySwitchId);
        if (primarySwitchId == null || primarySwitchId.equals(BigInteger.ZERO)) {
            LOG.error("AbstractSnatService : Unable to to select the primary NAPT switch");
        }

        return primarySwitchId;
    }

    protected String getFlowRef(BigInteger dpnId, short tableId, long routerID) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId).append(NatConstants
                .FLOWID_SEPARATOR).append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }

    protected void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
            BigInteger cookie, List<? extends MatchInfoBase> matches,
            List<InstructionInfo> instructions, int addOrRemove) {
        if (addOrRemove == NwConstants.DEL_FLOW) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                    NatConstants.DEFAULT_IDLE_TIMEOUT, NatConstants.DEFAULT_IDLE_TIMEOUT, cookie, matches, null);
            LOG.trace("Removing Acl Flow DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.removeFlow(flowEntity);
        } else {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName,
                    NatConstants.DEFAULT_IDLE_TIMEOUT, NatConstants.DEFAULT_IDLE_TIMEOUT, cookie, matches,
                    instructions);
            LOG.trace("Installing DpnId {}, flowId {}", dpId, flowId);
            mdsalManager.installFlow(flowEntity);
        }
    }

    private long createGroupId(String groupIdKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NatConstants.SNAT_IDPOOL_NAME).setIdKey(groupIdKey)
            .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("",e);
        }
        return 0;
    }

    private String getGroupIdKey(String routerName) {
        String groupIdKey = new String("snatmiss." + routerName);
        return groupIdKey;
    }

    private String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        RpcResult<GetTunnelInterfaceNameOutput> rpcResult;
        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result = itmManager
                    .getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder().setSourceDpid(srcDpId)
                            .setDestinationDpid(dstDpId).setTunnelType(tunType).build());
            rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                tunType = TunnelTypeGre.class ;
                result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                        .setSourceDpid(srcDpId)
                        .setDestinationDpid(dstDpId)
                        .setTunnelType(tunType)
                        .build());
                rpcResult = result.get();
                if (!rpcResult.isSuccessful()) {
                    LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
                } else {
                    return rpcResult.getResult().getInterfaceName();
                }
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException e) {
            LOG.warn("AbstractSnatService : Exception when getting tunnel interface Id for tunnel between {} and  {}",
                    srcDpId, dstDpId);
        }
        return null;
    }
}