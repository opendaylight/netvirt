/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.datastoreutils;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AsyncDataChangeListenerBase<T extends DataObject, K extends DataChangeListener> implements DataChangeListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncDataChangeListenerBase.class);

    private static final int DATATREE_CHANGE_HANDLER_THREAD_POOL_CORE_SIZE = 1;
    private static final int DATATREE_CHANGE_HANDLER_THREAD_POOL_MAX_SIZE = 1;
    private static final int DATATREE_CHANGE_HANDLER_THREAD_POOL_KEEP_ALIVE_TIME_SECS = 300;
    private static final int STARTUP_LOOP_TICK = 500;
    private static final int STARTUP_LOOP_MAX_RETRIES = 8;

    private static ThreadPoolExecutor dataChangeHandlerExecutor = new ThreadPoolExecutor(
            DATATREE_CHANGE_HANDLER_THREAD_POOL_CORE_SIZE,
            DATATREE_CHANGE_HANDLER_THREAD_POOL_MAX_SIZE,
            DATATREE_CHANGE_HANDLER_THREAD_POOL_KEEP_ALIVE_TIME_SECS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    private ListenerRegistration<K> listenerRegistration;
    protected final Class<T> clazz;
    private final Class<K> eventClazz;

    /**
     * @param clazz - for which the data change event is received
     */
    public AsyncDataChangeListenerBase(Class<T> clazz, Class<K> eventClazz) {
        this.clazz = Preconditions.checkNotNull(clazz, "Class can not be null!");
        this.eventClazz = Preconditions.checkNotNull(eventClazz, "eventClazz can not be null!");
    }

    public void registerListener(final LogicalDatastoreType dsType, final DataBroker db) {
        try {
            TaskRetryLooper looper = new TaskRetryLooper(STARTUP_LOOP_TICK, STARTUP_LOOP_MAX_RETRIES);
            listenerRegistration = looper.loopUntilNoException(new Callable<ListenerRegistration<K>>() {
                @Override
                public ListenerRegistration call() throws Exception {
                    return db.registerDataChangeListener(dsType, getWildCardPath(), getDataChangeListener(), getDataChangeScope());
                }
            });
        } catch (final Exception e) {
            LOG.warn("{}: Data Tree Change listener registration failed.", eventClazz.getName());
            LOG.debug("{}: Data Tree Change listener registration failed: {}", eventClazz.getName(), e);
            throw new IllegalStateException( eventClazz.getName() + "{}startup failed. System needs restart.", e);
        }
    }

    @Override
    public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changeEvent) {
        if (changeEvent == null) {
            return;
        }

        DataChangeHandler dataChangeHandler = new DataChangeHandler(changeEvent);
        dataChangeHandlerExecutor.execute(dataChangeHandler);
    }

    @SuppressWarnings("unchecked")
    private void createData(final Map<InstanceIdentifier<?>, DataObject> createdData) {
        final Set<InstanceIdentifier<?>> keys = createdData.keySet() != null
                ? createdData.keySet() : Collections.<InstanceIdentifier<?>>emptySet();
        for (InstanceIdentifier<?> key : keys) {
            if (clazz.equals(key.getTargetType())) {
                InstanceIdentifier<T> createKeyIdent = key.firstIdentifierOf(clazz);
                final Optional<DataObject> value = Optional.of(createdData.get(key));
                if (value.isPresent()) {
                    this.add(createKeyIdent, (T)value.get());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateData(final Map<InstanceIdentifier<?>, DataObject> updateData,
                            final Map<InstanceIdentifier<?>, DataObject> originalData) {

        final Set<InstanceIdentifier<?>> keys = updateData.keySet() != null
                ? updateData.keySet() : Collections.<InstanceIdentifier<?>>emptySet();
        for (InstanceIdentifier<?> key : keys) {
            if (clazz.equals(key.getTargetType())) {
                InstanceIdentifier<T> updateKeyIdent = key.firstIdentifierOf(clazz);
                final Optional<DataObject> value = Optional.of(updateData.get(key));
                final Optional<DataObject> original = Optional.of(originalData.get(key));
                if (value.isPresent() && original.isPresent()) {
                    this.update(updateKeyIdent, (T) original.get(), (T) value.get());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void removeData(final Set<InstanceIdentifier<?>> removeData,
                            final Map<InstanceIdentifier<?>, DataObject> originalData) {

        for (InstanceIdentifier<?> key : removeData) {
            if (clazz.equals(key.getTargetType())) {
                final InstanceIdentifier<T> ident = key.firstIdentifierOf(clazz);
                final DataObject removeValue = originalData.get(key);
                this.remove(ident, (T)removeValue);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("Interface Manager Closed");
    }

    protected abstract void remove(InstanceIdentifier<T> identifier, T del);

    protected abstract void update(InstanceIdentifier<T> identifier, T original, T update);

    protected abstract void add(InstanceIdentifier<T> identifier, T add);

    protected abstract InstanceIdentifier<T> getWildCardPath();

    protected abstract DataChangeListener getDataChangeListener();

    protected abstract AsyncDataBroker.DataChangeScope getDataChangeScope();

    public class DataChangeHandler implements Runnable {
        final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changeEvent;

        public DataChangeHandler(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changeEvent) {
            this.changeEvent = changeEvent;
        }

        @Override
        public void run() {
            Preconditions.checkNotNull(changeEvent,"Async ChangeEvent can not be null!");

            /* All DataObjects for create */
            final Map<InstanceIdentifier<?>, DataObject> createdData = changeEvent.getCreatedData() != null
                    ? changeEvent.getCreatedData() : Collections.<InstanceIdentifier<?>, DataObject>emptyMap();
            /* All DataObjects for remove */
            final Set<InstanceIdentifier<?>> removeData = changeEvent.getRemovedPaths() != null
                    ? changeEvent.getRemovedPaths() : Collections.<InstanceIdentifier<?>>emptySet();
            /* All DataObjects for updates */
            final Map<InstanceIdentifier<?>, DataObject> updateData = changeEvent.getUpdatedData() != null
                    ? changeEvent.getUpdatedData() : Collections.<InstanceIdentifier<?>, DataObject>emptyMap();
            /* All Original DataObjects */
            final Map<InstanceIdentifier<?>, DataObject> originalData = changeEvent.getOriginalData() != null
                    ? changeEvent.getOriginalData() : Collections.<InstanceIdentifier<?>, DataObject>emptyMap();

            createData(createdData);
            updateData(updateData, originalData);
            removeData(removeData, originalData);
        }
    }
}
