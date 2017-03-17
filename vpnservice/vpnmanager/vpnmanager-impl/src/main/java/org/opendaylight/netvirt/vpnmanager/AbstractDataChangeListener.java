/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AbstractDataChangeListener implemented basic {@link DataChangeListener} processing for
 * VPN related Data Objects.
 */
public abstract class AbstractDataChangeListener<T extends DataObject> implements DataChangeListener {

    protected final Class<T> clazz;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataChangeListener.class);

    /**
     * Creates an instance.
     *
     * @param clazz The class for which the data change event is received.
     */
    public AbstractDataChangeListener(Class<T> clazz) {
        this.clazz = Preconditions.checkNotNull(clazz, "Class can not be null!");
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changeEvent) {
        try {
            Preconditions.checkNotNull(changeEvent, "Async ChangeEvent can not be null!");

            /* All DataObjects for create */
            final Map<InstanceIdentifier<?>, DataObject> createdData = changeEvent.getCreatedData() != null
                    ? changeEvent.getCreatedData() : Collections.emptyMap();
            /* All DataObjects for remove */
            final Set<InstanceIdentifier<?>> removeData = changeEvent.getRemovedPaths() != null
                    ? changeEvent.getRemovedPaths() : Collections.emptySet();
            /* All DataObjects for updates */
            final Map<InstanceIdentifier<?>, DataObject> updateData = changeEvent.getUpdatedData() != null
                    ? changeEvent.getUpdatedData() : Collections.emptyMap();
            /* All Original DataObjects */
            final Map<InstanceIdentifier<?>, DataObject> originalData = changeEvent.getOriginalData() != null
                    ? changeEvent.getOriginalData() : Collections.emptyMap();

            this.createData(createdData);
            this.updateData(updateData, originalData);
            this.removeData(removeData, originalData);
        } catch (Throwable e) {
            LOG.error("failed to handle dcn ", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void createData(final Map<InstanceIdentifier<?>, DataObject> createdData) {
        for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : createdData.entrySet()) {
            InstanceIdentifier<?> key = entry.getKey();
            if (clazz.equals(key.getTargetType())) {
                DataObject value = entry.getValue();
                if (value != null) {
                    this.add(key.firstIdentifierOf(clazz), (T) value);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateData(final Map<InstanceIdentifier<?>, DataObject> updateData,
            final Map<InstanceIdentifier<?>, DataObject> originalData) {
        for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : updateData.entrySet()) {
            InstanceIdentifier<?> key = entry.getKey();
            if (clazz.equals(key.getTargetType())) {
                InstanceIdentifier<T> updateKeyIdent = key.firstIdentifierOf(clazz);
                final DataObject value = entry.getValue();
                final DataObject original = originalData.get(key);
                if (value != null && original != null) {
                    this.update(updateKeyIdent, (T) original, (T) value);
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
                this.remove(ident, (T) removeValue);
            }
        }
    }

    protected abstract void remove(InstanceIdentifier<T> identifier, T del);

    protected abstract void update(InstanceIdentifier<T> identifier, T original, T update);

    protected abstract void add(InstanceIdentifier<T> identifier, T add);

}


