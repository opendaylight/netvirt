package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DependencyData {


    final InstanceIdentifier<?> iid;
    final InstanceIdentifier<?> wildCardPath;
    final boolean expectData;
    final LogicalDatastoreType dsType;

    public InstanceIdentifier<?> getWildCardPath() {
        return wildCardPath;
    }

    public DependencyData(InstanceIdentifier<?> iid, boolean expectData, LogicalDatastoreType dsType,
                          InstanceIdentifier<?> wildCardPath) {
        this.iid = iid;
        this.expectData = expectData;
        this.dsType = dsType;
        this.wildCardPath = wildCardPath;
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
}
