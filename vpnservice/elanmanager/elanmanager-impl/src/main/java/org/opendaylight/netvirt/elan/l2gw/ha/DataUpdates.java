/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.yangtools.yang.binding.Identifiable;

public class DataUpdates {

    private Map<Class<? extends Identifiable>, List<Identifiable>> updatedData = new ConcurrentHashMap<>();
    private Map<Class<? extends Identifiable>, List<Identifiable>> deletedData = new ConcurrentHashMap<>();

    public DataUpdates(Map<Class<? extends Identifiable>, List<Identifiable>> updatedData,
                       Map<Class<? extends Identifiable>, List<Identifiable>> deletedData) {
        this.updatedData = updatedData;
        this.deletedData = deletedData;
    }

    public Map<Class<? extends Identifiable>, List<Identifiable>> getUpdatedData() {
        return updatedData;
    }

    public Map<Class<? extends Identifiable>, List<Identifiable>> getDeletedData() {
        return deletedData;
    }

}
