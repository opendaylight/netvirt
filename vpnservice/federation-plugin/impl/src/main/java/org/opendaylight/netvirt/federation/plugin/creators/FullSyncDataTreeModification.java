/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.creators;

import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class FullSyncDataTreeModification<T extends DataObject> implements DataTreeModification<T> {

    private DataObjectModification<T> dataObjectModification;

    public FullSyncDataTreeModification(T dataObject) {
        dataObjectModification = new FullSyncDataObjectModification<>(dataObject);
    }

    @Override
    public DataTreeIdentifier<T> getRootPath() {
        // no need to access since all the info can be taken from the listener key
        return null;
    }

    @Override
    public DataObjectModification<T> getRootNode() {
        return dataObjectModification;
    }

}
