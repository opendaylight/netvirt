/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;


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

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
        new FutureCallback<Void>() {
            public void onSuccess(Void result) {
                LOG.info("Success in Datastore write operation");
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
//            try {
//                LOG.info("Result2: {}",result.get());
//            } catch (InterruptedException | ExecutionException e) {
//                // TODO Auto-generated catch block
//                LOG.error("Error in result.get");
//            }

    }


    private long getVpnId(String vpnName) {
        InstanceIdentifierBuilder<VpnInstance> idBuilder = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName));

        InstanceIdentifier<VpnInstance> id = idBuilder.build();
        InstanceIdentifier<VpnInstance1> idx = id.augmentation(VpnInstance1.class);
        Optional<VpnInstance1> vpn = read(LogicalDatastoreType.CONFIGURATION, idx);

        if (vpn.isPresent()) {
            return vpn.get().getVpnId();
        } else {
            return 0;
        }
    }

    private long getDpnId(String ifName) {
        String[] fields = ifName.split(":");
        long dpn = Integer.parseInt(fields[1]);
        return dpn;
    }

    private int createNextHopPointer(String nexthopKey) {
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
        long dpnId = interfaceManager.getDpnForInterface(ifName);
        VpnNexthop nexthop = getVpnNexthop(vpnId, ipAddress);
        if (nexthop == null) {
            List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfo = interfaceManager.getInterfaceEgressActions(ifName);
            BucketInfo bucket = new BucketInfo(listActionInfo);
            // MAC re-write
            if (macAddress != null) {
                listActionInfo.add(new ActionInfo(ActionType.set_field_eth_dest, new String[]{macAddress}));
            } else {
                //FIXME: Log message here.
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

        long dpnId = getDpnId(ofPortId);
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

            //update MD-SAL DS
            addTunnelNexthopToDS(dpnId, ipAddress, groupId);
        } else {
            //check update
        }
    }

    private void addVpnNexthopToDS(long vpnId, String ipPrefix, long egressPointer) {

        InstanceIdentifierBuilder<VpnNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId));

        // check if vpn node is there or to be created
        InstanceIdentifier<VpnNexthops> id = idBuilder.build();
        Optional<VpnNexthops> nexthops = read(LogicalDatastoreType.CONFIGURATION, id);
        if (!nexthops.isPresent()) {
            // create a new node
            VpnNexthops node = new VpnNexthopsBuilder().setKey(new VpnNexthopsKey(vpnId)).setVpnId(vpnId).build();
            asyncWrite(LogicalDatastoreType.OPERATIONAL, id, node, DEFAULT_CALLBACK);
        }

        // Add nexthop to vpn node
        VpnNexthop nh = new VpnNexthopBuilder().
                setKey(new VpnNexthopKey(ipPrefix)).
                setIpAddress(ipPrefix).
                setEgressPointer(egressPointer).build();

        InstanceIdentifier<VpnNexthop> id1 = idBuilder
                .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix)).build();

        asyncWrite(LogicalDatastoreType.OPERATIONAL, id1, nh, DEFAULT_CALLBACK);

    }

    private void addTunnelNexthopToDS(long dpnId, String ipPrefix, long egressPointer) {
        InstanceIdentifierBuilder<TunnelNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId));

        // check if dpn node is there or to be created
        InstanceIdentifier<TunnelNexthops> id = idBuilder.build();
        Optional<TunnelNexthops> nexthops = read(LogicalDatastoreType.CONFIGURATION, id);
        if (!nexthops.isPresent()) {
            // create a new node
            TunnelNexthops node = new TunnelNexthopsBuilder()
                .setKey(new TunnelNexthopsKey(dpnId))
                .setDpnId(dpnId)
                .build();
            asyncWrite(LogicalDatastoreType.OPERATIONAL, id, node, DEFAULT_CALLBACK);
        }

        // Add nexthop to dpn node
        TunnelNexthop nh = new TunnelNexthopBuilder().
                setKey(new TunnelNexthopKey(ipPrefix)).
                setIpAddress(ipPrefix).
                setEgressPointer(egressPointer).build();

        InstanceIdentifier<TunnelNexthop> id1 = idBuilder
                .child(TunnelNexthop.class, new TunnelNexthopKey(ipPrefix)).build();

        asyncWrite(LogicalDatastoreType.OPERATIONAL, id1, nh, DEFAULT_CALLBACK);

    }

    private VpnNexthop getVpnNexthop(long vpnId, String ipAddress) {

        // check if vpn node is there 
        InstanceIdentifierBuilder<VpnNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId));
        InstanceIdentifier<VpnNexthops> id = idBuilder.build();
        Optional<VpnNexthops> vpnNexthops = read(LogicalDatastoreType.CONFIGURATION, id);
        if (!vpnNexthops.isPresent()) {

            // get nexthops list for vpn
            List<VpnNexthop> nexthops = vpnNexthops.get().getVpnNexthop();
            for (VpnNexthop nexthop : nexthops) {
                if (nexthop.getIpAddress().equals(ipAddress)) {
                    // return nexthop 
                    return nexthop;
                }
            }
        }
        //return null if not found
        return null;
    }

    private TunnelNexthop getTunnelNexthop(long dpnId, String ipAddress) {
        
        InstanceIdentifierBuilder<TunnelNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId));

        // check if vpn node is there 
        InstanceIdentifier<TunnelNexthops> id = idBuilder.build();
        Optional<TunnelNexthops> dpnNexthops = read(LogicalDatastoreType.CONFIGURATION, id);
        if (!dpnNexthops.isPresent()) {
            List<TunnelNexthop> nexthops = dpnNexthops.get().getTunnelNexthop();
            for (TunnelNexthop nexthop : nexthops) {
                if (nexthop.getIpAddress().equals(ipAddress)) {
                    return nexthop;
                }
            }
        }
        return null;
    }

    public long getNextHopPointer(long dpnId, long vpnId, String prefixIp, String nextHopIp) {
        String endpointIp = interfaceManager.getEndpointIpForDpn(dpnId);
        if (nextHopIp.equals(endpointIp)) {
            VpnNexthop vpnNextHop = getVpnNexthop(vpnId, prefixIp);
            return vpnNextHop.getEgressPointer();
        } else {
            TunnelNexthop tunnelNextHop = getTunnelNexthop(dpnId, nextHopIp);
            return tunnelNextHop.getEgressPointer();
        }
    }

    private void removeTunnelNexthopFromDS(long dpnId, String ipPrefix) {

        InstanceIdentifierBuilder<TunnelNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId))
                .child(TunnelNexthop.class, new TunnelNexthopKey(ipPrefix));
        InstanceIdentifier<TunnelNexthop> id = idBuilder.build();
        // remove from DS     
        delete(LogicalDatastoreType.OPERATIONAL, id);
    }

    private void removeVpnNexthopFromDS(long vpnId, String ipPrefix) {

        InstanceIdentifierBuilder<VpnNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
                .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix));
        InstanceIdentifier<VpnNexthop> id = idBuilder.build();
        // remove from DS
        delete(LogicalDatastoreType.OPERATIONAL, id);
    }

 
    public void removeLocalNextHop(String vpnName, String ipAddress) {

        long vpnId = getVpnId(vpnName);

        VpnNexthop nh = getVpnNexthop(vpnId, ipAddress);
        if (nh != null) {
            // how to inform and remove dependent FIB entries??
            // we need to do it before the group is removed
            
            // remove Group ...
            
            //update MD-SAL DS
            removeVpnNexthopFromDS(vpnId, ipAddress);
        } else {
            //throw error
        }

    }

    public void removeRemoteNextHop(long dpnId, String ipAddress) {

        TunnelNexthop nh = getTunnelNexthop(dpnId, ipAddress);
        if (nh != null) {
            // how to inform and remove dependent FIB entries??
            // we need to do it before the group is removed

            // remove Group ...
            //update MD-SAL DS
            removeTunnelNexthopFromDS(dpnId, ipAddress);
        } else {
            //throw error
        }

    }

    @Override
    public Future<RpcResult<GetEgressPointerOutput>> getEgressPointer(
            GetEgressPointerInput input) {

        GetEgressPointerOutputBuilder output = new GetEgressPointerOutputBuilder();

        String endpointIp = interfaceManager.getEndpointIpForDpn(input.getDpnId());
        if (input.getNexthopIp().equals(endpointIp)) {
            VpnNexthop vpnNextHop = getVpnNexthop(input.getVpnId(), input.getIpPrefix());
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
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }


    private <T extends DataObject> void delete(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

}
