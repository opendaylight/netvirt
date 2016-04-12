/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.l2gw.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.vpnservice.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.vpnservice.elan.utils.ElanUtils;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.vpnservice.utils.SystemPropertyReader;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * The listener class for listening to {@code LogicalSwitches}
 * add/delete/update.
 *
 * @see LogicalSwitches
 */
public class HwvtepLogicalSwitchListener
        extends AsyncDataChangeListenerBase<LogicalSwitches, HwvtepLogicalSwitchListener> {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepLogicalSwitchListener.class);

    /** The node id. */
    private NodeId nodeId;

    /** The logical switch name. */
    private String logicalSwitchName;

    /** The physical device. */
    private Devices physicalDevice;

    /** The l2 gateway device. */
    private L2GatewayDevice l2GatewayDevice;

    /** The default vlan id. */
    private Integer defaultVlanId;

    /**
     * Instantiates a new hardware vtep logical switch listener.
     *
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @param logicalSwitchName
     *            the logical switch name
     * @param physicalDevice
     *            the physical device
     * @param defaultVlanId
     *            the default vlan id
     */
    public HwvtepLogicalSwitchListener(L2GatewayDevice l2GatewayDevice, String logicalSwitchName,
            Devices physicalDevice, Integer defaultVlanId) {
        super(LogicalSwitches.class, HwvtepLogicalSwitchListener.class);
        this.nodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        this.logicalSwitchName = logicalSwitchName;
        this.physicalDevice = physicalDevice;
        this.l2GatewayDevice = l2GatewayDevice;
        this.defaultVlanId = defaultVlanId;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase#
     * getWildCardPath()
     */
    @Override
	public InstanceIdentifier<LogicalSwitches> getWildCardPath() {
        return HwvtepSouthboundUtils.createLogicalSwitchesInstanceIdentifier(nodeId,
                new HwvtepNodeName(logicalSwitchName));
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase#
     * getDataChangeListener()
     */
    @Override
    protected DataChangeListener getDataChangeListener() {
        return HwvtepLogicalSwitchListener.this;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase#
     * getDataChangeScope()
     */
    @Override
    protected AsyncDataBroker.DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase#
     * remove(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void remove(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches deletedLogicalSwitch) {
        LOG.trace("Received Remove DataChange Notification for identifier: {}, LogicalSwitches: {}", identifier,
                deletedLogicalSwitch);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase#
     * update(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void update(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches logicalSwitchOld,
            LogicalSwitches logicalSwitchNew) {
        LOG.trace("Received Update DataChange Notification for identifier: {}, LogicalSwitches old: {}, new: {}."
                + "No Action Performed.", identifier, logicalSwitchOld, logicalSwitchNew);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.vpnservice.datastoreutils.AsyncDataChangeListenerBase#
     * add(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void add(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches logicalSwitchNew) {
        LOG.debug("Received Add DataChange Notification for identifier: {}, LogicalSwitches: {}", identifier,
                logicalSwitchNew);
        try {
            L2GatewayConnectionUtils.addL2DeviceToElanL2GwCache(logicalSwitchNew.getHwvtepNodeName().getValue(), l2GatewayDevice);
            DataStoreJobCoordinator jobCoordinator = DataStoreJobCoordinator.getInstance();
            LogicalSwitchAddedWorker logicalSwitchAddedWorker = new LogicalSwitchAddedWorker(nodeId, logicalSwitchNew);
            String jobKey = ElanL2GatewayUtils.getL2GatewayConnectionJobKey(nodeId.getValue(),
                    logicalSwitchNew.getHwvtepNodeName().getValue());
            jobCoordinator.enqueueJob(jobKey, logicalSwitchAddedWorker,
                    SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());

        } catch (Exception e) {
            LOG.error("Failed to handle HwVTEPLogicalSwitch - add: {}", e);
        } finally {
            try {
                // This listener is specific to handle a specific logical
                // switch, hence closing it.
                LOG.trace("Closing LogicalSwitches listener for node: {}, logicalSwitch: {}", nodeId.getValue(),
                        logicalSwitchName);
                close();
            } catch (Exception e) {
                LOG.warn("Failed to close HwVTEPLogicalSwitchListener: {}", e);
            }
        }
    }

    /**
     * The Class LogicalSwitchAddedWorker.
     */
    private class LogicalSwitchAddedWorker implements Callable<List<ListenableFuture<Void>>> {
        /** The logical switch new. */
        LogicalSwitches logicalSwitchNew;

        /**
         * Instantiates a new logical switch added worker.
         *
         * @param nodeId
         *            the node id
         * @param logicalSwitchNew
         *            the logical switch new
         */
        public LogicalSwitchAddedWorker(NodeId nodeId, LogicalSwitches logicalSwitchNew) {
            this.logicalSwitchNew = logicalSwitchNew;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.concurrent.Callable#call()
         */
        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            try {
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                String elan = ElanL2GatewayUtils.getElanFromLogicalSwitch(logicalSwitchName);
                final L2GatewayDevice elanL2GwDevice = ElanL2GwCacheUtils
                        .getL2GatewayDeviceFromCache(elan, l2GatewayDevice.getHwvtepNodeId());
                if (elanL2GwDevice == null) {
                    LOG.error("Could not find L2GatewayDevice for ELAN: {}, nodeID:{} from cache",
                            l2GatewayDevice.getHwvtepNodeId());
                    return null;
                } else {
                    LOG.trace("got logical switch device {}", elanL2GwDevice);
                    futures.add(ElanL2GatewayUtils.updateVlanBindingsInL2GatewayDevice(
                            new NodeId(elanL2GwDevice.getHwvtepNodeId()), logicalSwitchName, physicalDevice, defaultVlanId));
                    futures.add(ElanL2GatewayMulticastUtils.handleMcastForElanL2GwDeviceAdd(logicalSwitchName, elanL2GwDevice));

                    HwvtepRemoteMcastMacListener list = new HwvtepRemoteMcastMacListener(ElanUtils.getDataBroker(),
                            logicalSwitchName, elanL2GwDevice,
                            new Callable<List<ListenableFuture<Void>>>() {

                            @Override
                            public List<ListenableFuture<Void>> call() {
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                futures.add(ElanL2GatewayUtils.installElanMacsInL2GatewayDevice(
                                        logicalSwitchName, elanL2GwDevice));
                                return futures;
                            }}
                        );
                    return futures;
                }
            } catch (Throwable e) {
                LOG.error("failed to add ls ", e);
                return null;
            }
        }

    }
}