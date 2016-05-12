/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.netvirt.elan.l2gw.jobs.LogicalSwitchAddedJob;
import org.opendaylight.netvirt.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.LogicalSwitches;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /** Id of L2 Gateway connection responsible for this logical switch creation */
    private Uuid l2GwConnId;

    static DataStoreJobCoordinator dataStoreJobCoordinator;

    public static void setDataStoreJobCoordinator(DataStoreJobCoordinator ds) {
        dataStoreJobCoordinator = ds;
    }

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
     * @param l2GwConnId
     *            the l2 gateway connection id
     */
    public HwvtepLogicalSwitchListener(L2GatewayDevice l2GatewayDevice, String logicalSwitchName,
            Devices physicalDevice, Integer defaultVlanId, Uuid l2GwConnId) {
        super(LogicalSwitches.class, HwvtepLogicalSwitchListener.class);
        this.nodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        this.logicalSwitchName = logicalSwitchName;
        this.physicalDevice = physicalDevice;
        this.l2GatewayDevice = l2GatewayDevice;
        this.defaultVlanId = defaultVlanId;
        this.l2GwConnId = l2GwConnId;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
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
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
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
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
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
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
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
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
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
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
     * add(org.opendaylight.yangtools.yang.binding.InstanceIdentifier,
     * org.opendaylight.yangtools.yang.binding.DataObject)
     */
    @Override
    protected void add(InstanceIdentifier<LogicalSwitches> identifier, LogicalSwitches logicalSwitchNew) {
        LOG.debug("Received Add DataChange Notification for identifier: {}, LogicalSwitches: {}", identifier,
                logicalSwitchNew);
        try {
            L2GatewayDevice elanDevice = L2GatewayConnectionUtils.addL2DeviceToElanL2GwCache(
                    logicalSwitchNew.getHwvtepNodeName().getValue(), l2GatewayDevice, l2GwConnId);

            LogicalSwitchAddedJob logicalSwitchAddedWorker = new LogicalSwitchAddedJob(
                    logicalSwitchName, physicalDevice, elanDevice, defaultVlanId);
            dataStoreJobCoordinator.enqueueJob(logicalSwitchAddedWorker.getJobKey(), logicalSwitchAddedWorker,
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

}