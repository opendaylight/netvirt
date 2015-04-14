/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.nexthopmgr;


import com.google.common.base.Optional;
import java.util.concurrent.Future;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;

import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstance1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.tunnelnexthops.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexthopManager implements L3nexthopService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NexthopManager.class);
    private final DataBroker broker;

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
        // create nexhhop ID pool
      //  getIdManager.createIdPool("nextHopGroupIdPool", 10000, 100000);
        broker = db;
    }


    @Override
    public void close() throws Exception {
        LOG.info("NextHop Manager Closed");
    }


    public void createLocalNextHop(String ifName, String vpnName, String ipAddress)
    {
        String nhKey = new String("nexthop." + vpnName + ipAddress);
        int groupId = 1;//getIdManager().getUniqueId("nextHopGroupIdPool", nhKey);

        long vpnId = getVpnId(vpnName);
        VpnNexthop nexthop = getVpnNexthop(vpnId, ipAddress);
        if (nexthop == null) {

         /*   List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfo = interfacemgr.getEgressGroupActions(ifName);
            BucketInfo bucket = new BucketInfo(listActionInfo);
            // MAC re-write??
            listBucketInfo.add(bucket);
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity
                (dpId, groupId, IPAddress, GroupTypes.GroupIndirect, listBucketInfo);
            getMdsalApiManager().installGroup(groupEntity, objTransaction???);
            */

            //update MD-SAL DS
            addVpnNexthopToDS(vpnId, ipAddress, groupId);
        } else {
            //check update
        }
    }

    private long getVpnId(String vpnName) {
        InstanceIdentifierBuilder<VpnInstance> idBuilder = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName));

        InstanceIdentifier<VpnInstance> id = idBuilder.build();
        InstanceIdentifier<VpnInstance1> idx = id.augmentation(VpnInstance1.class);
        Optional<VpnInstance1> vpn = read(LogicalDatastoreType.CONFIGURATION, idx);

        if (vpn.isPresent()) return vpn.get().getVpnId();
        else return 0;
    }

    private long getDpnId(String ifName) {
        return 1;
    }

    public void createRemoteNextHop(String ifName, String ipAddress)
    {
        String nhKey = new String("nexthop." + ifName + ipAddress);
        int groupId = 1;//getIdManager().getUniqueId("nextHopGroupIdPool", nhKey);

        long dpnId = getDpnId(ifName);
        TunnelNexthop nexthop = getTunnelNexthop(dpnId, ipAddress);
        if (nexthop == null) {

         /*   List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfo = interfacemgr.getEgressGroupActions(ifName);
            BucketInfo bucket = new BucketInfo(listActionInfo);
            // MAC re-write??
            listBucketInfo.add(bucket);
            GroupEntity groupEntity = MDSALUtil.buildGroupEntity
                (dpId, groupId, IPAddress, GroupTypes.GroupIndirect, listBucketInfo);
            getMdsalApiManager().installGroup(groupEntity, objTransaction???);
            */

            //update MD-SAL DS
            addTunnelNexthopToDS(dpnId, ipAddress, groupId);
        } else {
            //check update
        }
    }

    private void addVpnNexthopToDS(long vpnId, String ipPrefix, long egressPointer){


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

    private void addTunnelNexthopToDS(long dpnId, String ipPrefix, long egressPointer){
        InstanceIdentifierBuilder<TunnelNexthops> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId));

        // check if dpn node is there or to be created
        InstanceIdentifier<TunnelNexthops> id = idBuilder.build();
        Optional<TunnelNexthops> nexthops = read(LogicalDatastoreType.CONFIGURATION, id);
        if (!nexthops.isPresent()) {
            // create a new node
            TunnelNexthops node = new TunnelNexthopsBuilder().setKey(new TunnelNexthopsKey(dpnId)).setDpnId(dpnId).build();
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

        InstanceIdentifierBuilder<VpnNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
                .child(VpnNexthop.class, new VpnNexthopKey(ipAddress));
        InstanceIdentifier<VpnNexthop> id = idBuilder.build();
        Optional<VpnNexthop> nextHop = read(LogicalDatastoreType.CONFIGURATION, id);

        if(nextHop.isPresent()) return nextHop.get();
        else return null;
    }

    private TunnelNexthop getTunnelNexthop(long dpnId, String ipAddress) {
        InstanceIdentifierBuilder<TunnelNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(TunnelNexthops.class, new TunnelNexthopsKey(dpnId))
                .child(TunnelNexthop.class, new TunnelNexthopKey(ipAddress));
        InstanceIdentifier<TunnelNexthop> id = idBuilder.build();
        Optional<TunnelNexthop> nextHop = read(LogicalDatastoreType.CONFIGURATION, id);

        if(nextHop.isPresent()) return nextHop.get();
        else return null;
    }

    public long getNextHopPointer(long dpnId, long vpnId, String prefixIp, String nextHopIp) {
        String endpointIp = "10.10.10.1";//interfaceManager.getLocalEndpointIp(dpnId);
        if (nextHopIp.equals(endpointIp)) {
            VpnNexthop vpnNextHop = getVpnNexthop(vpnId, prefixIp);
            return vpnNextHop.getEgressPointer();
        } else {
            TunnelNexthop tunnelNextHop = getTunnelNexthop(dpnId, nextHopIp);
            return tunnelNextHop.getEgressPointer();
        }
    }

    public void removeRemoteNextHop(String ifname, String IpAddress)
    {
        String nhKey = new String("nexthop" + ifname + IpAddress);
        int groupId = 1;//getIdManager().getUniqueId(L3Constants.L3NEXTHOP_GROUPID_POOL, nhKey);

/*        if (getNextHop(groupId) != Null){
            List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
            List<ActionInfo> listActionInfo = null;//nextHop.getActions({output to port});
            BucketInfo bucket = new BucketInfo(listActionInfo);
            listBucketInfo.add(bucket);
            //GroupEntity groupEntity = MDSALUtil.buildGroupEntity
              (dpId, groupId, IPAddress, GroupTypes.GroupIndirect, listBucketInfo);
            //getMdsalApiManager().removeGroup(groupEntity, objTransaction???);

            //update MD-SAL DS
            removeNextHopFromDS(dpId, vpn, ipAddress);
        }else{
            //check update
        }*/
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


    @Override
    public Future<RpcResult<GetEgressPointerOutput>> getEgressPointer(
            GetEgressPointerInput input) {
        long egressGroupId =
                getNextHopPointer(input.getDpnId(), input.getVpnId(), input.getIpPrefix(), input.getNexthopIp());

        GetEgressPointerOutputBuilder output = new GetEgressPointerOutputBuilder();
        output.setEgressPointer(egressGroupId);

        /*RpcResult<GetEgressPointerOutput> result = Rpcs.<GetEgressPointerOutput> getRpcResult(false, output.build());
        return Futures.immediateFuture(result);*/
        return null;
    }

}