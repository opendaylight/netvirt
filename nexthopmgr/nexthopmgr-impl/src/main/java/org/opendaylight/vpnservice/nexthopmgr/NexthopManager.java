/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;

import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.GetUniqueIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.tunnelnexthops.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.*;
import org.opendaylight.vpnservice.interfacemgr.interfaces.IInterfaceManager;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.BucketInfo;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.idmanager.IdManager;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexthopManager implements L3nexthopService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NexthopManager.class);
    private final DataBroker broker;
    private IMdsalApiManager mdsalManager;
    private IInterfaceManager interfaceManager;
    private IdManager idManager;
    private static final short LPORT_INGRESS_TABLE = 0;
    private static final short LFIB_TABLE = 20;
    private static final short FIB_TABLE = 21;
    private static final short DEFAULT_FLOW_PRIORITY = 10;

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
        new FutureCallback<Void>() {
            public void onSuccess(Void result) {
                LOG.debug("Success in Datastore write operation");
            }
            public void onFailure(Throwable error) {
                LOG.error("Error in Datastore write operation", error);
            };
        };

    /**
    * Provides nexthop functions
    * Creates group ID pool
    *
    * @param db - dataBroker reference
    */
    public NexthopManager(final DataBroker db) {
        broker = db;
    }

    @Override
    public void close() throws Exception {
        LOG.info("NextHop Manager Closed");
    }

    public void setInterfaceManager(IInterfaceManager ifManager) {
        this.interfaceManager = ifManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setIdManager(IdManager idManager) {
        this.idManager = idManager;
    }

    protected void createNexthopPointerPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName("nextHopPointerPool")
            .setIdStart(1L)
            .setPoolSize(new BigInteger("65535"))
            .build();
        //TODO: Error handling
        Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
        LOG.trace("NextHopPointerPool result : {}", result);
//            try {
//                LOG.info("Result2: {}",result.get());
//            } catch (InterruptedException | ExecutionException e) {
//                // TODO Auto-generated catch block
//                LOG.error("Error in result.get");
//            }

    }


    protected long getVpnId(String vpnName) {
        InstanceIdentifierBuilder<VpnInstance> idBuilder = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName));

        InstanceIdentifier<VpnInstance> id = idBuilder.build();
        InstanceIdentifier<VpnInstance1> idx = id.augmentation(VpnInstance1.class);
        Optional<VpnInstance1> vpn = read(LogicalDatastoreType.OPERATIONAL, idx);

        if (vpn.isPresent()) {
            LOG.debug("VPN id returned: {}", vpn.get().getVpnId());
            return vpn.get().getVpnId();
        } else {
            return -1;
        }
    }

    private BigInteger getDpnId(String ofPortId) {
        String[] fields = ofPortId.split(":");
        BigInteger dpn = new BigInteger(fields[1]);
        LOG.debug("DpnId: {}", dpn);
        return dpn;
    }

    protected int createNextHopPointer(String nexthopKey) {
        GetUniqueIdInput getIdInput = new GetUniqueIdInputBuilder()
            .setPoolName("nextHopPointerPool").setIdKey(nexthopKey)
            .build();
        //TODO: Proper error handling once IdManager code is complete
        try {
            Future<RpcResult<GetUniqueIdOutput>> result = idManager.getUniqueId(getIdInput);
            RpcResult<GetUniqueIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue().intValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("",e);
        }
        return 0;
    }

    public void createLocalNextHop(String ifName, String vpnName, String ipAddress, String macAddress) {
        String nhKey = new String("nexthop." + vpnName + ipAddress);
        int groupId = createNextHopPointer(nhKey);

        long vpnId = getVpnId(vpnName);
        BigInteger dpnId = interfaceManager.getDpnForInterface(ifName);
        VpnNexthop nexthop = getVpnNexthop(vpnId, ipAddress, 0);
        LOG.trace("nexthop: {}", nexthop);
        if (nexthop == null) {
            List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfo = interfaceManager.getInterfaceEgressActions(ifName);
            BucketInfo bucket = new BucketInfo(listActionInfo);
            // MAC re-write
            if (macAddress != null) {
               listActionInfo.add(0, new ActionInfo(ActionType.set_field_eth_dest, new String[]{macAddress}));
               listActionInfo.add(0, new ActionInfo(ActionType.pop_mpls, new String[]{}));
            } else {
                //FIXME: Log message here.
                LOG.debug("mac address for new local nexthop is null");
            }
            listBucketInfo.add(bucket);
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                dpnId, groupId, ipAddress, GroupTypes.GroupIndirect, listBucketInfo);

            // install Group
            mdsalManager.installGroup(groupEntity);

            //update MD-SAL DS
            addVpnNexthopToDS(vpnId, ipAddress, groupId);
        } else {
            //check update
        }
    }

    public void createRemoteNextHop(String ifName, String ofPortId, String ipAddress) {
        String nhKey = new String("nexthop." + ifName + ipAddress);
        int groupId = createNextHopPointer(nhKey);

        BigInteger dpnId = getDpnId(ofPortId);
        TunnelNexthop nexthop = getTunnelNexthop(dpnId, ipAddress);
        if (nexthop == null) {
            List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfo = interfaceManager.getInterfaceEgressActions(ifName);
            BucketInfo bucket = new BucketInfo(listActionInfo);
            // MAC re-write??           
            listBucketInfo.add(bucket);
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                dpnId, groupId, ipAddress, GroupTypes.GroupIndirect, listBucketInfo);
            mdsalManager.installGroup(groupEntity);
            //makeRemoteFlow(dpnId, ifName, NwConstants.ADD_FLOW);

            //update MD-SAL DS
            addTunnelNexthopToDS(dpnId, ipAddress, groupId);
        } else {
            //check update
        }
    }

    private void makeRemoteFlow(BigInteger dpnId, String ifName, int addOrRemoveFlow) {
        long portNo = 0;
        String flowName = ifName;
        String flowRef = getTunnelInterfaceFlowRef(dpnId, LPORT_INGRESS_TABLE, ifName);
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        List<InstructionInfo> mkInstructions = new ArrayList<InstructionInfo>();
        if (NwConstants.ADD_FLOW == addOrRemoveFlow) {
            portNo = interfaceManager.getPortForInterface(ifName);
            matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {
                dpnId, BigInteger.valueOf(portNo) }));
            mkInstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] {LFIB_TABLE}));
        }

        BigInteger COOKIE_VM_INGRESS_TABLE = new BigInteger("8000001", 16);
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, LPORT_INGRESS_TABLE, flowRef,
                                                          DEFAULT_FLOW_PRIORITY, flowName, 0, 0, COOKIE_VM_INGRESS_TABLE, matches, mkInstructions);

        if (NwConstants.ADD_FLOW == addOrRemoveFlow) {
            mdsalManager.installFlow(flowEntity);
        } else {
            mdsalManager.removeFlow(flowEntity);
        }
    }

    private String getTunnelInterfaceFlowRef(BigInteger dpnId, short tableId, String ifName) {
                return new StringBuilder().append(dpnId).append(tableId).append(ifName).toString();
            }

    protected void addVpnNexthopToDS(long vpnId, String ipPrefix, long egressPointer) {

        InstanceIdentifierBuilder<VpnNexthops> idBuilder = InstanceIdentifier.builder(
            L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId));

        // Add nexthop to vpn node
        VpnNexthop nh = new VpnNexthopBuilder().
                setKey(new VpnNexthopKey(ipPrefix)).
                setIpAddress(ipPrefix).
                setEgressPointer(egressPointer).build();

        InstanceIdentifier<VpnNexthop> id1 = idBuilder
                .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix)).build();
        LOG.trace("Adding vpnnextHop {} to Operational DS", nh);
        syncWrite(LogicalDatastoreType.OPERATIONAL, id1, nh, DEFAULT_CALLBACK);

    }

    private void addTunnelNexthopToDS(BigInteger dpnId, String ipPrefix, long egressPointer) {
        InstanceIdentifierBuilder<TunnelNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId));

        // Add nexthop to dpn node
        TunnelNexthop nh = new TunnelNexthopBuilder().
                setKey(new TunnelNexthopKey(ipPrefix)).
                setIpAddress(ipPrefix).
                setEgressPointer(egressPointer).build();

        InstanceIdentifier<TunnelNexthop> id1 = idBuilder
                .child(TunnelNexthop.class, new TunnelNexthopKey(ipPrefix)).build();
        LOG.trace("Adding tunnelnextHop {} to Operational DS for a dpn node", nh);
        asyncWrite(LogicalDatastoreType.OPERATIONAL, id1, nh, DEFAULT_CALLBACK);

    }

    protected VpnNexthop getVpnNexthop(long vpnId, String ipAddress, int retryCount) {

        // check if vpn node is there
        InstanceIdentifierBuilder<VpnNexthops> idBuilder =
                        InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class, new VpnNexthopsKey(vpnId));
        InstanceIdentifier<VpnNexthops> id = idBuilder.build();
        try {
            for (int retry = 0; retry <= retryCount; retry++) {
                Optional<VpnNexthops> vpnNexthops = read(LogicalDatastoreType.OPERATIONAL, id);
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
                }
                Thread.sleep(100L);
            }
        } catch (InterruptedException e) {
            LOG.trace("", e);
        }
        // return null if not found
        return null;
    }

    private TunnelNexthop getTunnelNexthop(BigInteger dpnId, String ipAddress) {
        
        InstanceIdentifierBuilder<TunnelNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId));

        // check if vpn node is there 
        InstanceIdentifier<TunnelNexthops> id = idBuilder.build();
        Optional<TunnelNexthops> dpnNexthops = read(LogicalDatastoreType.OPERATIONAL, id);
        if (dpnNexthops.isPresent()) {
            List<TunnelNexthop> nexthops = dpnNexthops.get().getTunnelNexthop();
            for (TunnelNexthop nexthop : nexthops) {
                if (nexthop.getIpAddress().equals(ipAddress)) {
                    LOG.trace("TunnelNextHop : {}",nexthop);
                    return nexthop;
                }
            }
        }
        return null;
    }

    public long getNextHopPointer(BigInteger dpnId, long vpnId, String prefixIp, String nextHopIp) {
        String endpointIp = interfaceManager.getEndpointIpForDpn(dpnId);
        if (nextHopIp.equals(endpointIp)) {
            VpnNexthop vpnNextHop = getVpnNexthop(vpnId, prefixIp, 0);
            return vpnNextHop.getEgressPointer();
        } else {
            TunnelNexthop tunnelNextHop = getTunnelNexthop(dpnId, nextHopIp);
            LOG.trace("NExtHopPointer : {}", tunnelNextHop.getEgressPointer());
            return tunnelNextHop.getEgressPointer();
        }
    }

    private void removeTunnelNexthopFromDS(BigInteger dpnId, String ipPrefix) {

        InstanceIdentifierBuilder<TunnelNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId))
                .child(TunnelNexthop.class, new TunnelNexthopKey(ipPrefix));
        InstanceIdentifier<TunnelNexthop> id = idBuilder.build();
        // remove from DS     
        LOG.trace("Removing tunnel next hop from datastore : {}", id);
        delete(LogicalDatastoreType.OPERATIONAL, id);
    }

    private void removeVpnNexthopFromDS(long vpnId, String ipPrefix) {

        InstanceIdentifierBuilder<VpnNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
                .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix));
        InstanceIdentifier<VpnNexthop> id = idBuilder.build();
        // remove from DS
        LOG.trace("Removing vpn next hop from datastore : {}", id);
        delete(LogicalDatastoreType.OPERATIONAL, id);
    }

 
    public void removeLocalNextHop(BigInteger dpnId, Long vpnId, String ipAddress) {

        VpnNexthop nh = getVpnNexthop(vpnId, ipAddress, 0);
        if (nh != null) {
            // how to inform and remove dependent FIB entries??
            // we need to do it before the group is removed
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                    dpnId, nh.getEgressPointer(), ipAddress, GroupTypes.GroupIndirect, null);
            // remove Group ...
            mdsalManager.removeGroup(groupEntity);
            //update MD-SAL DS
            removeVpnNexthopFromDS(vpnId, ipAddress);
        } else {
            //throw error
            LOG.error("removal of local next hop failed");
        }

    }

    public void removeRemoteNextHop(BigInteger dpnId, String ifName, String ipAddress) {

        TunnelNexthop nh = getTunnelNexthop(dpnId, ipAddress);
        if (nh != null) {
            // how to inform and remove dependent FIB entries??
            // we need to do it before the group is removed

            // remove Group ...
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                    dpnId, nh.getEgressPointer(), ipAddress, GroupTypes.GroupIndirect, null);
            // remove Group ...
            mdsalManager.removeGroup(groupEntity);
            //makeRemoteFlow(dpnId, ifName, NwConstants.DEL_FLOW);
            //update MD-SAL DS
            removeTunnelNexthopFromDS(dpnId, ipAddress);
        } else {
            //throw error
            LOG.error("removal of remote next hop failed : dpnid : {}, ipaddress : {}", dpnId, ipAddress);
        }

    }

    @Override
    public Future<RpcResult<GetEgressPointerOutput>> getEgressPointer(
            GetEgressPointerInput input) {

        GetEgressPointerOutputBuilder output = new GetEgressPointerOutputBuilder();

        String endpointIp = interfaceManager.getEndpointIpForDpn(input.getDpnId());
        LOG.trace("getEgressPointer: input {}, endpointIp {}", input, endpointIp);
        if (input.getNexthopIp() == null || input.getNexthopIp().equals(endpointIp)) {
            VpnNexthop vpnNextHop = getVpnNexthop(input.getVpnId(), input.getIpPrefix(), 5);
            output.setEgressPointer(vpnNextHop.getEgressPointer());
            output.setLocalDestination(true);
        } else {
            TunnelNexthop tunnelNextHop = getTunnelNexthop(input.getDpnId(), input.getNexthopIp());
            output.setEgressPointer(tunnelNextHop.getEgressPointer());
            output.setLocalDestination(false);
        }

        RpcResultBuilder<GetEgressPointerOutput> rpcResultBuilder = RpcResultBuilder.success();
        rpcResultBuilder.withResult(output.build());

        return Futures.immediateFuture(rpcResultBuilder.build());
        
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        tx.submit();
    }

    private <T extends DataObject> void delete(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    @Override
    public Future<RpcResult<Void>> removeLocalNextHop(RemoveLocalNextHopInput input) {
        VpnNexthop vpnNextHop = getVpnNexthop(input.getVpnId(), input.getIpPrefix(), 0);
        RpcResultBuilder<Void> rpcResultBuilder;
        LOG.debug("vpnnexthop is: {}", vpnNextHop);
        try {
            removeLocalNextHop(input.getDpnId(),input.getVpnId(), input.getIpPrefix());
            rpcResultBuilder = RpcResultBuilder.success();
        }
        catch(Exception e){
            LOG.error("Removal of local next hop for vpnNextHop {} failed {}" ,vpnNextHop, e);
            rpcResultBuilder = RpcResultBuilder.failed();
        }
        return Futures.immediateFuture(rpcResultBuilder.build());
    }

}
