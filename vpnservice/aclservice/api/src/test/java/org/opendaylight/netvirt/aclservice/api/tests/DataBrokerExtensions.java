/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import org.eclipse.xtext.xbase.lib.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DataBrokerExtensions {

    public static <T extends DataObject> void put(DataBroker dataBroker, Pair<InstanceIdentifier<T>, T> pair) {
        MDSALUtil.syncWrite(dataBroker, OPERATIONAL, pair.getKey(), pair.getValue());
    }

}
