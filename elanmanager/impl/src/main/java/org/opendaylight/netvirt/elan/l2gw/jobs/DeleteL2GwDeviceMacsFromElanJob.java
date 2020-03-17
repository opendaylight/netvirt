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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager.ShardResource;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Job class to delete L2 gateway device local ucast macs from other Elan L2
 * gateway devices.
 */
public class DeleteL2GwDeviceMacsFromElanJob implements Callable<List<? extends ListenableFuture<?>>> {

    /** The Constant JOB_KEY_PREFIX. */
    private static final String JOB_KEY_PREFIX = "hwvtep:";

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(DeleteL2GwDeviceMacsFromElanJob.class);

    /** The elan name. */
    private final String elanName;

    /** The l2 gw device. */
    private final L2GatewayDevice l2GwDevice;

    /** The mac addresses. */
    private final Collection<MacAddress> macAddresses;

    /**
     * Instantiates a new delete l2 gw device macs from elan job.
     *
     * @param elanName
     *            the elan name
     * @param l2GwDevice
     *            the l2 gw device
     * @param macAddresses
     *            the mac addresses
     */
    public DeleteL2GwDeviceMacsFromElanJob(String elanName, L2GatewayDevice l2GwDevice,
                                           Collection<MacAddress> macAddresses) {
        this.elanName = elanName;
        this.l2GwDevice = l2GwDevice;
        this.macAddresses = macAddresses;
    }

    /**
     * Gets the job key.
     *
     * @return the job key
     */
    public String getJobKey() {
        String jobKey = JOB_KEY_PREFIX + this.elanName;
        if (macAddresses != null && macAddresses.size() == 1) {
            jobKey += ":" + macAddresses.iterator().next().getValue();
        }
        return jobKey;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.Callable#call()
     */
    @Override
    public List<ListenableFuture<Void>> call() {
        LOG.debug("Deleting l2gw device [{}] macs from other l2gw devices for elan [{}]",
                this.l2GwDevice.getHwvtepNodeId(), this.elanName);
        final String logicalSwitchName = ElanL2GatewayUtils.getLogicalSwitchFromElan(this.elanName);
        List<MacAddress> macs = new ArrayList<>();
        macAddresses.forEach((mac) -> macs.add(new MacAddress(mac.getValue().toLowerCase(Locale.ENGLISH))));

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (L2GatewayDevice otherDevice : ElanL2GwCacheUtils.getInvolvedL2GwDevices(this.elanName)) {
            if (!otherDevice.getHwvtepNodeId().equals(this.l2GwDevice.getHwvtepNodeId())
                    && !ElanL2GatewayUtils.areMLAGDevices(this.l2GwDevice, otherDevice)) {
                final String hwvtepId = otherDevice.getHwvtepNodeId();
                //This delete is batched using resourcebatchingmnagaer
                futures.addAll(deleteRemoteUcastMacs(new NodeId(hwvtepId), logicalSwitchName, macs));
            }
        }
        return futures;
    }

    /**
     *  Batched operation to delete list of Uast mac from the given nodeId and logical Switch .
     * @param nodeId NodeId of device
     * @param logicalSwitchName logicalSwitch Name
     * @param lstMac list of macs to be deleted
     * @return list of futures
     */
    public static List<ListenableFuture<Void>> deleteRemoteUcastMacs(final NodeId nodeId,
                                             String logicalSwitchName, final List<MacAddress> lstMac) {
        if (lstMac != null) {
            return lstMac.stream()
                .map(mac -> HwvtepSouthboundUtils.createRemoteUcastMacsInstanceIdentifier(
                        nodeId, logicalSwitchName, mac))
                .map(iid -> ResourceBatchingManager.getInstance().delete(ShardResource.CONFIG_TOPOLOGY, iid))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
