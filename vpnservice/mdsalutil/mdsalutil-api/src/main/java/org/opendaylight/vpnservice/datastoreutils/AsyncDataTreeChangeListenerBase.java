/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.datastoreutils;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AsyncDataTreeChangeListenerBase<T extends DataObject, K extends DataTreeChangeListener> implements DataTreeChangeListener<T>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncDataTreeChangeListenerBase.class);

    private static final int DATATREE_CHANGE_HANDLER_THREAD_POOL_CORE_SIZE = 1;
    private static final int DATATREE_CHANGE_HANDLER_THREAD_POOL_MAX_SIZE = 1;
    private static final int DATATREE_CHANGE_HANDLER_THREAD_POOL_KEEP_ALIVE_TIME_SECS = 300;
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    private ListenerRegistration<K> listenerRegistration;

    private static ThreadPoolExecutor dataTreeChangeHandlerExecutor = new ThreadPoolExecutor(
            DATATREE_CHANGE_HANDLER_THREAD_POOL_CORE_SIZE,
            DATATREE_CHANGE_HANDLER_THREAD_POOL_MAX_SIZE,
            DATATREE_CHANGE_HANDLER_THREAD_POOL_KEEP_ALIVE_TIME_SECS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    protected final Class<T> clazz;
    private final Class<K> eventClazz;

    public AsyncDataTreeChangeListenerBase(Class<T> clazz, Class<K> eventClazz) {
        this.clazz = Preconditions.checkNotNull(clazz, "Class can not be null!");
        this.eventClazz = Preconditions.checkNotNull(eventClazz, "eventClazz can not be null!");
    }

    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<T>> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        DataTreeChangeHandler dataTreeChangeHandler = new DataTreeChangeHandler(changes);
        dataTreeChangeHandlerExecutor.execute(dataTreeChangeHandler);
    }

    public void registerListener(LogicalDatastoreType dsType, final DataBroker db) {
        final DataTreeIdentifier<T> treeId = new DataTreeIdentifier<>(dsType, getWildCardPath());
        try {
            TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
            listenerRegistration = looper.loopUntilNoException(new Callable<ListenerRegistration<K>>() {
                @Override
                public ListenerRegistration<K> call() throws Exception {
                    return db.registerDataTreeChangeListener(treeId, getDataTreeChangeListener());
                }
            });
        } catch (final Exception e) {
            LOG.warn("{}: Data Tree Change listener registration failed.", eventClazz.getName());
            LOG.debug("{}: Data Tree Change listener registration failed: {}", eventClazz.getName(), e);
            throw new IllegalStateException( eventClazz.getName() + "{}startup failed. System needs restart.", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataTreeChangeListener.", e);
            }
            listenerRegistration = null;
        }
    }

    protected abstract InstanceIdentifier<T> getWildCardPath();
    protected abstract void remove(InstanceIdentifier<T> key, T dataObjectModification);
    protected abstract void update(InstanceIdentifier<T> key, T dataObjectModificationBefore, T dataObjectModificationAfter);
    protected abstract void add(InstanceIdentifier<T> key, T dataObjectModification);
    protected abstract K getDataTreeChangeListener();

    public class DataTreeChangeHandler implements Runnable {
        Collection<DataTreeModification<T>> changes;

        public DataTreeChangeHandler(Collection<DataTreeModification<T>> changes) {
            this.changes = changes;
        }



        @Override
        public void run() {
            for (DataTreeModification<T> change : changes) {
                final InstanceIdentifier<T> key = change.getRootPath().getRootIdentifier();
                final DataObjectModification<T> mod = change.getRootNode();

                switch (mod.getModificationType()) {
                    case DELETE:
                        remove(key, mod.getDataBefore());
                        break;
                    case SUBTREE_MODIFIED:
                        update(key, mod.getDataBefore(), mod.getDataAfter());
                        break;
                    case WRITE:
                        if (mod.getDataBefore() == null) {
                            add(key, mod.getDataAfter());
                        } else {
                            update(key, mod.getDataBefore(), mod.getDataAfter());
                        }
                        break;
                    default:
                        // FIXME: May be not a good idea to throw.
                        throw new IllegalArgumentException("Unhandled modification type " + mod.getModificationType());
                }
            }
        }
    }
}