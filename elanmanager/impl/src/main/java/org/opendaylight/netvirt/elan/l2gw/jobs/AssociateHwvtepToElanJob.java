/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.jobs;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayBcGroupUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.ovsdb.utils.southbound.utils.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ekvsver on 4/15/2016.
 */
public class AssociateHwvtepToElanJob implements Callable<List<? extends ListenableFuture<?>>> {
    private static final Logger LOG = LoggerFactory.getLogger(AssociateHwvtepToElanJob.class);

    private final DataBroker dataBroker;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils;
    private final ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils;
    private final L2GatewayDevice l2GatewayDevice;
    private final ElanInstance elanInstance;
    private final Devices l2Device;
    private final Integer defaultVlan;

    public AssociateHwvtepToElanJob(DataBroker dataBroker, ElanL2GatewayUtils elanL2GatewayUtils,
                                    ElanL2GatewayMulticastUtils elanL2GatewayMulticastUtils,
                                    ElanL2GatewayBcGroupUtils elanL2GatewayBcGroupUtils,
                                    L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance,
                                    Devices l2Device, Integer defaultVlan) {
        this.dataBroker = dataBroker;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanL2GatewayMulticastUtils = elanL2GatewayMulticastUtils;
        this.elanL2GatewayBcGroupUtils = elanL2GatewayBcGroupUtils;
        this.l2GatewayDevice = l2GatewayDevice;
        this.elanInstance = elanInstance;
        this.l2Device = l2Device;
        this.defaultVlan = defaultVlan;
        LOG.debug("created assosiate l2gw connection job for {} {} ", elanInstance.getElanInstanceName(),
                l2GatewayDevice.getHwvtepNodeId());
    }

    public String getJobKey() {
        return l2GatewayDevice.getHwvtepNodeId() + HwvtepHAUtil.L2GW_JOB_KEY;
    }

    @Override
    public List<ListenableFuture<?>> call() throws Exception {
        List<ListenableFuture<?>> futures = new ArrayList<>();
        String hwvtepNodeId = l2GatewayDevice.getHwvtepNodeId();
        String elanInstanceName = elanInstance.getElanInstanceName();
        LOG.info("AssociateHwvtepToElanJob Running associate l2gw connection job for {} {} ",
                elanInstanceName, hwvtepNodeId);

        elanL2GatewayUtils.cancelDeleteLogicalSwitch(new NodeId(hwvtepNodeId),
                ElanL2GatewayUtils.getLogicalSwitchFromElan(elanInstanceName));

        // Create Logical Switch if it's not created already in the device
        FluentFuture<? extends @NonNull CommitInfo> lsCreateFuture = createLogicalSwitch();
        futures.add(lsCreateFuture);
        String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(elanInstanceName);

        LogicalSwitchAddedJob logicalSwitchAddedJob =
                new LogicalSwitchAddedJob(elanL2GatewayUtils, elanL2GatewayMulticastUtils,
                        elanL2GatewayBcGroupUtils, logicalSwitchName, l2Device, l2GatewayDevice, defaultVlan);
        futures.addAll(logicalSwitchAddedJob.call());
        return futures;
    }

    private FluentFuture<? extends @NonNull CommitInfo> createLogicalSwitch() {
        final String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(
                elanInstance.getElanInstanceName());
        String segmentationId = ElanUtils.getVxlanSegmentationId(elanInstance).toString();
        String replicationMode = "";

        LOG.trace("logical switch {} is created on {} with VNI {}", logicalSwitchName,
                l2GatewayDevice.getHwvtepNodeId(), segmentationId);
        NodeId hwvtepNodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        String dbVersion = L2GatewayUtils.getConfigDbVersion(dataBroker, hwvtepNodeId);
        try {
            dbVersion =
                dbVersion != null ? dbVersion : HwvtepUtils.getDbVersion(dataBroker, hwvtepNodeId);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Failed to Read Node {} from Oper-Topo for retrieving DB version", hwvtepNodeId);
        }
        if (SouthboundUtils.compareDbVersionToMinVersion(dbVersion, "1.6.0")) {
            replicationMode = "source_node";
        }

        LOG.trace("logical switch {} has schema version {}, replication mode set to {}", logicalSwitchName,
                dbVersion, replicationMode);

        LogicalSwitches logicalSwitch = HwvtepSouthboundUtils.createLogicalSwitch(logicalSwitchName,
                elanInstance.getDescription(), segmentationId, replicationMode);

        FluentFuture<? extends @NonNull CommitInfo> lsCreateFuture =
            HwvtepUtils.addLogicalSwitch(dataBroker, hwvtepNodeId, logicalSwitch);
        Futures.addCallback(lsCreateFuture, new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(CommitInfo noarg) {
                // Listener will be closed after all configuration completed
                // on hwvtep by
                // listener itself
                LOG.trace("Successful in initiating logical switch {} creation", logicalSwitchName);
            }

            @Override
            public void onFailure(Throwable error) {
                LOG.error("Failed logical switch {} creation", logicalSwitchName, error);
            }
        }, MoreExecutors.directExecutor());
        return lsCreateFuture;
    }
}
