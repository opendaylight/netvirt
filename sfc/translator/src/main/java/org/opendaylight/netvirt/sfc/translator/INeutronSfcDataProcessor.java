/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator;

import org.opendaylight.yangtools.yang.binding.DataObject;

/**
 * Neutron SFC yang model processor.
 */
public interface INeutronSfcDataProcessor<D extends DataObject> {

    /**
     * Method removes DataObject which is identified by InstanceIdentifier.
     *
     * @param del - DataObject for removing
     */
    void remove(D del);

    /**
     * Method updates the original DataObject to the update DataObject.
     * Both are identified by same InstanceIdentifier.
     *
     * @param update - changed DataObject (contain updates)*/
    void update(D orig, D update);

    /**
     * Method adds the DataObject which is identified by InstanceIdentifier
     * to device.
     *
     * @param add - new DataObject
     */
    void add(D add);

}
