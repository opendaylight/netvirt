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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
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
    private final boolean clearUcast;
    private volatile boolean cancelled = false;

    private static final Logger LOG = LoggerFactory.getLogger(DeleteLogicalSwitchJob.class);

    public DeleteLogicalSwitchJob(DataBroker broker, ElanL2GatewayUtils elanL2GatewayUtils,
                                  NodeId hwvtepNodeId, String logicalSwitchName, boolean clearUcast) {
        this.broker = broker;
        this.hwvtepNodeId = hwvtepNodeId;
        this.logicalSwitchName = logicalSwitchName;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.clearUcast = clearUcast;
        LOG.debug("created logical switch deleted job for {} on {}", logicalSwitchName, hwvtepNodeId);
    }

    public void cancel() {
        this.cancelled = true;
    }

    public String getJobKey() {
        return logicalSwitchName;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        if (cancelled) {
            LOG.info("Delete logical switch job cancelled ");
            return Collections.emptyList();
        }
        LOG.debug("running logical switch deleted job for {} in {}", logicalSwitchName, hwvtepNodeId);
        elanL2GatewayUtils.deleteElanMacsFromL2GatewayDevice(hwvtepNodeId.getValue(), logicalSwitchName);
        InstanceIdentifier<LogicalSwitches> logicalSwitch = HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(hwvtepNodeId, new HwvtepNodeName(logicalSwitchName));
        RemoteMcastMacsKey remoteMcastMacsKey = new RemoteMcastMacsKey(new HwvtepLogicalSwitchRef(logicalSwitch),
                new MacAddress(ElanConstants.UNKNOWN_DMAC));
        HwvtepUtils.deleteRemoteMcastMac(broker, hwvtepNodeId, remoteMcastMacsKey);

        L2GatewayDevice l2GatewayDevice = new L2GatewayDevice("");
        l2GatewayDevice.setHwvtepNodeId(hwvtepNodeId.getValue());

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        futures.add(HwvtepUtils.deleteLogicalSwitch(broker, hwvtepNodeId, logicalSwitchName));
        if (clearUcast) {
            LOG.trace("Clearing the local ucast macs of device {} macs ", hwvtepNodeId.getValue());
            elanL2GatewayUtils.deleteL2GwDeviceUcastLocalMacsFromElan(l2GatewayDevice, logicalSwitchName);
        }
        return futures;
    }
}
