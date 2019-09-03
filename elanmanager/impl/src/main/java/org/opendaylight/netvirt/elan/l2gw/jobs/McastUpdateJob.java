/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanItmUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

public class McastUpdateJob extends DataStoreJob {
    private String elanName;
    private String nodeId;
    private ElanL2GatewayMulticastUtils mcastUtils;
    private ElanClusterUtils elanClusterUtils;
    boolean add;
    private IpAddress removedDstTep;
    private boolean dpnOrConnectionRemoved;

    public McastUpdateJob(String elanName,
                          String nodeId,
                          boolean add,
                          ElanL2GatewayMulticastUtils mcastUtils,
                          ElanClusterUtils elanClusterUtils,
                          Scheduler scheduler, JobCoordinator jobCoordinator) {
        super(elanName + ":" + nodeId, scheduler, jobCoordinator);
        this.elanName = elanName;
        this.nodeId = nodeId;
        this.mcastUtils = mcastUtils;
        this.add = add;
        this.elanClusterUtils = elanClusterUtils;
    }

    public void submit() {
        elanClusterUtils.runOnlyInOwnerNode(super.jobKey, "Mcast Update job",this);
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        L2GatewayDevice device = ElanL2GwCacheUtils.getL2GatewayDeviceFromCache(elanName, nodeId);
        ListenableFuture<Void> ft = null;
        //TODO: make prepareRemoteMcastMacUpdateOnDevice return a ListenableFuture<Void>
        if (add) {
            ft = mcastUtils.prepareRemoteMcastMacUpdateOnDevice(elanName, device, !dpnOrConnectionRemoved ,
                    removedDstTep);
        } else {
            ft =  mcastUtils.deleteRemoteMcastMac(new NodeId(nodeId), elanName);
        }
        processResult(ft);
        List<ListenableFuture<Void>> fts = new ArrayList<ListenableFuture<Void>>();
        fts.add(ft);
        return fts;
    }

    public static void updateAllMcasts(String elanName,
                                       ElanL2GatewayMulticastUtils mcastUtils,
                                       ElanClusterUtils elanClusterUtils,
                                       Scheduler scheduler, JobCoordinator jobCoordinator) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            new McastUpdateJob(elanName, nodeId, true, mcastUtils,
                    elanClusterUtils, scheduler, jobCoordinator).submit();
        });
    }

    public static void removeMcastForNode(String elanName, String nodeId,
                                          ElanL2GatewayMulticastUtils mcastUtils,
                                          ElanClusterUtils elanClusterUtils,
                                          Scheduler scheduler, JobCoordinator jobCoordinator) {
        new McastUpdateJob(elanName, nodeId, false, mcastUtils,
                elanClusterUtils, scheduler, jobCoordinator).submit();
    }

    public static void updateMcastForNode(String elanName, String nodeId,
                                          ElanL2GatewayMulticastUtils mcastUtils,
                                          ElanClusterUtils elanClusterUtils,
                                          Scheduler scheduler, JobCoordinator jobCoordinator) {
        new McastUpdateJob(elanName, nodeId, true, mcastUtils,
                elanClusterUtils, scheduler, jobCoordinator).submit();
    }

    private McastUpdateJob setRemovedDstTep(IpAddress removedDstTep) {
        this.removedDstTep = removedDstTep;
        return this;
    }

    private McastUpdateJob setDpnOrconnectionRemoved() {
        this.dpnOrConnectionRemoved = true;
        return this;
    }

    public static void updateAllMcastsForConnectionAdd(String elanName,
                                                       ElanL2GatewayMulticastUtils mcastUtils,
                                                       ElanClusterUtils elanClusterUtils,
                                                       Scheduler scheduler,
                                                       JobCoordinator jobCoordinator) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            new McastUpdateJob(elanName, nodeId, true , mcastUtils, elanClusterUtils, scheduler,
                    jobCoordinator).submit();
        });
    }

    public static void updateAllMcastsForConnectionDelete(String elanName,
                                                          ElanL2GatewayMulticastUtils mcastUtils,
                                                          ElanClusterUtils elanClusterUtils,
                                                          Scheduler scheduler,
                                                          JobCoordinator jobCoordinator,
                                                          L2GatewayDevice deletedDevice) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            IpAddress deletedTep = deletedDevice.getTunnelIp();
            new McastUpdateJob(elanName, nodeId, true , mcastUtils, elanClusterUtils, scheduler, jobCoordinator)
                    .setDpnOrconnectionRemoved()
                    .setRemovedDstTep(deletedTep)
                    .submit();
        });
    }

    public static void updateAllMcastsForDpnAdd(String elanName,
                                                ElanL2GatewayMulticastUtils mcastUtils,
                                                ElanClusterUtils elanClusterUtils,
                                                Scheduler scheduler,
                                                JobCoordinator jobCoordinator) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            new McastUpdateJob(elanName, nodeId, true , mcastUtils, elanClusterUtils, scheduler, jobCoordinator)
                    .submit();
        });
    }

    public static void updateAllMcastsForDpnDelete(String elanName,
                                                   ElanL2GatewayMulticastUtils mcastUtils,
                                                   ElanClusterUtils elanClusterUtils,
                                                   BigInteger srcDpnId,
                                                   ElanItmUtils elanItmUtils,
                                                   Scheduler scheduler,
                                                   JobCoordinator jobCoordinator) {
        ElanL2GwCacheUtils.getInvolvedL2GwDevices(elanName).keySet().forEach(nodeId -> {
            IpAddress deletedTep = elanItmUtils.getSourceDpnTepIp(srcDpnId, new NodeId(nodeId));
            new McastUpdateJob(elanName, nodeId, true , mcastUtils, elanClusterUtils, scheduler, jobCoordinator)
                    .setDpnOrconnectionRemoved()
                    .setRemovedDstTep(deletedTep)
                    .submit();
        });
    }

}