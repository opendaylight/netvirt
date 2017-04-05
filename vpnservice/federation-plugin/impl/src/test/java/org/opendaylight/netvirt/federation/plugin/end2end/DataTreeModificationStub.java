/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.end2end;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.Augmentation;
import org.opendaylight.yangtools.yang.binding.ChildOf;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;

public class DataTreeModificationStub<T extends DataObject> implements DataTreeModification<T> {

    private InstanceIdentifier<T> instanceIdentifier;
    private LogicalDatastoreType datastoreType;
    private T dataObject;

    public DataTreeModificationStub(InstanceIdentifier<T> instanceIdentifier, LogicalDatastoreType datastoreType,
            T dataObject) {
        this.instanceIdentifier = instanceIdentifier;
        this.datastoreType = datastoreType;
        this.dataObject = dataObject;
    }

    @Override
    public DataTreeIdentifier<T> getRootPath() {
        return new DataTreeIdentifier<>(datastoreType, instanceIdentifier);
    }

    @Override
    public DataObjectModification<T> getRootNode() {
        return new DataObjectModificationStub<>();
    }

    @SuppressWarnings("hiding")
    private class DataObjectModificationStub<T extends DataObject> implements DataObjectModification<T> {

        @Override
        public PathArgument getIdentifier() {
            return null;
        }

        @Override
        public Class<T> getDataType() {
            return null;
        }

        @Override
        public ModificationType getModificationType() {
            return ModificationType.WRITE;
        }

        @Override
        public T getDataBefore() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T getDataAfter() {
            return (T) dataObject;
        }

        @Override
        public Collection<DataObjectModification<? extends DataObject>> getModifiedChildren() {
            return null;
        }

        @Override
        public <C extends ChildOf<? super T>> DataObjectModification<C> getModifiedChildContainer(Class<C> child) {
            return null;
        }

        @Override
        public <C extends Augmentation<T> & DataObject> DataObjectModification<C> getModifiedAugmentation(
                Class<C> augmentation) {
            return null;
        }

        @Override
        public <C extends Identifiable<K> & ChildOf<? super T>,
                K extends Identifier<C>> DataObjectModification<C> getModifiedChildListItem(Class<C> listItem,
                        K listKey) {
            return null;
        }

        @Override
        public DataObjectModification<? extends DataObject> getModifiedChild(PathArgument childArgument) {
            return null;
        }

    }
}
