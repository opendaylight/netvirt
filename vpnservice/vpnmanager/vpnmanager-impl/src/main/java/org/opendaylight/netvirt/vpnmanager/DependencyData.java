/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DependencyData {


    final InstanceIdentifier<?> iid;
    final InstanceIdentifier<?> wildCardPath;
    final boolean expectData;
    final LogicalDatastoreType dsType;

    public DependencyData(InstanceIdentifier<?> iid, boolean expectData, LogicalDatastoreType dsType,
                          InstanceIdentifier<?> wildCardPath) {
        this.iid = iid;
        this.expectData = expectData;
        this.dsType = dsType;
        this.wildCardPath = wildCardPath;
    }

    public InstanceIdentifier<?> getWildCardPath() {
        return wildCardPath;
    }

    InstanceIdentifier<?> getIid() {
        return iid;
    }

    LogicalDatastoreType getDsType() {
        return dsType;
    }

    boolean isExpectData() {
        return expectData;
    }

    @Override
    public String toString() {
        return "Dependent data: IID:" + iid + ", wildCardPath: " + wildCardPath + ", expecteData: " + expectData
                + "dataStoreType: " + dsType;
    }
}
