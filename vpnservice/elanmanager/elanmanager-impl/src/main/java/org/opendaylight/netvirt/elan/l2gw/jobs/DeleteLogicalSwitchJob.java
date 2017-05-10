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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class DeleteLogicalSwitchJob.
 */
public class DeleteLogicalSwitchJob implements Callable<List<ListenableFuture<Void>>> {
    private DataBroker broker;

    /** The logical switch name. */
    private String logicalSwitchName;

    /** The physical device. */
    private NodeId hwvtepNodeId;

    private final ElanL2GatewayUtils elanL2GatewayUtils;

    private static final Logger LOG = LoggerFactory.getLogger(DeleteLogicalSwitchJob.class);

    public DeleteLogicalSwitchJob(DataBroker broker, ElanL2GatewayUtils elanL2GatewayUtils , NodeId hwvtepNodeId,
                                  String logicalSwitchName) {
        this.broker = broker;
        this.hwvtepNodeId = hwvtepNodeId;
        this.logicalSwitchName = logicalSwitchName;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        LOG.debug("created logical switch deleted job for {} on {}", logicalSwitchName, hwvtepNodeId);
    }

    public String getJobKey() {
        return logicalSwitchName;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        LOG.debug("running logical switch deleted job for {} in {}", logicalSwitchName, hwvtepNodeId);
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(HwvtepUtils.deleteLogicalSwitch(broker, hwvtepNodeId, logicalSwitchName));
        /*
            Adding extra code to delete entries related to logicalswitch , in case they exist while deleting
            logicalSwitch.
            In UT , while macs are being pushed from southbound in to l2gw Device and at the same time l2Gw Connection
            is deleted , the config tree is left with stale entries .
            Hence to remove the stale entries for this use case , deleting all entries related to logicalSwitch upon
            deleting L2gwConnecton.
         */
        elanL2GatewayUtils.deleteElanMacsFromL2GatewayDevice(hwvtepNodeId.getValue(), logicalSwitchName);
        InstanceIdentifier<LogicalSwitches> logicalSwitch = HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(hwvtepNodeId, new HwvtepNodeName(logicalSwitchName));
        RemoteMcastMacsKey remoteMcastMacsKey = new RemoteMcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch),
                new MacAddress(ElanConstants.UNKNOWN_DMAC));
        HwvtepUtils.deleteRemoteMcastMac(broker, hwvtepNodeId, remoteMcastMacsKey);
        return futures;
    }
}
