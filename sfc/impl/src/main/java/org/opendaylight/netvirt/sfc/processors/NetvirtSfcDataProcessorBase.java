/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 * @param <D>
 *
 */
public abstract class NetvirtSfcDataProcessorBase<D extends DataObject> {

    /**
     * Method removes DataObject which is identified by InstanceIdentifier.
     *
     * @param identifier - the whole path to DataObject
     * @param del - DataObject for removing
     */
    public abstract void remove(InstanceIdentifier<D> identifier, D del);

    /**
     * Method updates the original DataObject to the update DataObject.
     * Both are identified by same InstanceIdentifier.
     *
     * @param identifier - the whole path to DataObject
     * @param original - original DataObject (for update)
     * @param update - changed DataObject (contain updates)
     */
    public abstract void update(InstanceIdentifier<D> identifier, D original, D update);

    /**
     * Method adds the DataObject which is identified by InstanceIdentifier
     * to device.
     *
     * @param identifier - the whole path to new DataObject
     * @param add - new DataObject
     */
    public abstract void add(InstanceIdentifier<D> identifier, D add);

    // TODO put common DataProcessor methods, etc here. If there wont be any common
    //      state, then this abstract class should be changed to an interface
}
