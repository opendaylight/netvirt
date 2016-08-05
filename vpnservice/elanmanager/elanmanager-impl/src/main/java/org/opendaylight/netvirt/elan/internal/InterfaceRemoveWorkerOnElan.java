/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class InterfaceRemoveWorkerOnElan implements Callable<List<ListenableFuture<Void>>> {
    private String key;
    private ElanInstance elanInfo;
    private String interfaceName;
    private InterfaceInfo interfaceInfo;
    private boolean isInterfaceStateRemoved;
    private ElanInterfaceManager dataChangeListener;
    private static final Logger logger = LoggerFactory.getLogger(InterfaceRemoveWorkerOnElan.class);

    public InterfaceRemoveWorkerOnElan(String key, ElanInstance elanInfo, String interfaceName,
                                       InterfaceInfo interfaceInfo, boolean isInterfaceStateRemoved, ElanInterfaceManager dataChangeListener) {
        super();
        this.key = key;
        this.elanInfo = elanInfo;
        this.interfaceName = interfaceName;
        this.interfaceInfo = interfaceInfo;
        this.isInterfaceStateRemoved = isInterfaceStateRemoved;
        this.dataChangeListener = dataChangeListener;
    }

    @Override
    public String toString() {
        return "InterfaceRemoveWorkerOnElan [key=" + key + ", elanInfo=" + elanInfo +
            ", interfaceName=" + interfaceName
            + ", interfaceInfo=" + interfaceInfo + "]";
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        try {
            dataChangeListener.removeElanInterface(futures, elanInfo, interfaceName, interfaceInfo, isInterfaceStateRemoved);
        } catch (Exception e) {
            logger.error("Error while processing {} for {}, error {}", key, interfaceName, e);
        }
        return futures;
    }

}
