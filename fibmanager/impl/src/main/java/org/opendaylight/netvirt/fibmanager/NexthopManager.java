/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;
import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionOutput;
import org.opendaylight.genius.mdsalutil.actions.ActionPushMpls;
import org.opendaylight.genius.mdsalutil.actions.ActionPushVlan;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.actions.ActionRegMove;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetDestination;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldTunnelId;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldVlanVid;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.L3VPNTransportTypes;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelOperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.DcGatewayIpList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.dc.gateway.ip.list.DcGatewayIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetInternalOrExternalInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetInternalOrExternalInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.AddGroupInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.AddGroupInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.AddGroupOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.SalGroupService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.Buckets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeFlat;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthopBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthopKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.vpnnexthop.IpAdjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.vpnnexthop.IpAdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.vpnnexthop.IpAdjacenciesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ConfTransportTypeL3vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ConfTransportTypeL3vpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.l3vpn.lb.nexthops.Nexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NexthopManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NexthopManager.class);
    private static final String NEXTHOP_ID_POOL_NAME = "nextHopPointerPool";
    private static final long WAIT_TIME_FOR_SYNC_INSTALL = Long.getLong("wait.time.sync.install", 300L);
    //  We set the total wait time for lock to be obtained at 9 seconds since GC pauses can be upto 8 seconds
    //in scale setups.
    private static final long WAIT_TIME_TO_ACQUIRE_LOCK = 9000L;
    private static final int SELECT_GROUP_WEIGHT = 1;
    private static final int RETRY_COUNT = 6;

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalApiManager;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final ItmRpcService itmManager;
    private final IdManagerService idManager;
    private final IElanService elanService;
    private final LockManagerService lockManager;
    private final SalGroupService salGroupService;
    private final JobCoordinator jobCoordinator;
    private final FibUtil fibUtil;
    private final IInterfaceManager interfaceManager;
    private volatile L3VPNTransportTypes configuredTransportTypeL3VPN = L3VPNTransportTypes.Invalid;

    /**
     * Provides nexthop functions.
     * Creates group ID pool
     *
     * @param dataBroker       - dataBroker reference
     * @param mdsalApiManager  - mdsalApiManager reference
     * @param idManager        - idManager reference
     * @param odlInterfaceRpcService - odlInterfaceRpcService reference
     * @param itmManager       - itmManager reference
     */
    @Inject
    public NexthopManager(final DataBroker dataBroker,
                          final IMdsalApiManager mdsalApiManager,
                          final IdManagerService idManager,
                          final OdlInterfaceRpcService odlInterfaceRpcService,
                          final ItmRpcService itmManager,
                          final LockManagerService lockManager,
                          final IElanService elanService,
                          final SalGroupService salGroupService,
                          final JobCoordinator jobCoordinator,
                          final FibUtil fibUtil,
                          final IInterfaceManager interfaceManager) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.mdsalApiManager = mdsalApiManager;
        this.idManager = idManager;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.itmManager = itmManager;
        this.elanService = elanService;
        this.salGroupService = salGroupService;
        this.jobCoordinator = jobCoordinator;
        this.fibUtil = fibUtil;
        this.lockManager = lockManager;
        this.interfaceManager = interfaceManager;
        createIdPool();
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME)
            .setLow(150000L)
            .setHigh(175000L)
            .build();
        try {
            Future<RpcResult<CreateIdPoolOutput>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("Created IdPool for NextHopPointerPool");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for NextHopPointerPool", e);
        }
    }

    private String getNextHopKey(Uint32 vpnId, String ipAddress) {
        return "nexthop." + vpnId + ipAddress;
    }

    String getRemoteSelectGroupKey(Uint32 vpnId, String ipAddress) {
        return "remote.ecmp.nexthop." + vpnId + ipAddress;
    }

    String getLocalSelectGroupKey(Uint32 vpnId, String ipAddress) {
        return "local.ecmp.nexthop." + vpnId + ipAddress;
    }

    public ItmRpcService getItmManager() {
        return itmManager;
    }

    protected long createNextHopPointer(String nexthopKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME).setIdKey(nexthopKey)
            .build();
        //TODO: Proper error handling once IdManager code is complete
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue().toJava();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            // FIXME: NPEs should not be caught but rather their root cause should be eliminated
            LOG.trace("Failed to allocate {}", getIdInput, e);
        }
        return 0;
    }

    protected void removeNextHopPointer(String nexthopKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME)
            .setIdKey(nexthopKey).build();
        try {
            RpcResult<ReleaseIdOutput> rpcResult = idManager.releaseId(idInput).get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get Unique Id for nexthopKey {} returned with Errors {}",
                        nexthopKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id for key {}", nexthopKey, e);
        }
    }

    protected List<ActionInfo> getEgressActionsForInterface(final String ifName, int actionKey,
                                                            boolean isTunnelInterface,
                                                            Uint32 vpnId, String destIpPrefix) {
        List<Action> actions;
        try {
            if (isTunnelInterface && interfaceManager.isItmDirectTunnelsEnabled()) {
                RpcResult<GetEgressActionsForTunnelOutput> rpcResult =
                        itmManager.getEgressActionsForTunnel(new GetEgressActionsForTunnelInputBuilder()
                                .setIntfName(ifName).build()).get();
                if (!rpcResult.isSuccessful()) {
                    LOG.error("RPC Call to Get egress tunnel actions for interface {} returned with Errors {}",
                            ifName, rpcResult.getErrors());
                    return Collections.emptyList();
                } else {
                    actions = new ArrayList<Action>(rpcResult.getResult().nonnullAction().values());
                }
            } else {
                RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = odlInterfaceRpcService
                        .getEgressActionsForInterface(new GetEgressActionsForInterfaceInputBuilder()
                                .setIntfName(ifName).build()).get();
                if (!rpcResult.isSuccessful()) {
                    LOG.error("RPC Call to Get egress vm actions for interface {} vpnId {} ipPrefix {} returned with "
                                    + "Errors {}", ifName, vpnId, destIpPrefix, rpcResult.getErrors());
                    return Collections.emptyList();
                } else {
                    actions = new ArrayList<Action>(rpcResult.getResult().nonnullAction().values());
                }
            }
            List<ActionInfo> listActionInfo = new ArrayList<>();
            for (Action action : actions) {
                actionKey = action.key().getOrder() + actionKey;
                org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action
                    actionClass = action.getAction();
                if (actionClass instanceof OutputActionCase) {
                    listActionInfo.add(new ActionOutput(actionKey,
                        ((OutputActionCase) actionClass).getOutputAction().getOutputNodeConnector()));
                } else if (actionClass instanceof PushVlanActionCase) {
                    listActionInfo.add(new ActionPushVlan(actionKey));
                } else if (actionClass instanceof SetFieldCase) {
                    if (((SetFieldCase) actionClass).getSetField().getVlanMatch() != null) {
                        int vlanVid = ((SetFieldCase) actionClass).getSetField().getVlanMatch()
                            .getVlanId().getVlanId().getValue().toJava();
                        listActionInfo.add(new ActionSetFieldVlanVid(actionKey, vlanVid));
                    }
                } else if (actionClass instanceof NxActionResubmitRpcAddGroupCase) {
                    Short tableId = ((NxActionResubmitRpcAddGroupCase) actionClass).getNxResubmit().getTable().toJava();
                    listActionInfo.add(new ActionNxResubmit(actionKey, tableId));
                } else if (actionClass instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                    NxRegLoad nxRegLoad =
                        ((NxActionRegLoadNodesNodeTableFlowApplyActionsCase) actionClass).getNxRegLoad();
                    listActionInfo.add(new ActionRegLoad(actionKey, NxmNxReg6.class,
                        nxRegLoad.getDst().getStart().toJava(), nxRegLoad.getDst().getEnd().toJava(),
                        nxRegLoad.getValue().longValue()));
                }
            }
            return listActionInfo;
        } catch (InterruptedException | ExecutionException | NullPointerException e) {
            LOG.error("Exception when egress actions for interface {} isTunnel {} vpnId {} ipPrefix {}", ifName,
                    isTunnelInterface, vpnId, destIpPrefix, e);
        }
        LOG.warn("Exception when egress actions for interface {}", ifName);
        return Collections.emptyList();
    }

    @Nullable
    protected String getTunnelInterfaceName(Uint64 srcDpId, Uint64 dstDpId) {
        Class<? extends TunnelTypeBase> tunType = getReqTunType(getReqTransType().toUpperCase(Locale.getDefault()));
        Future<RpcResult<GetTunnelInterfaceNameOutput>> result;
        try {
            result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                .setSourceDpid(srcDpId)
                .setDestinationDpid(dstDpId)
                .setTunnelType(tunType)
                .build());
            RpcResult<GetTunnelInterfaceNameOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel between {} and  {}", srcDpId, dstDpId, e);
        }
        return null;
    }

    @Nullable
    protected String getTunnelInterfaceName(Uint64 srcDpId, org.opendaylight.yang.gen.v1.urn.ietf.params
        .xml.ns.yang.ietf.inet.types.rev130715.IpAddress dstIp, Class<? extends TunnelTypeBase> tunnelType) {
        Future<RpcResult<GetInternalOrExternalInterfaceNameOutput>> result;
        try {
            LOG.debug("Trying to fetch tunnel interface name for source dpn {} destIp {} tunType {}", srcDpId,
                    dstIp.stringValue(), tunnelType.getName());
            result = itmManager.getInternalOrExternalInterfaceName(new GetInternalOrExternalInterfaceNameInputBuilder()
                .setSourceDpid(srcDpId)
                .setDestinationIp(dstIp)
                .setTunnelType(tunnelType)
                .build());
            RpcResult<GetInternalOrExternalInterfaceNameOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceName returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting tunnel interface Id for tunnel between {} and  {}", srcDpId, dstIp, e);
        }
        return null;
    }


    public long getLocalNextHopGroup(Uint32 vpnId,
            String ipNextHopAddress) {
        long groupId = createNextHopPointer(getNextHopKey(vpnId, ipNextHopAddress));
        if (groupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate groupId for vpnId {} , prefix {}", vpnId, ipNextHopAddress);
        }
        return groupId;
    }

    public long getLocalSelectGroup(Uint32 vpnId,
            String ipNextHopAddress) {
        long groupId = createNextHopPointer(getLocalSelectGroupKey(vpnId, ipNextHopAddress));
        if (groupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate groupId for vpnId {} , prefix {}", vpnId, ipNextHopAddress);
        }
        return groupId;
    }

    public long createLocalNextHop(Uint32 vpnId, Uint64 dpnId, String ifName,
                                   String primaryIpAddress, String currDestIpPrefix,
                                   String gwMacAddress, Uint32 parentVpnId) {
        //For VPN Imported routes, getting VPN Instance name using parentVpnId
        String vpnName = parentVpnId != null ? fibUtil.getVpnNameFromId(parentVpnId) : fibUtil.getVpnNameFromId(vpnId);
        if (vpnName == null) {
            return 0;
        }
        String macAddress = fibUtil.getMacAddressFromPrefix(ifName, vpnName, primaryIpAddress);

        long groupId = createNextHopPointer(getNextHopKey(vpnId, primaryIpAddress));
        if (groupId == 0) {
            LOG.error("Unable to allocate groupId for vpnId {} , IntfName {}, primaryIpAddress {} curIpPrefix {}",
                    vpnId, ifName, primaryIpAddress, currDestIpPrefix);
            return groupId;
        }
        String nextHopLockStr = vpnId + primaryIpAddress;
        String jobKey = FibUtil.getCreateLocalNextHopJobKey(vpnId, dpnId, currDestIpPrefix);
        jobCoordinator.enqueueJob(jobKey, () -> {
            try {
                if (FibUtil.lockCluster(lockManager, nextHopLockStr, WAIT_TIME_TO_ACQUIRE_LOCK)) {
                    VpnNexthop nexthop = getVpnNexthop(vpnId, primaryIpAddress);
                    LOG.trace("nexthop: {} retrieved for vpnId {}, prefix {}, ifName {} on dpn {}", nexthop, vpnId,
                            primaryIpAddress, ifName, dpnId);
                    if (nexthop == null) {
                        String encMacAddress = macAddress == null
                                ? fibUtil.getMacAddressFromPrefix(ifName, vpnName, primaryIpAddress) : macAddress;
                        List<ActionInfo> listActionInfo = new ArrayList<>();
                        int actionKey = 0;
                        // MAC re-write
                        if (encMacAddress != null) {
                            if (gwMacAddress != null) {
                                LOG.trace("The Local NextHop Group Source Mac {} for VpnInterface {} on VPN {}",
                                        gwMacAddress, ifName, vpnId);
                                listActionInfo.add(new ActionSetFieldEthernetSource(actionKey++,
                                        new MacAddress(gwMacAddress)));
                            }
                            listActionInfo.add(new ActionSetFieldEthernetDestination(actionKey++,
                                    new MacAddress(encMacAddress)));
                            // listActionInfo.add(0, new ActionPopMpls());
                        } else {
                            LOG.error("mac address for new local nexthop group {} is null for vpnId {}, prefix {}, "
                                    + "ifName {} on dpn {}", groupId, vpnId, primaryIpAddress, ifName, dpnId);
                        }
                        List<ActionInfo> nhActionInfoList = getEgressActionsForInterface(ifName, actionKey, false,
                                vpnId, currDestIpPrefix);
                        if (nhActionInfoList.isEmpty()) {
                            LOG.error("createLocalNextHop: Skipping, Empty list of egress actions received for "
                                    + "interface {} on dpn {} for vpn {} prefix {}", ifName, dpnId, vpnId,
                                    currDestIpPrefix);
                        }
                        listActionInfo.addAll(nhActionInfoList);
                        BucketInfo bucket = new BucketInfo(listActionInfo);
                        List<BucketInfo> listBucketInfo = new ArrayList<>();
                        listBucketInfo.add(bucket);
                        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, primaryIpAddress,
                                GroupTypes.GroupAll, listBucketInfo);
                        LOG.trace("Install LNH Group: id {}, mac address {}, interface {} for prefix {}", groupId,
                                encMacAddress, ifName, primaryIpAddress);
                        //Try to install group directly on the DPN bypassing the FRM, in order to avoid waiting for the
                        // group to get installed before programming the flows
                        installGroupOnDpn(groupId, dpnId, primaryIpAddress, listBucketInfo,
                                getNextHopKey(vpnId, primaryIpAddress), GroupTypes.GroupAll);
                        // install Group
                        mdsalApiManager.syncInstallGroup(groupEntity);
                        // update MD-SAL DS
                        addVpnNexthopToDS(dpnId, vpnId, primaryIpAddress, currDestIpPrefix, groupId);

                    } else {
                        // Ignore adding new prefix , if it already exists
                        Map<IpAdjacenciesKey, IpAdjacencies> keyIpAdjacenciesMap = nexthop.getIpAdjacencies();
                        IpAdjacencies prefix = new IpAdjacenciesBuilder().setIpAdjacency(currDestIpPrefix).build();
                        if (keyIpAdjacenciesMap != null && keyIpAdjacenciesMap.values().contains(prefix)) {
                            LOG.trace("Prefix {} is already present in l3nextHop {} ", currDestIpPrefix, nexthop);
                        } else {
                            IpAdjacenciesBuilder ipPrefixesBuilder =
                                    new IpAdjacenciesBuilder().withKey(new IpAdjacenciesKey(currDestIpPrefix));
                            LOG.trace("Updating prefix {} to vpnNextHop {} Operational DS", currDestIpPrefix, nexthop);
                            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                    getVpnNextHopIpPrefixIdentifier(vpnId, primaryIpAddress, currDestIpPrefix),
                                    ipPrefixesBuilder.build());
                        }
                    }
                }
            } finally {
                FibUtil.unlockCluster(lockManager, nextHopLockStr);
            }
            return Collections.emptyList();
        });
        return groupId;
    }

    private void installGroupOnDpn(long groupId, Uint64 dpnId, String groupName, List<BucketInfo> bucketsInfo,
                                     String nextHopKey, GroupTypes groupType) {
        NodeRef nodeRef = FibUtil.buildNodeRef(dpnId);
        Buckets buckets = FibUtil.buildBuckets(bucketsInfo);
        GroupRef groupRef = new GroupRef(FibUtil.buildGroupInstanceIdentifier(groupId, dpnId));
        AddGroupInput input = new AddGroupInputBuilder().setNode(nodeRef).setGroupId(new GroupId(groupId))
                .setBuckets(buckets).setGroupRef(groupRef).setGroupType(groupType)
                .setGroupName(groupName).build();
        Future<RpcResult<AddGroupOutput>> groupStats = salGroupService.addGroup(input);
        RpcResult<AddGroupOutput> rpcResult = null;
        try {
            rpcResult = groupStats.get(WAIT_TIME_FOR_SYNC_INSTALL, TimeUnit.MILLISECONDS);
            if (rpcResult != null && rpcResult.isSuccessful()) {
                LOG.info("installGroupOnDpn: Group {} with key {} has been successfully installed directly on dpn {}.",
                        groupId, nextHopKey, dpnId);
            } else {
                LOG.error("installGroupOnDpn: Unable to install group {} with key {} directly on dpn {} due to {}.",
                        groupId, nextHopKey, dpnId, rpcResult != null ? rpcResult.getErrors() : null);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("installGroupOnDpn: Error while installing group {} directly on dpn {}", groupId, dpnId);
        } catch (TimeoutException e) {
            LOG.error("installGroupOnDpn: Group {} installation on dpn {} timed out.", groupId, dpnId);
        }
    }

    protected void addVpnNexthopToDS(Uint64 dpnId, Uint32 vpnId, String primaryIpAddr,
                                     String currIpAddr, long egressPointer) {
        InstanceIdentifierBuilder<VpnNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
            .child(VpnNexthops.class, new VpnNexthopsKey(vpnId));

        List<IpAdjacencies> ipPrefixesList = new ArrayList<>();
        IpAdjacencies prefix = new IpAdjacenciesBuilder().setIpAdjacency(currIpAddr).build();
        ipPrefixesList.add(prefix);
        // Add nexthop to vpn node
        VpnNexthop nh = new VpnNexthopBuilder()
            .withKey(new VpnNexthopKey(primaryIpAddr))
            .setDpnId(dpnId)
            .setIpAdjacencies(ipPrefixesList)
            .setEgressPointer(egressPointer).build();

        InstanceIdentifier<VpnNexthop> id1 = idBuilder
            .child(VpnNexthop.class, new VpnNexthopKey(primaryIpAddr)).build();
        LOG.trace("Adding vpnnextHop {} to Operational DS", nh);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, id1, nh);

    }

    protected InstanceIdentifier<IpAdjacencies> getVpnNextHopIpPrefixIdentifier(Uint32 vpnId, String primaryIpAddress,
                                                                                String ipPrefix) {
        InstanceIdentifier<IpAdjacencies> id = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
                .child(VpnNexthop.class, new VpnNexthopKey(primaryIpAddress))
                .child(IpAdjacencies.class, new IpAdjacenciesKey(ipPrefix)).build();
        return id;
    }

    @Nullable
    protected VpnNexthop getVpnNexthop(Uint32 vpnId, String ipAddress) {

        // check if vpn node is there
        InstanceIdentifierBuilder<VpnNexthops> idBuilder =
            InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class,
                new VpnNexthopsKey(vpnId));
        InstanceIdentifier<VpnNexthops> id = idBuilder.build();
        Optional<VpnNexthops> vpnNexthops;
        try {
            vpnNexthops = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getVpnNexthop: Exception while reading VpnNexthops DS for the address {} vpn {}", ipAddress,
                    vpnId, e);
            return null;
        }
        if (vpnNexthops.isPresent()) {
            // get keyVpnNexthopMap list for vpn
            Map<VpnNexthopKey, VpnNexthop> keyVpnNexthopMap = vpnNexthops.get().nonnullVpnNexthop();
            for (VpnNexthop nexthop : keyVpnNexthopMap.values()) {
                if (Objects.equals(nexthop.getIpAddress(), ipAddress)) {
                    // return nexthop
                    LOG.trace("VpnNextHop : {}", nexthop);
                    return nexthop;
                }
            }
            // return null if not found
        }
        return null;
    }

    @Nullable
    public AdjacencyResult getRemoteNextHopPointer(Uint64 remoteDpnId, Uint32 vpnId, String prefixIp,
            @Nullable String nextHopIp, Class<? extends TunnelTypeBase> tunnelType) {
        String egressIfName = null;
        LOG.trace("getRemoteNextHopPointer: input [remoteDpnId {}, vpnId {}, prefixIp {}, nextHopIp {} ]", remoteDpnId,
            vpnId, prefixIp, nextHopIp);

        Class<? extends InterfaceType> egressIfType;
        ElanInstance elanInstance = getElanInstanceForPrefix(vpnId, prefixIp);
        if (elanInstance != null) {
            egressIfType = getInterfaceType(elanInstance);
        } else {
            LOG.warn("Failed to determine network type for prefixIp {} using tunnel", prefixIp);
            egressIfType = Tunnel.class;
        }

        if (Tunnel.class.equals(egressIfType)) {
            egressIfName = getTunnelRemoteNextHopPointer(remoteDpnId, nextHopIp, tunnelType);
        } else {
            egressIfName = getExtPortRemoteNextHopPointer(remoteDpnId, elanInstance);
        }

        LOG.trace("NextHop pointer for prefixIp {} vpnId {} dpnId {} is {}", prefixIp, vpnId, remoteDpnId,
            egressIfName);
        return egressIfName != null ? new AdjacencyResult(egressIfName, egressIfType, nextHopIp,
                prefixIp) : null;
    }

    private void removeVpnNexthopFromDS(Uint32 vpnId, String ipPrefix) {

        InstanceIdentifierBuilder<VpnNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
            .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
            .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix));
        InstanceIdentifier<VpnNexthop> id = idBuilder.build();
        // remove from DS
        LOG.trace("Removing vpn next hop from datastore : {}", id);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
    }

    public void removeLocalNextHop(Uint64 dpnId, Uint32 vpnId, String primaryIpAddress, String currDestIpPrefix) {
        String nextHopLockStr = vpnId + primaryIpAddress;
        try {
            if (FibUtil.lockCluster(lockManager, nextHopLockStr, WAIT_TIME_TO_ACQUIRE_LOCK)) {
                VpnNexthop nh = getVpnNexthop(vpnId, primaryIpAddress);
                if (nh != null) {
                    List<IpAdjacencies> prefixesList = new ArrayList<IpAdjacencies>(nh.nonnullIpAdjacencies().values());
                    IpAdjacencies prefix = new IpAdjacenciesBuilder().setIpAdjacency(currDestIpPrefix).build();
                    prefixesList.remove(prefix);
                    if (prefixesList.isEmpty()) { //remove the group only if there are no more flows using this group
                        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, nh.getEgressPointer().toJava(),
                                primaryIpAddress, GroupTypes.GroupAll, Collections.emptyList());
                        // remove Group ...
                        mdsalApiManager.removeGroup(groupEntity);
                        //update MD-SAL DS
                        removeVpnNexthopFromDS(vpnId, primaryIpAddress);
                        //release groupId
                        removeNextHopPointer(getNextHopKey(vpnId, primaryIpAddress));
                        LOG.debug("Local Next hop {} for {} {} on dpn {} successfully deleted",
                                nh.getEgressPointer(), vpnId, primaryIpAddress, dpnId);
                    } else {
                        //remove the currIpPrefx from IpPrefixList of the vpnNexthop
                        LOG.trace("Removing the prefix {} from vpnNextHop {} Operational DS", currDestIpPrefix, nh);
                        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                getVpnNextHopIpPrefixIdentifier(vpnId, primaryIpAddress, currDestIpPrefix));
                    }
                } else {
                    //throw error
                    LOG.error("Local NextHop for VpnId {} curIpPrefix {} on dpn {} primaryIpAddress {} not deleted",
                            vpnId, currDestIpPrefix, dpnId, primaryIpAddress);
                }
            }
        } finally {
            FibUtil.unlockCluster(lockManager, nextHopLockStr);
        }
    }

    public void setConfTransType(String service, String transportType) {

        if (!service.equalsIgnoreCase("L3VPN")) {
            LOG.error("Incorrect service {} provided for setting the transport type.", service);
            return;
        }

        L3VPNTransportTypes transType = L3VPNTransportTypes.validateTransportType(transportType
                .toUpperCase(Locale.getDefault()));

        if (transType != L3VPNTransportTypes.Invalid) {
            configuredTransportTypeL3VPN = transType;
        }
    }

    public void writeConfTransTypeConfigDS() {
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, getConfTransportTypeIdentifier(),
            createConfTransportType(configuredTransportTypeL3VPN.getTransportType()));
    }

    public L3VPNTransportTypes getConfiguredTransportTypeL3VPN() {
        return this.configuredTransportTypeL3VPN;
    }

    public String getReqTransType() {
        if (configuredTransportTypeL3VPN == L3VPNTransportTypes.Invalid) {
            /*
             * Restart scenario, Read from the ConfigDS.
             * if the value is Unset, cache value as VxLAN.
             */
            LOG.trace("configureTransportType is not yet set.");
            Optional<ConfTransportTypeL3vpn> configuredTransTypeFromConfig;
            try {
                configuredTransTypeFromConfig = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, getConfTransportTypeIdentifier());
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("getReqTransType: Exception while reading ConfTransportTypeL3vpn DS", e);
                return null;
            }
            if (configuredTransTypeFromConfig.isPresent()) {
                if (TunnelTypeGre.class.equals(configuredTransTypeFromConfig.get().getTransportType())) {
                    configuredTransportTypeL3VPN = L3VPNTransportTypes.GRE;
                } else {
                    configuredTransportTypeL3VPN = L3VPNTransportTypes.VxLAN;
                }
                LOG.trace("configuredTransportType set from config DS to {}",
                    getConfiguredTransportTypeL3VPN().getTransportType());
            } else {
                setConfTransType("L3VPN", L3VPNTransportTypes.VxLAN.getTransportType());
                LOG.trace("configuredTransportType is not set in the Config DS. VxLAN as default will be used.");
            }
        } else {
            LOG.trace("configuredTransportType is set as {}", getConfiguredTransportTypeL3VPN().getTransportType());
        }
        return getConfiguredTransportTypeL3VPN().getTransportType();
    }

    public InstanceIdentifier<ConfTransportTypeL3vpn> getConfTransportTypeIdentifier() {
        return InstanceIdentifier.builder(ConfTransportTypeL3vpn.class).build();
    }

    private ConfTransportTypeL3vpn createConfTransportType(String type) {
        ConfTransportTypeL3vpn confTransType;
        switch (type) {
            case ITMConstants.TUNNEL_TYPE_GRE:
                confTransType = new ConfTransportTypeL3vpnBuilder().setTransportType(TunnelTypeGre.class).build();
                LOG.trace("Setting the confTransportType to GRE.");
                break;
            case ITMConstants.TUNNEL_TYPE_VXLAN:
                confTransType = new ConfTransportTypeL3vpnBuilder().setTransportType(TunnelTypeVxlan.class).build();
                LOG.trace("Setting the confTransportType to VxLAN.");
                break;
            default:
                LOG.trace("Invalid transport type {} passed to Config DS ", type);
                confTransType = null;
                break;
        }
        return confTransType;
    }

    public Class<? extends TunnelTypeBase> getReqTunType(String transportType) {
        switch (transportType) {
            case "VXLAN":
                return TunnelTypeVxlan.class;
            case "GRE":
                return TunnelTypeGre.class;
            default:
                return TunnelTypeMplsOverGre.class;
        }
    }

    public String getTransportTypeStr(String tunType) {
        if (tunType.equals(TunnelTypeVxlan.class.toString())) {
            return ITMConstants.TUNNEL_TYPE_VXLAN;
        } else if (tunType.equals(TunnelTypeGre.class.toString())) {
            return ITMConstants.TUNNEL_TYPE_GRE;
        } else if (tunType.equals(TunnelTypeMplsOverGre.class.toString())) {
            return ITMConstants.TUNNEL_TYPE_MPLSoGRE;
        } else {
            return ITMConstants.TUNNEL_TYPE_INVALID;
        }
    }

    @Override
    @PreDestroy
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Nullable
    private String getTunnelRemoteNextHopPointer(Uint64 remoteDpnId, String nextHopIp,
                                                 Class<? extends TunnelTypeBase> tunnelType) {
        if (nextHopIp != null && !nextHopIp.isEmpty()) {
            try {
                // here use the config for tunnel type param
                return getTunnelInterfaceName(remoteDpnId,
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder
                        .getDefaultInstance(nextHopIp), tunnelType);
            } catch (Exception ex) {
                LOG.error("Error while retrieving nexthop pointer for nexthop {} remoteDpn {}",
                        nextHopIp, remoteDpnId, ex);
            }
        }

        return null;
    }

    private String getExtPortRemoteNextHopPointer(Uint64 remoteDpnId, ElanInstance elanInstance) {
        return elanService.getExternalElanInterface(elanInstance.getElanInstanceName(), remoteDpnId);
    }

    /**
     * Get the interface type associated with the type of ELAN used for routing
     * traffic to/from remote compute nodes.
     *
     * @param elanInstance The elan instance
     * @return L2vlan for flat/VLAN network type and Tunnel otherwise
     */
    private Class<? extends InterfaceType> getInterfaceType(ElanInstance elanInstance) {
        Class<? extends SegmentTypeBase> segmentType = elanInstance.getSegmentType();
        if (SegmentTypeFlat.class.equals(segmentType) || SegmentTypeVlan.class.equals(segmentType)) {
            return L2vlan.class;
        }

        return Tunnel.class;
    }

    private ElanInstance getElanInstanceForPrefix(Uint32 vpnId, String prefixIp) {
        ElanInstance elanInstance = null;
        Prefixes prefix = fibUtil.getPrefixToInterface(vpnId, prefixIp);
        if (prefix != null) {
            if (prefix.getNetworkId() != null) {
                elanInstance = elanService.getElanInstance(prefix.getNetworkId().getValue());
            }
        }

        return elanInstance;
    }

    static class AdjacencyResult {
        private final String interfaceName;
        private final Class<? extends InterfaceType> interfaceType;
        private final String nextHopIp;
        private final String prefix;

        AdjacencyResult(String interfaceName, Class<? extends InterfaceType> interfaceType, String nextHopIp,
                        String prefix) {
            this.interfaceName = interfaceName;
            this.interfaceType = interfaceType;
            this.nextHopIp = nextHopIp;
            this.prefix = prefix;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public Class<? extends InterfaceType> getInterfaceType() {
            return interfaceType;
        }

        public String getNextHopIp() {
            return nextHopIp;
        }

        public String getPrefix() {
            return prefix;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (interfaceName == null ? 0 : interfaceName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (getClass() != obj.getClass()) {
                return false;
            } else {
                AdjacencyResult other = (AdjacencyResult) obj;
                return interfaceName.equals(other.interfaceName);
            }
        }
    }

    protected long setupLoadBalancingNextHop(Uint32 parentVpnId, Uint64 dpnId,
            String destPrefix, List<BucketInfo> localBucketInfo, List<BucketInfo> remoteBucketInfo) {
        long remoteGroupId = createNextHopPointer(getRemoteSelectGroupKey(parentVpnId, destPrefix));
        if (remoteGroupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate/retrieve remote groupId for vpnId {} , prefix {}", parentVpnId, destPrefix);
            return remoteGroupId;
        }
        long localGroupId =  createNextHopPointer(getLocalSelectGroupKey(parentVpnId, destPrefix));
        if (localGroupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate/retrieve local groupId for vpnId {} , prefix {}",
                parentVpnId, destPrefix);
            return remoteGroupId;
        }
        List<BucketInfo> combinedBucketInfo = new ArrayList<>();
        combinedBucketInfo.addAll(localBucketInfo);
        combinedBucketInfo.addAll(remoteBucketInfo);
        GroupEntity remoteGroupEntity = MDSALUtil.buildGroupEntity(
                dpnId, remoteGroupId, destPrefix, GroupTypes.GroupSelect, combinedBucketInfo);
        GroupEntity localGroupEntity = MDSALUtil.buildGroupEntity(
                dpnId, localGroupId, destPrefix, GroupTypes.GroupSelect, localBucketInfo);
        String jobKey = FibUtil.getCreateLocalNextHopJobKey(parentVpnId, dpnId, destPrefix);
        jobCoordinator.enqueueJob(jobKey, () -> {
            mdsalApiManager.syncInstallGroup(remoteGroupEntity);
            if (!localBucketInfo.isEmpty()) {
                mdsalApiManager.syncInstallGroup(localGroupEntity);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished installing GroupEntity with jobCoordinator key {} remoteGroupEntity.groupId {}"
                        + "localGroupEntity.groupId {}  groupEntity.groupType {}", jobKey,
                        remoteGroupEntity.getGroupId(), localGroupEntity.getGroupId(),
                        remoteGroupEntity.getGroupType());
            }
            // Delete local group(if exists) if there is no local info.
            // Local group has to be deleted if all VMs in a compute is deleted.
            if (localBucketInfo.isEmpty()) {
                LOG.debug("Deleting local group {} since no local nhs present for "
                        + "prefix {}", localGroupEntity.getGroupId(), destPrefix);
                mdsalApiManager.syncRemoveGroup(localGroupEntity);
            }
            return Collections.emptyList();
        });
        return remoteGroupId;
    }

    protected void deleteLoadBalancingNextHop(Uint32 parentVpnId, Uint64 dpnId, String destPrefix) {
        long remoteGroupId = createNextHopPointer(getRemoteSelectGroupKey(parentVpnId, destPrefix));
        if (remoteGroupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate/retrieve remote groupId for vpnId {} , prefix {}", parentVpnId, destPrefix);
        }
        long localGroupId = createNextHopPointer(getLocalSelectGroupKey(parentVpnId, destPrefix));
        if (localGroupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate/retrieve local groupId for vpnId {} , prefix {}", parentVpnId, destPrefix);
        }
        GroupEntity remoteGroupEntity = MDSALUtil.buildGroupEntity(
                dpnId, remoteGroupId, destPrefix, GroupTypes.GroupSelect, Collections.emptyList());
        GroupEntity localGroupEntity = MDSALUtil.buildGroupEntity(
                dpnId, localGroupId, destPrefix, GroupTypes.GroupSelect, Collections.emptyList());
        String jobKey = FibUtil.getCreateLocalNextHopJobKey(parentVpnId, dpnId, destPrefix);
        jobCoordinator.enqueueJob(jobKey, () -> {
            mdsalApiManager.syncRemoveGroup(remoteGroupEntity);
            mdsalApiManager.syncRemoveGroup(localGroupEntity);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished removing GroupEntity with jobCoordinator key {} remoteGroupEntity.groupId {}"
                    + "localGroupEntity.groupId {}", jobKey, remoteGroupId, localGroupId);
            }
            return Collections.emptyList();
        });
    }

    long createNextHopGroups(Uint32 vpnId, String rd, Uint64 dpnId, VrfEntry vrfEntry,
            @Nullable Routes routes, List<Routes> vpnExtraRoutes) {
        List<BucketInfo> localBucketInfo = new ArrayList<>();
        List<Routes> clonedVpnExtraRoutes  = new ArrayList<>(vpnExtraRoutes);
        if (clonedVpnExtraRoutes.contains(routes)) {
            localBucketInfo.addAll(getBucketsForLocalNexthop(vpnId, dpnId, vrfEntry, routes));
            clonedVpnExtraRoutes.remove(routes);
        }
        List<BucketInfo> remoteBucketInfo =
            new ArrayList<>(getBucketsForRemoteNexthop(vpnId, dpnId, vrfEntry, rd, clonedVpnExtraRoutes));
        return setupLoadBalancingNextHop(vpnId, dpnId,
            vrfEntry.getDestPrefix(), localBucketInfo, remoteBucketInfo);
    }

    private List<BucketInfo> getBucketsForLocalNexthop(Uint32 vpnId, Uint64 dpnId,
            VrfEntry vrfEntry, Routes routes) {
        @Nullable List<String> nexthopIpList = routes.getNexthopIpList();
        if (LOG.isDebugEnabled()) {
            LOG.debug("NexthopManager.getBucketsForLocalNexthop invoked with vpnId {} dpnId {} "
                            + " vrfEntry.routePaths {}, routes.nexthopList {}", vpnId, dpnId, vrfEntry.getRoutePaths(),
                nexthopIpList);
        }
        List<BucketInfo> listBucketInfo = new CopyOnWriteArrayList<>();
        if (nexthopIpList != null) {
            nexthopIpList.parallelStream().forEach(nextHopIp -> {
                String localNextHopIP;
                if (isIpv4Address(nextHopIp)) {
                    localNextHopIP = nextHopIp + NwConstants.IPV4PREFIX;
                } else {
                    localNextHopIP = nextHopIp + NwConstants.IPV6PREFIX;
                }
                Prefixes localNextHopInfo = fibUtil.getPrefixToInterface(vpnId, localNextHopIP);
                if (localNextHopInfo != null) {
                    long groupId = getLocalNextHopGroup(vpnId, localNextHopIP);
                    if (groupId == FibConstants.INVALID_GROUP_ID) {
                        LOG.error("Unable to allocate groupId for vpnId {} , prefix {} , interface {}", vpnId,
                            vrfEntry.getDestPrefix(), localNextHopInfo.getVpnInterfaceName());
                        return;
                    }
                    List<ActionInfo> actionsInfos =
                        Collections.singletonList(new ActionGroup(groupId));
                    BucketInfo bucket = new BucketInfo(actionsInfos);
                    bucket.setWeight(1);
                    listBucketInfo.add(bucket);
                }
            });
        }
        LOG.trace("LOCAL: listbucket {}, vpnId {}, dpnId {}, routes {}", listBucketInfo, vpnId, dpnId, routes);
        return listBucketInfo;
    }

    private List<BucketInfo> getBucketsForRemoteNexthop(Uint32 vpnId, Uint64 dpnId, VrfEntry vrfEntry, String rd,
            List<Routes> vpnExtraRoutes) {
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        Map<String, List<ActionInfo>> egressActionMap = new HashMap<>();
        vpnExtraRoutes.stream().filter(vpnExtraRoute -> vpnExtraRoute.getNexthopIpList() != null).forEach(
            vpnExtraRoute -> vpnExtraRoute.getNexthopIpList().forEach(nextHopIp -> {
                String nextHopPrefixIp;
                if (isIpv4Address(nextHopIp)) {
                    nextHopPrefixIp = nextHopIp + NwConstants.IPV4PREFIX;
                } else {
                    nextHopPrefixIp = nextHopIp + NwConstants.IPV6PREFIX;
                }
                List<String> tepIpAddresses = fibUtil.getNextHopAddresses(rd, nextHopPrefixIp);
                if (tepIpAddresses.isEmpty()) {
                    return;
                }
                // There would be only one nexthop address for a VM ip which would be the tep Ip
                String tepIp = tepIpAddresses.get(0);
                AdjacencyResult adjacencyResult = getRemoteNextHopPointer(dpnId, vpnId,
                    vrfEntry.getDestPrefix(), tepIp, TunnelTypeVxlan.class);
                if (adjacencyResult == null) {
                    return;
                }
                String egressInterface = adjacencyResult.getInterfaceName();
                if (!FibUtil.isTunnelInterface(adjacencyResult)) {
                    return;
                }
                Class<? extends TunnelTypeBase> tunnelType =
                    VpnExtraRouteHelper.getTunnelType(itmManager, egressInterface);
                StateTunnelList ifState = null;
                try {
                    ifState = fibUtil.getTunnelState(egressInterface);
                    if (ifState == null || ifState.getOperState() != TunnelOperStatus.Up) {
                        LOG.trace("Tunnel is not up for interface {}", egressInterface);
                        return;
                    }
                } catch (ReadFailedException e) {
                    LOG.error("getBucketsForRemoteNexthop: error in fetching tunnel state for interface {}",
                        egressInterface, e);
                    return;
                }
                if (!TunnelTypeVxlan.class.equals(tunnelType)) {
                    return;
                }
                Uint32 label = FibUtil.getLabelFromRoutePaths(vrfEntry).get();
                Prefixes prefixInfo = fibUtil.getPrefixToInterface(vpnId, nextHopPrefixIp);
                if (prefixInfo == null) {
                    LOG.error("No prefix info found for prefix {} in rd {} for VPN {}", nextHopPrefixIp, rd,
                        vpnId);
                    return;
                }
                Uint32 tunnelId;
                if (FibUtil.isVxlanNetwork(prefixInfo.getNetworkType())) {
                    tunnelId = prefixInfo.getSegmentationId();
                } else {
                    LOG.warn("Network is not of type VXLAN for prefix {}."
                        + "Going with default Lport Tag.", prefixInfo.toString());
                    tunnelId = label;
                }
                List<ActionInfo> actionInfos = new ArrayList<>();
                actionInfos.add(new ActionSetFieldTunnelId(Uint64.valueOf(tunnelId.longValue())));
                String ifName = prefixInfo.getVpnInterfaceName();
                String vpnName = fibUtil.getVpnNameFromId(vpnId);
                if (vpnName == null) {
                    return;
                }
                String macAddress = fibUtil.getMacAddressFromPrefix(ifName, vpnName, nextHopPrefixIp);
                actionInfos.add(new ActionSetFieldEthernetDestination(actionInfos.size(),
                    new MacAddress(macAddress)));
                List<ActionInfo> egressActions;
                if (egressActionMap.containsKey(egressInterface)) {
                    egressActions = egressActionMap.get(egressInterface);
                } else {
                    egressActions = getEgressActionsForInterface(egressInterface, actionInfos.size(),
                            true, vpnId, vrfEntry.getDestPrefix());
                    if (egressActions.isEmpty()) {
                        LOG.error("Skipping getBucketsForRemoteNexthop: Empty list of egress actions received for "
                                        + "interface {} on dpn {} for vpn {} prefix {} nextHop {}", ifName, dpnId,
                                vpnId, vrfEntry.getDestPrefix(), nextHopPrefixIp);
                    }
                    egressActionMap.put(egressInterface, egressActions);
                }
                if (egressActions.isEmpty()) {
                    LOG.error("Failed to retrieve egress action for prefix {} route-paths {}"
                            + " interface {}." + " Aborting remote FIB entry creation.",
                        vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(), egressInterface);
                }
                actionInfos.addAll(egressActions);
                BucketInfo bucket = new BucketInfo(actionInfos);
                bucket.setWeight(1);
                listBucketInfo.add(bucket);
            }));
        LOG.trace("LOCAL: listbucket {}, rd {}, dpnId {}, routes {}", listBucketInfo, rd, dpnId, vpnExtraRoutes);
        return listBucketInfo;
    }

    public void createDcGwLoadBalancingGroup(Uint64 dpnId, String destinationIp,
                                             Class<? extends TunnelTypeBase> tunnelType) {
        jobCoordinator.enqueueJob(FibHelper.getJobKeyForDcGwLoadBalancingGroup(dpnId), () -> {
            List<ListenableFuture<?>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operationalTx -> {
                synchronized (getDcGateWaySyncKey(destinationIp)) {
                    FibUtil.addL3vpnDcGateWay(destinationIp, operationalTx);
                }
                futures.add(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, configTx -> {
                    List<String> availableDcGws = getDcGwIps();
                    requireNonNull(availableDcGws, "There are no dc-gws present");
                    int noOfDcGws = availableDcGws.size();
                    if (noOfDcGws == 1) {
                        LOG.trace("There are no enough DC GateWays {} present to program LB group", availableDcGws);
                        return;
                    }
                    if (availableDcGws.contains(destinationIp)) {
                        availableDcGws.remove(destinationIp);
                    }
                    availableDcGws.forEach(dcGwIp -> {
                        List<String> dcGws = Arrays.asList(dcGwIp, destinationIp);
                        Collections.sort(dcGws);
                        String groupIdKey = FibUtil.getGreLbGroupKey(dcGws);
                        Long groupId = createNextHopPointer(groupIdKey);
                        List<Bucket> listBucket = new ArrayList<>();
                        for (int index = 0; index < dcGws.size(); index++) {
                            if (isTunnelUp(dcGws.get(index), dpnId, tunnelType)) {
                                listBucket.add(buildBucketForDcGwLbGroup(dcGws.get(index),
                                        dpnId, index, tunnelType, true));
                            }
                        }
                        Group group = MDSALUtil.buildGroup(groupId, groupIdKey, GroupTypes.GroupSelect,
                                        MDSALUtil.buildBucketLists(listBucket));
                        mdsalApiManager.addGroup(configTx, dpnId, group);
                        FibUtil.updateLbGroupInfo(dpnId, groupIdKey, groupId.toString(), operationalTx);
                        LOG.trace("LB group {} towards DC-GW installed on dpn {}. Group - {}",
                                groupIdKey, dpnId, group);
                    });
                }));
            }));
            return futures;
        }, RETRY_COUNT);
    }

    private String getDcGateWaySyncKey(String destinationIp) {
        String mutex = new StringBuilder().append("L3vpncDcGateWay").append(destinationIp).toString();
        return mutex.intern();
    }

    private List<String> getDcGwIps() {
        InstanceIdentifier<DcGatewayIpList> dcGatewayIpListid =
                InstanceIdentifier.builder(DcGatewayIpList.class).build();
        DcGatewayIpList dcGatewayIpListConfig;
        try {
            dcGatewayIpListConfig = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, dcGatewayIpListid).orElse(null);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getDcGwIps: Exception while reading DcGatewayIpList DS", e);
            return Collections.emptyList();
        }
        if (dcGatewayIpListConfig == null) {
            return Collections.emptyList();
        }
        return new ArrayList<DcGatewayIp>(dcGatewayIpListConfig.nonnullDcGatewayIp().values())
                .stream()
                .filter(dcGwIp -> dcGwIp.getTunnnelType().equals(TunnelTypeMplsOverGre.class))
                .map(dcGwIp -> dcGwIp.getIpAddress().stringValue()).sorted()
                .collect(toList());
    }

    private boolean isTunnelUp(String dcGwIp, Uint64 dpnId, Class<? extends TunnelTypeBase> tunnelType) {
        String tunnelName = getTunnelRemoteNextHopPointer(dpnId, dcGwIp, tunnelType);
        if (tunnelName != null) {
            InstanceIdentifier<StateTunnelList> tunnelStateId =
                    InstanceIdentifier.builder(TunnelsState.class).child(
                            StateTunnelList.class, new StateTunnelListKey(tunnelName)).build();
            try {
                return SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        tunnelStateId).map(StateTunnelList::getOperState)
                        .orElse(TunnelOperStatus.Down) == TunnelOperStatus.Up;
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("isTunnelUp: Exception while reading StateTunnelList DS for tunnel {} tunnelType {}",
                        tunnelName, tunnelType, e);
                return false;
            }
        }
        return false;
    }

    private List<Action> getEgressActions(String interfaceName, int actionKey) {
        List<Action> actions = Collections.emptyList();
        try {
            GetEgressActionsForInterfaceInputBuilder egressAction =
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(interfaceName).setActionKey(actionKey);
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    odlInterfaceRpcService.getEgressActionsForInterface(egressAction.build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}",
                        interfaceName, rpcResult.getErrors());
            } else {
                actions = new ArrayList<Action>(rpcResult.getResult().nonnullAction().values());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", interfaceName, e);
        }
        return actions;
    }

    /**
     * This method is invoked when the neighbor is removed from DS.
     * All the LB groups which point to the given destination will be deleted.
     */
    public void removeDcGwLoadBalancingGroup(Uint64 dpnId,
            String destinationIp) {
        jobCoordinator.enqueueJob(FibHelper.getJobKeyForDcGwLoadBalancingGroup(dpnId), () -> {
            List<String> availableDcGws = fibUtil.getL3VpnDcGateWays();
            if (availableDcGws.contains(destinationIp)) {
                availableDcGws.remove(destinationIp);
            }
            List<ListenableFuture<?>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, operationalTx -> {
                futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, configTx -> {
                    availableDcGws.forEach(dcGwIp -> {
                        List<String> dcGws = Arrays.asList(dcGwIp, destinationIp);
                        Collections.sort(dcGws);
                        String nextHopKey = FibUtil.getGreLbGroupKey(dcGws);
                        Optional<Nexthops> optionalNextHops = fibUtil.getNexthops(nextHopKey);
                        if (!optionalNextHops.isPresent()) {
                            return;
                        }
                        Nexthops nexthops = optionalNextHops.get();
                        final String groupId = nexthops.getGroupId();
                        final long groupIdValue = Long.parseLong(groupId);
                        Group group = MDSALUtil.buildGroup(groupIdValue, nextHopKey, GroupTypes.GroupSelect,
                                MDSALUtil.buildBucketLists(Collections.emptyList()));
                        LOG.trace("Removed LB group {} on dpn {}", group, dpnId);
                        try {
                            mdsalApiManager.removeGroup(configTx, dpnId, group);
                        } catch (ExecutionException | InterruptedException e) {
                            LOG.error("Group removal failed for group {} with exception", groupId, e);
                        }
                        removeNextHopPointer(nextHopKey);
                        FibUtil.removeOrUpdateNextHopInfo(dpnId, nextHopKey, groupId, nexthops, operationalTx);
                    });
                    synchronized (getDcGateWaySyncKey(destinationIp)) {
                        FibUtil.removeL3vpnDcGateWay(destinationIp, operationalTx);
                    }
                }));
            }));
            return futures;
        }, RETRY_COUNT);
    }

    /**
     * This method is invoked when the tunnel status is deleted.
     * All the buckets which point to given destination will be marked down.
     */
    public void updateDcGwLoadBalancingGroup(Uint64 dpnId, String destinationIp,
            boolean isTunnelUp, Class<? extends TunnelTypeBase> tunnelType) {
        jobCoordinator.enqueueJob(FibHelper.getJobKeyForDcGwLoadBalancingGroup(dpnId), () -> {
            List<String> availableDcGws = fibUtil.getL3VpnDcGateWays();
            if (availableDcGws.contains(destinationIp)) {
                availableDcGws.remove(destinationIp);
            }
            List<ListenableFuture<?>> futures = new ArrayList<>();
            futures.add(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, configTx -> {
                availableDcGws.forEach(dcGwIp -> {
                    List<String> dcGws = Arrays.asList(dcGwIp, destinationIp);
                    Collections.sort(dcGws);
                    String nextHopKey = FibUtil.getGreLbGroupKey(dcGws);
                    int bucketId = dcGws.indexOf(destinationIp);
                    Optional<Nexthops> optionalNextHops = fibUtil.getNexthops(nextHopKey);
                    if (!optionalNextHops.isPresent()) {
                        return;
                    }
                    Nexthops nexthops = optionalNextHops.get();
                    final String groupId = nexthops.getGroupId();
                    final long groupIdValue = Long.parseLong(groupId);
                    Bucket bucket = buildBucketForDcGwLbGroup(destinationIp, dpnId, bucketId, tunnelType, isTunnelUp);
                    LOG.trace("updated bucket {} to group {} on dpn {}.", bucket, groupId, dpnId);
                    try {
                        mdsalApiManager.addBucket(configTx, dpnId, groupIdValue, bucket);
                    } catch (ExecutionException | InterruptedException e) {
                        LOG.error("Bucket addition failed for bucket {} with exception", bucketId, e);
                    }
                });
            }));
            return futures;
        }, RETRY_COUNT);
    }

    private Bucket buildBucketForDcGwLbGroup(String ipAddress, Uint64 dpnId,
            int index, Class<? extends TunnelTypeBase> tunnelType, boolean isTunnelUp) {
        List<Action> listAction = new ArrayList<>();
        // ActionKey 0 goes to mpls label.
        int actionKey = 1;
        listAction.add(new ActionPushMpls().buildAction());
        listAction.add(new ActionRegMove(actionKey++, FibConstants.NXM_REG_MAPPING
                .get(index), 0, 19).buildAction());
        String tunnelInterfaceName = getTunnelInterfaceName(dpnId, IpAddressBuilder.getDefaultInstance(ipAddress),
            tunnelType);
        List<Action> egressActions = getEgressActions(tunnelInterfaceName, actionKey++);
        if (!egressActions.isEmpty()) {
            listAction.addAll(getEgressActions(tunnelInterfaceName, actionKey++));
        } else {
            // clear off actions if there is no egress actions.
            listAction = Collections.emptyList();
        }
        long watchPort = MDSALUtil.WATCH_PORT;
        if (!isTunnelUp) {
            watchPort = 0xFFFFFFFEL;
        }
        //OVS expects a non-zero weight value for load balancing to happen in select groups
        return MDSALUtil.buildBucket(listAction, SELECT_GROUP_WEIGHT, index,
                watchPort, MDSALUtil.WATCH_GROUP);
    }

    public void programDcGwLoadBalancingGroup(Uint64 dpnId, String destinationIp,
                                              int addRemoveOrUpdate, boolean isTunnelUp,
                                              Class<? extends TunnelTypeBase> tunnelType) {
        if (NwConstants.ADD_FLOW == addRemoveOrUpdate) {
            createDcGwLoadBalancingGroup(dpnId, destinationIp, tunnelType);
        } else if (NwConstants.DEL_FLOW == addRemoveOrUpdate) {
            removeDcGwLoadBalancingGroup(dpnId, destinationIp);
        } else if (NwConstants.MOD_FLOW == addRemoveOrUpdate) {
            updateDcGwLoadBalancingGroup(dpnId, destinationIp, isTunnelUp, tunnelType);
        }
    }
}
