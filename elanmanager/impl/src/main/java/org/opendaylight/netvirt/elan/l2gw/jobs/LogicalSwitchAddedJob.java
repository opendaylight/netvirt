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

import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayBcGroupUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class LogicalSwitchAddedWorker.
 */
public class LogicalSwitchAddedJob implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger("HwvtepEventLogger");

    /** The logical switch name. */
    private final String logicalSwitchName;

    /** The physical device. */
    private final Devices physicalDevice;

    /** The l2 gateway device. */
    private final L2GatewayDevice elanL2GwDevice;

    /** The default vlan id. */
    private Integer defaultVlanId;

    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils;

    public LogicalSwitchAddedJob(ElanL2GatewayUtils elanL2GatewayUtils,
                                 ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                 ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils,
                                 String logicalSwitchName, Devices physicalDevice,
                                 L2GatewayDevice l2GatewayDevice, Integer defaultVlanId) {
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanL2GatewayBcGroupUtils = elanL2GatewayBcGroupUtils;
        this.logicalSwitchName = logicalSwitchName;
        this.physicalDevice = physicalDevice;
        this.elanL2GwDevice = l2GatewayDevice;
        this.defaultVlanId = defaultVlanId;
        LOG.debug("created logical switch added job for {} {}", logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
    }

    public String getJobKey() {
//        return logicalSwitchName + HwvtepHAUtil.L2GW_JOB_KEY;
        return logicalSwitchName + ":" + elanL2GwDevice.getHwvtepNodeId();
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        elanL2GatewayUtils.cancelDeleteLogicalSwitch(new NodeId(elanL2GwDevice.getHwvtepNodeId()), logicalSwitchName);
        LOG.info("LogicalSwitchAddedJob Running logical switch added job for {} {}", logicalSwitchName,
                elanL2GwDevice.getHwvtepNodeId());
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        ListenableFuture<Void> ft = null;
        //String elan = elanL2GatewayUtils.getElanFromLogicalSwitch(logicalSwitchName);

        LOG.trace("LogicalSwitchAddedJob Creating vlan bindings for {} {}",
                logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
        ft = elanL2GatewayUtils.updateVlanBindingsInL2GatewayDevice(
                new NodeId(elanL2GwDevice.getHwvtepNodeId()), logicalSwitchName, physicalDevice, defaultVlanId);
        futures.add(ft);
        //logResultMsg(ft);
        LOG.trace("LogicalSwitchAddedJob Creating mast mac entries and bc group for {} {}",
                logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
        elanL2GatewayBcGroupUtils.updateBcGroupForAllDpns(logicalSwitchName, elanL2GwDevice, true);
        elanL2GatewayMulticastUtils.updateMcastMacsForAllElanDevices(logicalSwitchName, elanL2GwDevice, true);
        futures.addAll(elanL2GatewayUtils.installElanMacsInL2GatewayDevice(
                logicalSwitchName, elanL2GwDevice));
        return futures;
    }

    /*private void logResultMsg(ListenableFuture<Void> ft) {
        String portName = null;
        if (physicalDevice.getInterfaces() != null && !physicalDevice.getInterfaces().isEmpty()) {
            portName = physicalDevice.getInterfaces().get(0).getInterfaceName();
            if (physicalDevice.getInterfaces().get(0).getSegmentationIds() != null
                    && !physicalDevice.getInterfaces().get(0).getSegmentationIds().isEmpty()) {
                defaultVlanId = physicalDevice.getInterfaces().get(0).getSegmentationIds().get(0);
            }
        }
        if (portName != null && defaultVlanId != null) {
            new FtCallback(ft, "Added vlan bindings {} logical switch {} to node {}",
                    portName + ":" + defaultVlanId, logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
        }
    }*/

}
