/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by emhamla on 8/31/2015.
 */
public class BgpUtil {

    private static final Logger LOG = LoggerFactory.getLogger(BgpUtil.class);
    private static DataBroker  dataBroker;

    static <T extends DataObject> void update(DataBroker broker, LogicalDatastoreType datastoreType,
                                              InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        tx.submit();
    }

    public static <T extends DataObject> void write(DataBroker broker, LogicalDatastoreType datastoreType,
                                              InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        tx.submit();
    }

    static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        tx.submit();
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public static void setBroker(DataBroker broker) {
        BgpUtil.dataBroker = broker;
    }

    public static DataBroker getBroker() {
        return dataBroker;
    }

}
