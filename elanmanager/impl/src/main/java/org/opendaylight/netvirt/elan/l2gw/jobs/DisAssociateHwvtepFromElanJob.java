/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayBcGroupUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ekvsver on 4/15/2016.
 */
public class DisAssociateHwvtepFromElanJob implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final Scheduler scheduler;
    private final JobCoordinator jobCoordinator;
    private final L2GatewayDevice l2GatewayDevice;
    private final String elanName;
    private final Devices l2Device;
    private final Integer defaultVlan;
    private final boolean isLastL2GwConnDeleted;
    private final NodeId hwvtepNodeId;
    private final String hwvtepNodeIdString;

    public DisAssociateHwvtepFromElanJob(ElanL2GatewayUtils elanL2GatewayUtils,
                                         ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                         ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils,
                                         ElanClusterUtils elanClusterUtils, Scheduler scheduler,
                                         JobCoordinator jobCoordinator,
                                         @Nullable L2GatewayDevice l2GatewayDevice,  String elanName,
                                         Devices l2Device, Integer defaultVlan,
                                         String nodeId, boolean isLastL2GwConnDeleted) {
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanL2GatewayBcGroupUtils = elanL2GatewayBcGroupUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.scheduler = scheduler;
        this.jobCoordinator = jobCoordinator;
        this.l2GatewayDevice = l2GatewayDevice;
        this.elanName = elanName;
        this.l2Device = l2Device;
        this.defaultVlan = defaultVlan;
        this.isLastL2GwConnDeleted = isLastL2GwConnDeleted;
        this.hwvtepNodeId = new NodeId(nodeId);
        this.hwvtepNodeIdString = nodeId;
        LOG.trace("created disassociate l2gw connection job for {}", elanName);
    }

    public String getJobKey() {
        return hwvtepNodeIdString + HwvtepHAUtil.L2GW_JOB_KEY;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        String strHwvtepNodeId = hwvtepNodeId.getValue();
        LOG.info("running disassociate l2gw connection job for elanName:{},strHwvtepNodeId:{},"
            + "isLastL2GwConnDeleted:{}", elanName, strHwvtepNodeId, isLastL2GwConnDeleted);

        List<ListenableFuture<Void>> futures = new ArrayList<>();

        // Remove remote MACs and vlan mappings from physical port
        // Once all above configurations are deleted, delete logical
        // switch
        LOG.trace("delete vlan bindings for {} {}", elanName, strHwvtepNodeId);
        futures.add(elanL2GatewayUtils.deleteVlanBindingsFromL2GatewayDevice(hwvtepNodeId, l2Device, defaultVlan));

        if (isLastL2GwConnDeleted) {
            if (l2GatewayDevice == null) {
                LOG.info("Scheduled delete logical switch {} {}", elanName, strHwvtepNodeId);
                elanL2GatewayUtils.scheduleDeleteLogicalSwitch(hwvtepNodeId,
                        ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName));
                return futures;
            }
            LOG.trace("delete remote ucast macs {} {}", elanName, strHwvtepNodeId);
            elanL2GatewayUtils.deleteElanMacsFromL2GatewayDevice(hwvtepNodeId.getValue(), elanName);

            LOG.trace("delete mcast mac for {} {}", elanName, strHwvtepNodeId);
            McastUpdateJob.removeMcastForNode(elanName, l2GatewayDevice.getHwvtepNodeId(),
                    elanL2GatewayMulticastUtils, elanClusterUtils, scheduler, jobCoordinator);
            elanL2GatewayBcGroupUtils.updateBcGroupForAllDpns(elanName, l2GatewayDevice, false);
            elanL2GatewayMulticastUtils.updateMcastMacsForAllElanDevices(elanName, l2GatewayDevice, false);

//            futures.addAll(elanL2GatewayMulticastUtils.handleMcastForElanL2GwDeviceDelete(this.elanName,
//                    l2GatewayDevice));

            LOG.trace("delete local ucast macs {} {}", elanName, strHwvtepNodeId);
            elanL2GatewayUtils.deleteL2GwDeviceUcastLocalMacsFromElan(l2GatewayDevice, elanName);

            LOG.info("scheduled delete logical switch {} {}", elanName, strHwvtepNodeId);
            elanL2GatewayUtils.scheduleDeleteLogicalSwitch(hwvtepNodeId,
                    ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName));
        } else {
            LOG.trace("l2gw mcast delete not triggered for nodeId {} with elan {}",
                    l2GatewayDevice != null ? l2GatewayDevice.getHwvtepNodeId() : null, elanName);
        }
        return futures;
    }
}
