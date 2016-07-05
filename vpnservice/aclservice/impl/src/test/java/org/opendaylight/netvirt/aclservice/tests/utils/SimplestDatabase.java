package org.opendaylight.netvirt.aclservice.tests.utils;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Optional;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

class SimplestDatabase {

    private Map<LogicalDatastoreType, Map<InstanceIdentifier<?>, DataObject>> map;

    protected Map<LogicalDatastoreType, Map<InstanceIdentifier<?>, DataObject>> getMap() {
        if (map == null) {
            map = new EnumMap<>(LogicalDatastoreType.class);
            map.put(OPERATIONAL, new HashMap<>());
            map.put(CONFIGURATION, new HashMap<>());
        }
        return map;
    }

    void mergeInto(SimplestDatabase parent) {
        map.get(OPERATIONAL).forEach((t, u) -> parent.map.get(OPERATIONAL).put(t, u));
        map.get(CONFIGURATION).forEach((t, u) -> parent.map.get(CONFIGURATION).put(t, u));
        map = null;
    }

    @SuppressWarnings("unchecked")
    public <T extends DataObject> Optional<T> get(LogicalDatastoreType store, InstanceIdentifier<T> path) {
        return (Optional<T>) Optional.fromNullable(getMap().get(store).get(path));
    }
}
