/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import static org.opendaylight.genius.mdsalutil.NWUtil.isIpv4Address;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
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
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.L3VPNTransportTypes;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetInternalOrExternalInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetInternalOrExternalInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ConfTransportTypeL3vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.ConfTransportTypeL3vpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpid.l3vpn.lb.nexthops.DpnLbNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.l3vpn.lb.nexthops.Nexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NexthopManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NexthopManager.class);
    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalApiManager;
    private final OdlInterfaceRpcService interfaceManager;
    private final ItmRpcService itmManager;
    private final IdManagerService idManager;
    private final IElanService elanService;
    private final SalGroupService salGroupService;
    private static final String NEXTHOP_ID_POOL_NAME = "nextHopPointerPool";
    private static final long FIXED_DELAY_IN_MILLISECONDS = 4000;
    private static final long WAIT_TIME_FOR_SYNC_INSTALL = Long.getLong("wait.time.sync.install", 300L);
    private L3VPNTransportTypes configuredTransportTypeL3VPN = L3VPNTransportTypes.Invalid;

    /**
     * Provides nexthop functions.
     * Creates group ID pool
     *
     * @param dataBroker       - dataBroker reference
     * @param mdsalApiManager  - mdsalApiManager reference
     * @param idManager        - idManager reference
     * @param interfaceManager - interfaceManager reference
     * @param itmManager       - itmManager reference
     */
    @Inject
    public NexthopManager(final DataBroker dataBroker,
                          final IMdsalApiManager mdsalApiManager,
                          final IdManagerService idManager,
                          final OdlInterfaceRpcService interfaceManager,
                          final ItmRpcService itmManager,
                          final IElanService elanService,
                          final SalGroupService salGroupService) {
        this.dataBroker = dataBroker;
        this.mdsalApiManager = mdsalApiManager;
        this.idManager = idManager;
        this.interfaceManager = interfaceManager;
        this.itmManager = itmManager;
        this.elanService = elanService;
        this.salGroupService = salGroupService;
        createIdPool();
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME)
            .setLow(150000L)
            .setHigh(175000L)
            .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("Created IdPool for NextHopPointerPool");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for NextHopPointerPool", e);
        }
    }

    private BigInteger getDpnId(String ofPortId) {
        String[] fields = ofPortId.split(":");
        BigInteger dpn = new BigInteger(fields[1]);
        LOG.debug("DpnId: {}", dpn);
        return dpn;
    }

    private String getNextHopKey(long vpnId, String ipAddress) {
        return "nexthop." + vpnId + ipAddress;
    }

    private String getNextHopKey(String ifName, String ipAddress) {
        return "nexthop." + ifName + ipAddress;
    }

    public OdlInterfaceRpcService getInterfaceManager() {
        return interfaceManager;
    }

    protected long createNextHopPointer(String nexthopKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME).setIdKey(nexthopKey)
            .build();
        //TODO: Proper error handling once IdManager code is complete
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("", e);
        }
        return 0;
    }

    protected void removeNextHopPointer(String nexthopKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME)
            .setIdKey(nexthopKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get Unique Id for nexthopKey {} returned with Errors {}",
                        nexthopKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id for key {}", nexthopKey, e);
        }
    }

    protected List<ActionInfo> getEgressActionsForInterface(final String ifName, int actionKey) {
        List<ActionInfo> listActionInfo = new ArrayList<>();
        try {
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                interfaceManager.getEgressActionsForInterface(
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName).build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get egress actions for interface {} returned with Errors {}",
                    ifName, rpcResult.getErrors());
            } else {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actions =
                    rpcResult.getResult().getAction();
                for (Action action : actions) {
                    actionKey = action.getKey().getOrder() + actionKey++;
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
                                .getVlanId().getVlanId().getValue();
                            listActionInfo.add(new ActionSetFieldVlanVid(actionKey, vlanVid));
                        }
                    } else if (actionClass instanceof NxActionResubmitRpcAddGroupCase) {
                        Short tableId = ((NxActionResubmitRpcAddGroupCase) actionClass).getNxResubmit().getTable();
                        listActionInfo.add(new ActionNxResubmit(actionKey, tableId));
                    } else if (actionClass instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                        NxRegLoad nxRegLoad =
                            ((NxActionRegLoadNodesNodeTableFlowApplyActionsCase) actionClass).getNxRegLoad();
                        listActionInfo.add(new ActionRegLoad(actionKey, NxmNxReg6.class,
                            nxRegLoad.getDst().getStart(), nxRegLoad.getDst().getEnd(),
                            nxRegLoad.getValue().longValue()));
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listActionInfo;
    }

    protected String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
        Class<? extends TunnelTypeBase> tunType = getReqTunType(getReqTransType().toUpperCase());
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

    protected String getTunnelInterfaceName(BigInteger srcDpId, org.opendaylight.yang.gen.v1.urn.ietf.params
        .xml.ns.yang.ietf.inet.types.rev130715.IpAddress dstIp) {
        Class<? extends TunnelTypeBase> tunType = getReqTunType(getReqTransType().toUpperCase());
        Future<RpcResult<GetInternalOrExternalInterfaceNameOutput>> result;
        try {
            result = itmManager.getInternalOrExternalInterfaceName(new GetInternalOrExternalInterfaceNameInputBuilder()
                .setSourceDpid(srcDpId)
                .setDestinationIp(dstIp)
                .setTunnelType(tunType)
                .build());
            RpcResult<GetInternalOrExternalInterfaceNameOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceName returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel between {} and  {}", srcDpId, dstIp, e);
        }
        return null;
    }


    public long getLocalNextHopGroup(long vpnId,
            String ipNextHopAddress) {
        long groupId = createNextHopPointer(getNextHopKey(vpnId, ipNextHopAddress));
        if (groupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate groupId for vpnId {} , prefix {}", vpnId, ipNextHopAddress);
        }
        return groupId;
    }

    public long createLocalNextHop(long vpnId, BigInteger dpnId, String ifName,
            String ipNextHopAddress, String ipPrefixAddress, String gwMacAddress, String jobKey) {
        String macAddress = FibUtil.getMacAddressFromPrefix(dataBroker, ifName, ipPrefixAddress);
        String ipAddress = macAddress != null ? ipPrefixAddress : ipNextHopAddress;

        long groupId = createNextHopPointer(getNextHopKey(vpnId, ipAddress));
        if (groupId == 0) {
            LOG.error("Unable to allocate groupId for vpnId {} , prefix {}  IntfName {}, nextHopAddr {}",
                    vpnId, ipAddress, ifName, ipNextHopAddress);
            return groupId;
        }
        String nextHopLockStr = vpnId + ipAddress;
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();

        dataStoreCoordinator.enqueueJob(jobKey, () -> {
            synchronized (nextHopLockStr.intern()) {
                VpnNexthop nexthop = getVpnNexthop(vpnId, ipAddress);
                LOG.trace("nexthop: {} retrieved for vpnId {}, prefix {}, ifName {} on dpn {}", nexthop, vpnId,
                        ipAddress, ifName, dpnId);
                if (nexthop == null) {
                    String encMacAddress = macAddress == null
                            ? FibUtil.getMacAddressFromPrefix(dataBroker, ifName, ipAddress) : macAddress;
                    List<BucketInfo> listBucketInfo = new ArrayList<>();
                    List<ActionInfo> listActionInfo = new ArrayList<>();
                    int actionKey = 0;
                    // MAC re-write
                    if (encMacAddress != null) {
                        if (gwMacAddress != null) {
                            LOG.trace("The Local NextHop Group Source Mac {} for VpnInterface {} on VPN {}",
                                    gwMacAddress, ifName, vpnId);
                            listActionInfo
                                    .add(new ActionSetFieldEthernetSource(actionKey++, new MacAddress(gwMacAddress)));
                        }
                        listActionInfo
                                .add(new ActionSetFieldEthernetDestination(actionKey++, new MacAddress(encMacAddress)));
                        // listActionInfo.add(0, new ActionPopMpls());
                    } else {
                        // FIXME: Log message here.
                        LOG.debug("mac address for new local nexthop is null");
                    }
                    listActionInfo.addAll(getEgressActionsForInterface(ifName, actionKey));
                    BucketInfo bucket = new BucketInfo(listActionInfo);

                    listBucketInfo.add(bucket);
                    GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpnId, groupId, ipAddress, GroupTypes.GroupAll,
                            listBucketInfo);
                    LOG.trace("Install LNH Group: id {}, mac address {}, interface {} for prefix {}", groupId,
                            encMacAddress, ifName, ipAddress);
                    //Try to install group directly on the DPN bypassing the FRM, in order to avoid waiting for the
                    // group to get installed before programming the flows
                    installGroupOnDpn(groupId, dpnId, ipAddress, listBucketInfo, getNextHopKey(vpnId, ipAddress),
                            GroupTypes.GroupAll);
                    // install Group
                    mdsalApiManager.syncInstallGroup(groupEntity, FIXED_DELAY_IN_MILLISECONDS);
                    // update MD-SAL DS
                    addVpnNexthopToDS(dpnId, vpnId, ipAddress, groupId);

                } else {
                    // nexthop exists already; a new flow is going to point to
                    // it, increment the flowrefCount by 1
                    int flowrefCnt = nexthop.getFlowrefCount() + 1;
                    VpnNexthop nh = new VpnNexthopBuilder().setKey(new VpnNexthopKey(ipAddress))
                            .setFlowrefCount(flowrefCnt).build();
                    LOG.trace("Updating vpnnextHop {} for refCount {} to Operational DS", nh, flowrefCnt);
                    MDSALUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, getVpnNextHopIdentifier(vpnId,
                            ipAddress), nh);
                }
            }
            return Collections.emptyList();
        });
        return groupId;
    }

    private void installGroupOnDpn(long groupId, BigInteger dpnId, String groupName, List<BucketInfo> bucketsInfo,
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
            rpcResult = groupStats.get();
            if (rpcResult != null && rpcResult.isSuccessful() && rpcResult.getResult() != null) {
                LOG.info("Group {} with key {} has been successfully installed directly on dpn {}.", groupId,
                        nextHopKey, dpnId);
            } else {
                LOG.error("Unable to install group {} with key {} directly on dpn {} due to {}.", groupId, nextHopKey,
                        dpnId, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error while installing group {} directly on dpn {}", groupId, dpnId);
        }
    }

    protected void addVpnNexthopToDS(BigInteger dpnId, long vpnId, String ipPrefix, long egressPointer) {
        InstanceIdentifierBuilder<VpnNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
            .child(VpnNexthops.class, new VpnNexthopsKey(vpnId));

        // Add nexthop to vpn node
        VpnNexthop nh = new VpnNexthopBuilder()
            .setKey(new VpnNexthopKey(ipPrefix))
            .setDpnId(dpnId)
            .setIpAddress(ipPrefix)
            .setFlowrefCount(1)
            .setEgressPointer(egressPointer).build();

        InstanceIdentifier<VpnNexthop> id1 = idBuilder
            .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix)).build();
        LOG.trace("Adding vpnnextHop {} to Operational DS", nh);
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, id1, nh);

    }

    protected InstanceIdentifier<VpnNexthop> getVpnNextHopIdentifier(long vpnId, String ipAddress) {
        InstanceIdentifier<VpnNexthop> id = InstanceIdentifier.builder(L3nexthop.class)
            .child(VpnNexthops.class, new VpnNexthopsKey(vpnId)).child(VpnNexthop.class,
                new VpnNexthopKey(ipAddress)).build();
        return id;
    }

    protected VpnNexthop getVpnNexthop(long vpnId, String ipAddress) {

        // check if vpn node is there
        InstanceIdentifierBuilder<VpnNexthops> idBuilder =
            InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class,
                new VpnNexthopsKey(vpnId));
        InstanceIdentifier<VpnNexthops> id = idBuilder.build();
        Optional<VpnNexthops> vpnNexthops = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnNexthops.isPresent()) {
            // get nexthops list for vpn
            List<VpnNexthop> nexthops = vpnNexthops.get().getVpnNexthop();
            for (VpnNexthop nexthop : nexthops) {
                if (nexthop.getIpAddress().equals(ipAddress)) {
                    // return nexthop
                    LOG.trace("VpnNextHop : {}", nexthop);
                    return nexthop;
                }
            }
            // return null if not found
        }
        return null;
    }

    public AdjacencyResult getRemoteNextHopPointer(BigInteger remoteDpnId, long vpnId, String prefixIp,
            String nextHopIp) {
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
            egressIfName = getTunnelRemoteNextHopPointer(remoteDpnId, nextHopIp);
        } else {
            egressIfName = getExtPortRemoteNextHopPointer(remoteDpnId, elanInstance);
        }

        LOG.trace("NextHop pointer for prefixIp {} vpnId {} dpnId {} is {}", prefixIp, vpnId, remoteDpnId,
            egressIfName);
        return egressIfName != null ? new AdjacencyResult(egressIfName, egressIfType, nextHopIp,
                prefixIp) : null;
    }

    public BigInteger getDpnForPrefix(long vpnId, String prefixIp) {
        VpnNexthop vpnNexthop = getVpnNexthop(vpnId, prefixIp);
        BigInteger localDpnId = vpnNexthop == null ? null : vpnNexthop.getDpnId();
        return localDpnId;
    }

    private void removeVpnNexthopFromDS(long vpnId, String ipPrefix) {

        InstanceIdentifierBuilder<VpnNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
            .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
            .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix));
        InstanceIdentifier<VpnNexthop> id = idBuilder.build();
        // remove from DS
        LOG.trace("Removing vpn next hop from datastore : {}", id);
        MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
    }

    public void removeLocalNextHop(BigInteger dpnId, Long vpnId, String ipNextHopAddress, String ipPrefixAddress) {
        String ipPrefixStr = vpnId + ipPrefixAddress;
        VpnNexthop prefixNh = null;
        synchronized (ipPrefixStr.intern()) {
            prefixNh = getVpnNexthop(vpnId, ipPrefixAddress);
        }
        String ipAddress = prefixNh != null ? ipPrefixAddress : ipNextHopAddress;

        String nextHopLockStr = vpnId + ipAddress;
        synchronized (nextHopLockStr.intern()) {
            VpnNexthop nh = getVpnNexthop(vpnId, ipAddress);
            if (nh != null) {
                int newFlowrefCnt = nh.getFlowrefCount() - 1;
                if (newFlowrefCnt == 0) { //remove the group only if there are no more flows using this group
                    GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                        dpnId, nh.getEgressPointer(), ipAddress, GroupTypes.GroupAll,
                            Collections.EMPTY_LIST /*listBucketInfo*/);
                    // remove Group ...
                    mdsalApiManager.removeGroup(groupEntity);
                    //update MD-SAL DS
                    removeVpnNexthopFromDS(vpnId, ipAddress);
                    //release groupId
                    removeNextHopPointer(getNextHopKey(vpnId, ipAddress));
                    LOG.debug("Local Next hop {} for {} {} on dpn {} successfully deleted",
                        nh.getEgressPointer(), vpnId, ipAddress, dpnId);
                } else {
                    //just update the flowrefCount of the vpnNexthop
                    VpnNexthop currNh = new VpnNexthopBuilder().setKey(new VpnNexthopKey(ipAddress))
                        .setFlowrefCount(newFlowrefCnt).build();
                    LOG.trace("Updating vpnnextHop {} for refCount {} to Operational DS", currNh, newFlowrefCnt);
                    MDSALUtil.syncUpdate(dataBroker, LogicalDatastoreType.OPERATIONAL, getVpnNextHopIdentifier(vpnId,
                            ipAddress), currNh);
                }
            } else {
                //throw error
                LOG.error("Local Next hop for Prefix {} VpnId {} on dpn {} not deleted", ipAddress, vpnId, dpnId);
            }
        }
    }

    // TODO Clean up the console output
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void setConfTransType(String service, String transportType) {

        if (!service.equalsIgnoreCase("L3VPN")) {
            System.out.println("Please provide a valid service name. Available value(s): L3VPN");
            LOG.error("Incorrect service {} provided for setting the transport type.", service);
            return;
        }

        L3VPNTransportTypes transType = L3VPNTransportTypes.validateTransportType(transportType.toUpperCase());

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
            Optional<ConfTransportTypeL3vpn> configuredTransTypeFromConfig =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, getConfTransportTypeIdentifier());

            if (configuredTransTypeFromConfig.isPresent()) {
                if (configuredTransTypeFromConfig.get().getTransportType().equals(TunnelTypeGre.class)) {
                    configuredTransportTypeL3VPN.setL3VPNTransportTypes(ITMConstants.TUNNEL_TYPE_GRE);
                } else {
                    configuredTransportTypeL3VPN.setL3VPNTransportTypes(ITMConstants.TUNNEL_TYPE_VXLAN);
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
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private String getTunnelRemoteNextHopPointer(BigInteger remoteDpnId, String nextHopIp) {
        if (nextHopIp != null && !nextHopIp.isEmpty()) {
            try {
                // here use the config for tunnel type param
                return getTunnelInterfaceName(remoteDpnId,
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder
                        .getDefaultInstance(nextHopIp));
            } catch (Exception ex) {
                LOG.error("Error while retrieving nexthop pointer for nexthop {} remoteDpn {}",
                        nextHopIp, remoteDpnId, ex);
            }
        }

        return null;
    }

    private String getExtPortRemoteNextHopPointer(BigInteger remoteDpnId, ElanInstance elanInstance) {
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

    private ElanInstance getElanInstanceForPrefix(long vpnId, String prefixIp) {
        ElanInstance elanInstance = null;
        Prefixes prefix = FibUtil.getPrefixToInterface(dataBroker, vpnId, prefixIp);
        if (prefix != null) {
            Uuid subnetId = prefix.getSubnetId();
            if (subnetId != null) {
                Subnetmap subnetMap = FibUtil.getSubnetMap(dataBroker, subnetId);
                if (subnetMap != null && subnetMap.getNetworkId() != null) {
                    elanInstance = elanService.getElanInstance(subnetMap.getNetworkId().getValue());
                }
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
            boolean result = false;
            if (getClass() != obj.getClass()) {
                return result;
            } else {
                AdjacencyResult other = (AdjacencyResult) obj;
                result = interfaceName.equals(other.interfaceName);
            }
            return result;
        }
    }

    long setupLoadBalancingNextHop(Long parentVpnId, BigInteger dpnId,
            String destPrefix, List<BucketInfo> listBucketInfo, boolean addOrRemove) {
        long groupId = createNextHopPointer(getNextHopKey(parentVpnId, destPrefix));
        if (groupId == FibConstants.INVALID_GROUP_ID) {
            LOG.error("Unable to allocate/retrieve groupId for vpnId {} , prefix {}", parentVpnId, destPrefix);
            return groupId;
        }
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                dpnId, groupId, destPrefix, GroupTypes.GroupSelect, listBucketInfo);
        if (addOrRemove == true) {
            mdsalApiManager.syncInstallGroup(groupEntity, FIXED_DELAY_IN_MILLISECONDS);
            try {
                Thread.sleep(WAIT_TIME_FOR_SYNC_INSTALL);
            } catch (InterruptedException e1) {
                LOG.warn("Thread got interrupted while programming LB group {}", groupEntity);
                Thread.currentThread().interrupt();
            }
        } else {
            mdsalApiManager.removeGroup(groupEntity);
        }
        return groupId;
    }

    long createNextHopGroups(Long vpnId, String rd, BigInteger dpnId, VrfEntry vrfEntry,
            Routes routes, List<Routes> vpnExtraRoutes) {
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        List<Routes> clonedVpnExtraRoutes  = new ArrayList<>(vpnExtraRoutes);
        if (clonedVpnExtraRoutes.contains(routes)) {
            listBucketInfo.addAll(getBucketsForLocalNexthop(vpnId, dpnId, vrfEntry, routes));
            clonedVpnExtraRoutes.remove(routes);
        }
        listBucketInfo.addAll(getBucketsForRemoteNexthop(vpnId, dpnId, vrfEntry, rd, clonedVpnExtraRoutes));
        return setupLoadBalancingNextHop(vpnId, dpnId, vrfEntry.getDestPrefix(), listBucketInfo, true);
    }

    private List<BucketInfo> getBucketsForLocalNexthop(Long vpnId, BigInteger dpnId,
            VrfEntry vrfEntry, Routes routes) {
        List<BucketInfo> listBucketInfo = new CopyOnWriteArrayList<>();
        routes.getNexthopIpList().parallelStream().forEach(nextHopIp -> {
            String localNextHopIP;
            if (isIpv4Address(nextHopIp)) {
                localNextHopIP = nextHopIp + NwConstants.IPV4PREFIX;
            } else {
                localNextHopIP = nextHopIp + NwConstants.IPV6PREFIX;
            }
            Prefixes localNextHopInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, localNextHopIP);
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
        LOG.trace("LOCAL: listbucket {}, vpnId {}, dpnId {}, routes {}", listBucketInfo, vpnId, dpnId, routes);
        return listBucketInfo;
    }

    private List<BucketInfo> getBucketsForRemoteNexthop(Long vpnId, BigInteger dpnId, VrfEntry vrfEntry, String rd,
            List<Routes> vpnExtraRoutes) {
        List<BucketInfo> listBucketInfo = new ArrayList<>();
        Map<String, List<ActionInfo>> egressActionMap = new HashMap<>();
        vpnExtraRoutes.forEach(vpnExtraRoute -> vpnExtraRoute.getNexthopIpList().forEach(nextHopIp -> {
            String nextHopPrefixIp;
            if (isIpv4Address(nextHopIp)) {
                nextHopPrefixIp = nextHopIp + NwConstants.IPV4PREFIX;
            } else {
                nextHopPrefixIp = nextHopIp + NwConstants.IPV6PREFIX;
            }
            List<String> tepIpAddresses = FibUtil.getNextHopAddresses(dataBroker, rd, nextHopPrefixIp);
            if (tepIpAddresses.isEmpty()) {
                return;
            }
            // There would be only one nexthop address for a VM ip which would be the tep Ip
            String tepIp = tepIpAddresses.get(0);
            AdjacencyResult adjacencyResult = getRemoteNextHopPointer(dpnId, vpnId,
                    vrfEntry.getDestPrefix(), tepIp);
            if (adjacencyResult == null) {
                return;
            }
            String egressInterface = adjacencyResult.getInterfaceName();
            if (!FibUtil.isTunnelInterface(adjacencyResult)) {
                return;
            }
            Class<? extends TunnelTypeBase> tunnelType = VpnExtraRouteHelper
                    .getTunnelType(interfaceManager,
                            egressInterface);
            Interface ifState = FibUtil.getInterfaceStateFromOperDS(dataBroker, egressInterface);
            if (ifState == null || ifState.getOperStatus() != OperStatus.Up) {
                LOG.trace("Tunnel not up {}", egressInterface);
                return;
            }
            if (!TunnelTypeVxlan.class.equals(tunnelType)) {
                return;
            }
            Long label = FibUtil.getLabelFromRoutePaths(vrfEntry).get();
            Prefixes prefixInfo = FibUtil.getPrefixToInterface(dataBroker, vpnId, nextHopPrefixIp);
            BigInteger tunnelId;
            if (FibUtil.enforceVxlanDatapathSemanticsforInternalRouterVpn(dataBroker, prefixInfo.getSubnetId(),
                    vpnId, rd)) {
                java.util.Optional<Long> optionalVni = FibUtil.getVniForVxlanNetwork(dataBroker,
                        prefixInfo.getSubnetId());
                if (!optionalVni.isPresent()) {
                    LOG.error("VNI not found for nexthop {} vrfEntry {} with subnetId {}", nextHopIp,
                            vrfEntry, prefixInfo.getSubnetId());
                    return;
                }
                tunnelId = BigInteger.valueOf(optionalVni.get());
            } else {
                tunnelId = BigInteger.valueOf(label);
            }
            List<ActionInfo> actionInfos = new ArrayList<>();
            actionInfos.add(new ActionSetFieldTunnelId(tunnelId));
            String ifName = prefixInfo.getVpnInterfaceName();
            String macAddress = FibUtil.getMacAddressFromPrefix(dataBroker, ifName, nextHopPrefixIp);
            actionInfos.add(new ActionSetFieldEthernetDestination(actionInfos.size(),
                    new MacAddress(macAddress)));
            List<ActionInfo> egressActions;
            if (egressActionMap.containsKey(egressInterface)) {
                egressActions = egressActionMap.get(egressInterface);
            } else {
                egressActions = getEgressActionsForInterface(egressInterface, actionInfos.size());
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

    public void createDcGwLoadBalancingGroup(List<String> availableDcGws, BigInteger dpnId, String destinationIp) {
        Preconditions.checkNotNull(availableDcGws, "There are no dc-gws present");
        int noOfDcGws = availableDcGws.size();
        if (noOfDcGws == 1) {
            LOG.trace("There are no enough DC GateWays {} present to program LB group", availableDcGws);
            return;
        }
        // TODO : Place the logic to construct all possible DC-GW combination here.
        String groupIdKey = FibUtil.getGreLbGroupKey(availableDcGws);
        Long groupId = createNextHopPointer(groupIdKey);
        List<Bucket> listBucket = new ArrayList<>();
        for (int index = 0; index < noOfDcGws; index++) {
            if (isTunnelUp(availableDcGws.get(index), dpnId)) {
                listBucket.add(buildBucketForDcGwLbGroup(availableDcGws.get(index), dpnId, index));
            }
        }
        Group group = MDSALUtil.buildGroup(groupId, groupIdKey, GroupTypes.GroupSelect,
                        MDSALUtil.buildBucketLists(listBucket));
        WriteTransaction configTx = dataBroker.newWriteOnlyTransaction();
        WriteTransaction operationalTx = dataBroker.newWriteOnlyTransaction();
        mdsalApiManager.addGroupToTx(dpnId, group, configTx);
        FibUtil.updateLbGroupInfo(dpnId, destinationIp, groupIdKey, groupId.toString(), operationalTx);
        configTx.submit();
        operationalTx.submit();
        LOG.trace("LB group {} towards DC-GW installed on dpn {}. Group - {}", groupIdKey, dpnId, group);
    }

    private boolean isTunnelUp(String dcGwIp, BigInteger dpnId) {
        String tunnelName = getTunnelRemoteNextHopPointer(dpnId, dcGwIp);
        if (tunnelName != null) {
            InstanceIdentifier<StateTunnelList> tunnelStateId =
                    InstanceIdentifier.builder(TunnelsState.class).child(
                            StateTunnelList.class, new StateTunnelListKey(tunnelName)).build();
            // Don’t use Optional.transform() here, getOperState() can return null
            Optional<StateTunnelList> optionalStateTunnelList =
                    MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, tunnelStateId);
            return optionalStateTunnelList.isPresent()
                    && optionalStateTunnelList.get().getOperState() == TunnelOperStatus.Up;
        }
        return false;
    }

    private List<Action> getEgressActions(String interfaceName, int actionKey) {
        List<Action> actions = Collections.EMPTY_LIST;
        try {
            GetEgressActionsForInterfaceInputBuilder egressAction =
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(interfaceName).setActionKey(actionKey);
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                    interfaceManager.getEgressActionsForInterface(egressAction.build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}",
                        interfaceName, rpcResult.getErrors());
            } else {
                actions = rpcResult.getResult().getAction();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", interfaceName, e);
        }
        return actions;
    }

    /**
     * This method is invoked when the tunnel state is removed from DS.
     * If the there is just one DC-GW left in configuration then the LB groups can be deleted.
     * Otherwise, the groups are just updated.
     */
    public void removeOrUpdateDcGwLoadBalancingGroup(List<String> availableDcGws, BigInteger dpnId,
            String destinationIp) {
        Preconditions.checkNotNull(availableDcGws, "There are no dc-gws present");
        WriteTransaction configTx = dataBroker.newWriteOnlyTransaction();
        WriteTransaction operationalTx = dataBroker.newWriteOnlyTransaction();
        int noOfDcGws = availableDcGws.size();
        // If availableDcGws does not contain the destination Ip it means this is a configuration delete.
        if (!availableDcGws.contains(destinationIp)) {
            availableDcGws.add(destinationIp);
            Collections.sort(availableDcGws);
        }
        // TODO : Place the logic to construct all possible DC-GW combination here.
        int bucketId = availableDcGws.indexOf(destinationIp);
        Optional<DpnLbNexthops> dpnLbNextHops = FibUtil.getDpnLbNexthops(dataBroker, dpnId, destinationIp);
        if (!dpnLbNextHops.isPresent()) {
            return;
        }
        List<String> nextHopKeys = dpnLbNextHops.get().getNexthopKey();
        nextHopKeys.forEach(nextHopKey -> {
            Optional<Nexthops> optionalNextHops = FibUtil.getNexthops(dataBroker, nextHopKey);
            if (!optionalNextHops.isPresent()) {
                return;
            }
            Nexthops nexthops = optionalNextHops.get();
            final String groupId = nexthops.getGroupId();
            if (noOfDcGws > 1) {
                mdsalApiManager.removeBucketToTx(dpnId, Long.valueOf(groupId), bucketId, configTx);
            } else {
                Group group = MDSALUtil.buildGroup(Long.valueOf(groupId), nextHopKey, GroupTypes.GroupSelect,
                        MDSALUtil.buildBucketLists(Collections.EMPTY_LIST));
                LOG.trace("Removed LB group {} on dpn {}", group, dpnId);
                mdsalApiManager.removeGroupToTx(dpnId, group, configTx);
                removeNextHopPointer(nextHopKey);
            }
            // When the DC-GW is removed from configuration.
            if (noOfDcGws != availableDcGws.size()) {
                FibUtil.removeOrUpdateNextHopInfo(dpnId, nextHopKey, groupId, nexthops, operationalTx);
            }
        });
        FibUtil.removeDpnIdToNextHopInfo(destinationIp, dpnId, operationalTx);
        configTx.submit();
        operationalTx.submit();
        return;
    }

    /**
     * This method is invoked when the tunnel status is updated.
     * The bucket is directly removed/added based on the operational status of the tunnel.
     */
    public void updateDcGwLoadBalancingGroup(List<String> availableDcGws,
            BigInteger dpnId, String destinationIp, boolean isTunnelUp) {
        Preconditions.checkNotNull(availableDcGws, "There are no dc-gws present");
        WriteTransaction configTx = dataBroker.newWriteOnlyTransaction();
        // TODO : Place the logic to construct all possible DC-GW combination here.
        int bucketId = availableDcGws.indexOf(destinationIp);
        Optional<DpnLbNexthops> dpnLbNextHops = FibUtil.getDpnLbNexthops(dataBroker, dpnId, destinationIp);
        if (!dpnLbNextHops.isPresent()) {
            return;
        }
        List<String> nextHopKeys = dpnLbNextHops.get().getNexthopKey();
        nextHopKeys.forEach(nextHopKey -> {
            Optional<Nexthops> optionalNextHops = FibUtil.getNexthops(dataBroker, nextHopKey);
            if (!optionalNextHops.isPresent()) {
                return;
            }
            Nexthops nexthops = optionalNextHops.get();
            final String groupId = nexthops.getGroupId();
            if (isTunnelUp) {
                Bucket bucket = buildBucketForDcGwLbGroup(destinationIp, dpnId, bucketId);
                LOG.trace("Added bucket {} to group {} on dpn {}.", bucket, groupId, dpnId);
                mdsalApiManager.addBucketToTx(dpnId, Long.valueOf(groupId), bucket , configTx);
            } else {
                LOG.trace("Removed bucketId {} from group {} on dpn {}.", bucketId, groupId, dpnId);
                mdsalApiManager.removeBucketToTx(dpnId, Long.valueOf(groupId), bucketId, configTx);
            }
        });
        configTx.submit();
        return;
    }

    private Bucket buildBucketForDcGwLbGroup(String ipAddress, BigInteger dpnId,
            int index) {
        List<Action> listAction = new ArrayList<>();
        // ActionKey 0 goes to mpls label.
        int actionKey = 1;
        listAction.add(new ActionPushMpls().buildAction());
        listAction.add(new ActionRegMove(actionKey++, FibConstants.NXM_REG_MAPPING
                .get(index), 0, 19).buildAction());
        String tunnelInterfaceName = getTunnelInterfaceName(dpnId, new IpAddress(ipAddress.toCharArray()));
        List<Action> egressActions = getEgressActions(tunnelInterfaceName, actionKey++);
        if (!egressActions.isEmpty()) {
            listAction.addAll(getEgressActions(tunnelInterfaceName, actionKey++));
        } else {
            // clear off actions if there is no egress actions.
            listAction = Collections.EMPTY_LIST;
        }
        return MDSALUtil.buildBucket(listAction, MDSALUtil.GROUP_WEIGHT, index,
                MDSALUtil.WATCH_PORT, MDSALUtil.WATCH_GROUP);
    }

    public void programDcGwLoadBalancingGroup(List<String> availableDcGws, BigInteger dpnId,
            String destinationIp, int addRemoveOrUpdate, boolean isTunnelUp) {
        if (NwConstants.ADD_FLOW == addRemoveOrUpdate) {
            createDcGwLoadBalancingGroup(availableDcGws, dpnId, destinationIp);
        } else if (NwConstants.DEL_FLOW == addRemoveOrUpdate) {
            removeOrUpdateDcGwLoadBalancingGroup(availableDcGws, dpnId, destinationIp);
        } else if (NwConstants.MOD_FLOW == addRemoveOrUpdate) {
            updateDcGwLoadBalancingGroup(availableDcGws, dpnId, destinationIp, isTunnelUp);
        }
    }
}
