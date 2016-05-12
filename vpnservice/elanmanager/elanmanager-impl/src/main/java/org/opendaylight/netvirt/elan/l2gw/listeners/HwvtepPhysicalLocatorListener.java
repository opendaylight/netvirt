/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.collect.Lists;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Listener for physical locator presence in operational datastore
 *
 *
 *
 */
public class HwvtepPhysicalLocatorListener extends
        AsyncClusteredDataChangeListenerBase<TerminationPoint, HwvtepPhysicalLocatorListener> implements AutoCloseable {

    private DataBroker broker;
    private ListenerRegistration<DataChangeListener> lstnerRegistration;

    private static final Logger logger = LoggerFactory.getLogger(HwvtepPhysicalLocatorListener.class);

    public HwvtepPhysicalLocatorListener(DataBroker broker) {
        super(TerminationPoint.class, HwvtepPhysicalLocatorListener.class);

        this.broker = broker;
        registerListener();
        logger.debug("created HwvtepPhysicalLocatorListener");
    }

    static Map<InstanceIdentifier<TerminationPoint>, List<Runnable>> waitingJobsList = new ConcurrentHashMap<>();
    static Map<InstanceIdentifier<TerminationPoint>, Boolean> teps = new ConcurrentHashMap<>();

    public static void runJobAfterPhysicalLocatorIsAvialable(InstanceIdentifier<TerminationPoint> key, Runnable runnable) {
        if (teps.get(key) != null) {
            logger.debug("physical locator already available {} running job ", key);
            runnable.run();
            return;
        }
        synchronized (HwvtepPhysicalLocatorListener.class) {
            List<Runnable> list = waitingJobsList.get(key);
            if (list == null) {
                waitingJobsList.put(key, Lists.newArrayList(runnable));
            } else {
                list.add(runnable);
            }
            logger.debug("added the job to wait list of physical locator {}", key);
        }
    }

    protected void registerListener() {
        try {
            lstnerRegistration = this.broker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                    InstanceIdentifier.create(NetworkTopology.class).child(Topology.class).child(Node.class).
                            child(TerminationPoint.class), this, DataChangeScope.BASE);
        } catch (final Exception e) {
            logger.error("Hwvtep LocalUcasMacs DataChange listener registration failed !", e);
            throw new IllegalStateException("Hwvtep LocalUcasMacs DataChange listener registration failed .", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (lstnerRegistration != null) {
            try {
                lstnerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            lstnerRegistration = null;
        }
    }

    @Override
    protected void remove(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint del) {
        logger.trace("physical locator removed {}", identifier);
        teps.remove(identifier);
    }

    @Override
    protected void update(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint original, TerminationPoint update) {
        logger.trace("physical locator available {}", identifier);
    }

    @Override
    protected void add(InstanceIdentifier<TerminationPoint> identifier, TerminationPoint add) {
        logger.trace("physical locator available {}", identifier);
        teps.put(identifier, true);
        List<Runnable> runnableList = null;
        synchronized (HwvtepPhysicalLocatorListener.class) {
            runnableList = waitingJobsList.get(identifier);
            waitingJobsList.remove(identifier);
        }
        if (runnableList != null) {
            logger.debug("physical locator available {} running jobs ", identifier);
            for (Runnable r : runnableList) {
                r.run();
            }
        } else {
            logger.debug("no jobs are waiting for physical locator {}", identifier);
        }
    }

    @Override
    protected InstanceIdentifier<TerminationPoint> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class).child(Topology.class).child(Node.class).
                child(TerminationPoint.class);
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return HwvtepPhysicalLocatorListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return DataChangeScope.BASE;
    }
}
