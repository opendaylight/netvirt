/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.utils.Scheduler;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanGroupCache extends AbstractClusteredAsyncDataTreeChangeListener<Group> {
    private static final Logger LOG = LoggerFactory.getLogger(ElanGroupCache.class);
    private final DataBroker dataBroker;
    private final Scheduler scheduler;
    private final Map<InstanceIdentifier<Group>, Group> groupsById = new ConcurrentHashMap<>();
    private final Map<InstanceIdentifier<Group>, Collection<Runnable>> waitingJobs = new ConcurrentHashMap<>();

    @Inject
    public ElanGroupCache(final DataBroker dataBroker, final Scheduler scheduler) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Nodes.class)
                .child(Node.class).augmentation(FlowCapableNode.class).child(Group.class),
                Executors.newListeningSingleThreadExecutor("ElanGroupCache", LOG));
        this.dataBroker = dataBroker;
        this.scheduler = scheduler;
    }

    public synchronized void init() {
        LOG.info("{} registered", getClass().getSimpleName());
    }

    public synchronized void addJobToWaitList(InstanceIdentifier<Group> key,
                                              Runnable job) {
        if (groupsById.containsKey(key)) {
            job.run();
        } else {
            waitingJobs.putIfAbsent(key, new ArrayList<>());
            waitingJobs.get(key).add(job);
        }
    }

    @Override
    public synchronized void remove(InstanceIdentifier<Group> key, Group deleted) {
        groupsById.remove(key);
    }

    @Override
    public void update(InstanceIdentifier<Group> key, Group old, Group updated) {
        add(key, updated);
    }

    @Override
    public synchronized void add(InstanceIdentifier<Group> key, Group added) {
        if (groupsById.containsKey(key)) {
            groupsById.put(key, added);
            return;
        }
        scheduler.getScheduledExecutorService().schedule(() -> {
            groupsById.put(key, added);
            Collection<Runnable> jobs = waitingJobs.remove(key);
            if (jobs == null) {
                return;
            }
            for (Runnable job : jobs) {
                job.run();
            }
        }, ElanInterfaceManager.WAIT_TIME_FOR_SYNC_INSTALL, TimeUnit.MILLISECONDS);
    }

    public Optional<Group> getGroup(InstanceIdentifier<Group> key) throws InterruptedException, ExecutionException {
        if (groupsById.containsKey(key)) {
            return Optional.of(groupsById.get(key));
        }
        ReadTransaction transaction = dataBroker.newReadOnlyTransaction();
        Optional<Group> optional = transaction.read(LogicalDatastoreType.CONFIGURATION, key).get();
        transaction.close();
        return optional;
    }
}
