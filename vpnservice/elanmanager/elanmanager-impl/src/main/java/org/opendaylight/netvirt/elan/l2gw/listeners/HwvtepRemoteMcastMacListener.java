/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.elan.utils.ElanConstants;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * The listener class for listening to {@code RemoteMcastMacs}
 * add/delete/update.
 *
 * @see RemoteMcastMacs
 */
public class HwvtepRemoteMcastMacListener
        extends AsyncClusteredDataChangeListenerBase<RemoteMcastMacs, HwvtepRemoteMcastMacListener> {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(HwvtepRemoteMcastMacListener.class);

    /** The node id. */
    private NodeId nodeId;

    private List<IpAddress> expectedPhyLocatorIps;

    DataBroker broker;

    String logicalSwitchName;

    AtomicBoolean executeTask = new AtomicBoolean(true);

    Callable<List<ListenableFuture<Void>>> taskToRun;

    static DataStoreJobCoordinator dataStoreJobCoordinator;

    public static void setDataStoreJobCoordinator(DataStoreJobCoordinator ds) {
        dataStoreJobCoordinator = ds;
    }

    /**
     * Instantiates a new remote mcast mac listener.
     *
     * @param broker
     *            the mdsal databroker reference
     * @param logicalSwitchName
     *            the logical switch name
     * @param l2GatewayDevice
     *            the l2 gateway device
     * @param expectedPhyLocatorIps
     *            the expected phy locator ips
     * @param task
     *            the task to be run upon data presence
     * @throws Exception
     *             the exception
     */
    public HwvtepRemoteMcastMacListener(DataBroker broker, String logicalSwitchName, L2GatewayDevice l2GatewayDevice,
            List<IpAddress> expectedPhyLocatorIps, Callable<List<ListenableFuture<Void>>> task) throws Exception {
        super(RemoteMcastMacs.class, HwvtepRemoteMcastMacListener.class);
        this.nodeId = new NodeId(l2GatewayDevice.getHwvtepNodeId());
        this.broker = broker;
        this.taskToRun = task;
        this.logicalSwitchName = logicalSwitchName;
        this.expectedPhyLocatorIps = expectedPhyLocatorIps;
        LOG.info("registering the listener for mcast mac ");
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
        LOG.info("registered the listener for mcast mac ");
        if (isDataPresentInOpDs(getWildCardPath())) {
            LOG.info("mcast mac already present running the task ");
            if (executeTask.compareAndSet(true, false)) {
                runTask();
            }
        }
    }

    private boolean isDataPresentInOpDs(InstanceIdentifier<RemoteMcastMacs> path) throws Exception {
        Optional<RemoteMcastMacs> mac = null;
        try {
            mac = ElanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, path);
        } catch (Throwable e) {
        }
        if (mac == null || !mac.isPresent()) {
            return false;
        }
        if (this.expectedPhyLocatorIps != null && !this.expectedPhyLocatorIps.isEmpty()) {
            RemoteMcastMacs remoteMcastMac = mac.get();
            if (remoteMcastMac.getLocatorSet() == null || remoteMcastMac.getLocatorSet().isEmpty()) {
                return false;
            }
            for (IpAddress ip : this.expectedPhyLocatorIps) {
                boolean ipExists = ElanL2GatewayUtils.checkIfPhyLocatorAlreadyExistsInRemoteMcastEntry(this.nodeId,
                        remoteMcastMac, ip);
                if (!ipExists) {
                    LOG.trace("IP [{}] not found in RemoteMcastMacs for node [{}]", String.valueOf(ip.getValue()),
                            this.nodeId.getValue());
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
     * getWildCardPath()
     */
    @Override
    public InstanceIdentifier<RemoteMcastMacs> getWildCardPath() {
        return HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(nodeId,
                logicalSwitchName, new MacAddress(ElanConstants.UNKNOWN_DMAC));
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.opendaylight.genius.datastoreutils.AsyncDataChangeListenerBase#
     * getDataChangeListener()
     */
    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return HwvtepRemoteMcastMacListener.this;
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
    protected void remove(InstanceIdentifier<RemoteMcastMacs> identifier, RemoteMcastMacs deleted) {
        LOG.trace("Received Remove DataChange Notification for identifier: {}, RemoteMcastMacs: {}", identifier,
                deleted);
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
    protected void update(InstanceIdentifier<RemoteMcastMacs> identifier, RemoteMcastMacs old,
            RemoteMcastMacs newdata) {
        LOG.trace("Received Update DataChange Notification for identifier: {}, RemoteMcastMacs old: {}, new: {}."
                + "No Action Performed.", identifier, old, newdata);
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
    protected void add(InstanceIdentifier<RemoteMcastMacs> identifier, RemoteMcastMacs mcastMac) {
        LOG.debug("Received Add DataChange Notification for identifier: {}, RemoteMcastMacs: {}", identifier, mcastMac);
        // No isDataPresentInOpDs check is done as assuming all the expected phy
        // locator ips will be available during add
        if (executeTask.compareAndSet(true, false)) {
            runTask();
        }
    }

    void runTask() {
        try {

            String jobKey = ElanL2GatewayUtils.getL2GatewayConnectionJobKey(nodeId.getValue(), nodeId.getValue());
            dataStoreJobCoordinator.enqueueJob(jobKey, taskToRun, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        } catch (Exception e) {
            LOG.error("Failed to handle remote mcast mac - add: {}", e);
        } finally {
            try {
                close();
            } catch (Exception e) {
                LOG.warn("Failed to close McastMacSwitchListener: {}", e);
            }
        }
    }
}
