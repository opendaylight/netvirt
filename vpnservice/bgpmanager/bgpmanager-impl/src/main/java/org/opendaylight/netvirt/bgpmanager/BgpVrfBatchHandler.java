/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class BgpVrfBatchHandler implements ResourceHandler {

    public void update(WriteTransaction tx, LogicalDatastoreType datastoreType,
                       final InstanceIdentifier identifier, final Object original, final Object update) {
        if ((update != null) && !(update instanceof DataObject)) {
            return;
        }
        if (datastoreType != getDatastoreType()) {
            return;
        }
        tx.merge(datastoreType, identifier, (DataObject) update, true);
    }

    public void create(WriteTransaction tx, final LogicalDatastoreType datastoreType,
                       final InstanceIdentifier identifier, final Object data) {
        if ((data != null) && !(data instanceof DataObject)) {
            return;
        }
        if (datastoreType != getDatastoreType()) {
            return;
        }
        tx.put(datastoreType, identifier, (DataObject) data, true);
    }

    public void delete(WriteTransaction tx, final LogicalDatastoreType datastoreType,
                       final InstanceIdentifier identifier, final Object data) {
        if ((data != null) && !(data instanceof DataObject)) {
            return;
        }
        if (datastoreType != getDatastoreType()) {
            return;
        }
        tx.delete(datastoreType, identifier);
    }

    public DataBroker getResourceBroker() {
        return BgpUtil.getBroker();
    }

    public int getBatchSize() {
        return BgpUtil.batchSize;
    }

    public int getBatchInterval() {
        return BgpUtil.batchInterval;
    }

    public LogicalDatastoreType getDatastoreType() {
        return LogicalDatastoreType.CONFIGURATION;
    }
}

