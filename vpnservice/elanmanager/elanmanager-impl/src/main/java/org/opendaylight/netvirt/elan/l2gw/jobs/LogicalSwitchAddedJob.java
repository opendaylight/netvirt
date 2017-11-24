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
import java.util.function.Supplier;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class LogicalSwitchAddedWorker.
 */
public class LogicalSwitchAddedJob implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger(LogicalSwitchAddedJob.class);

    /** The logical switch name. */
    private final String logicalSwitchName;

    /** The physical device. */
    private final Devices physicalDevice;

    /** The l2 gateway device. */
    private final L2GatewayDevice elanL2GwDevice;

    /** The default vlan id. */
    private final Integer defaultVlanId;

    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final Supplier<ElanInstance> elanInstanceSupplier;

    public LogicalSwitchAddedJob(ElanL2GatewayUtils elanL2GatewayUtils,
                                 ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils, String logicalSwitchName,
                                 Devices physicalDevice, L2GatewayDevice l2GatewayDevice, Integer defaultVlanId,
                                 Supplier<ElanInstance> elanInstanceSupplier) {
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.logicalSwitchName = logicalSwitchName;
        this.physicalDevice = physicalDevice;
        this.elanL2GwDevice = l2GatewayDevice;
        this.defaultVlanId = defaultVlanId;
        this.elanInstanceSupplier = elanInstanceSupplier;
        LOG.debug("created logical switch added job for {} {}", logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
    }

    public String getJobKey() {
        return logicalSwitchName + HwvtepHAUtil.L2GW_JOB_KEY;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        elanL2GatewayUtils.cancelDeleteLogicalSwitch(new NodeId(elanL2GwDevice.getHwvtepNodeId()), logicalSwitchName);
        LOG.debug("running logical switch added job for {} {}", logicalSwitchName,
                elanL2GwDevice.getHwvtepNodeId());
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        LOG.info("creating vlan bindings for {} {}", logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
        futures.add(elanL2GatewayUtils.updateVlanBindingsInL2GatewayDevice(
            new NodeId(elanL2GwDevice.getHwvtepNodeId()), logicalSwitchName, physicalDevice, defaultVlanId));
        LOG.info("creating mast mac entries for {} {}", logicalSwitchName, elanL2GwDevice.getHwvtepNodeId());
        futures.add(elanL2GatewayMulticastUtils.handleMcastForElanL2GwDeviceAdd(logicalSwitchName,
                elanInstanceSupplier.get(), elanL2GwDevice));
        futures.add(elanL2GatewayUtils.installElanMacsInL2GatewayDevice(
                logicalSwitchName, elanL2GwDevice));
        return futures;
    }

}
