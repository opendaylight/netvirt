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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;

import com.google.common.util.concurrent.ListenableFuture;

public class ElanInterfaceAddWorker implements Callable<List<ListenableFuture<Void>>> {
    private String key;
    private ElanInterface elanInterface;
    private ElanInstance elanInstance;
    private InterfaceInfo interfaceInfo;
    private ElanInterfaceManager dataChangeListener;

    public ElanInterfaceAddWorker(String key, ElanInterface elanInterface, InterfaceInfo interfaceInfo,
            ElanInstance elanInstance, ElanInterfaceManager dataChangeListener) {
        super();
        this.key = key;
        this.elanInterface = elanInterface;
        this.interfaceInfo = interfaceInfo;
        this.elanInstance = elanInstance;
        this.dataChangeListener = dataChangeListener;
    }

    @Override
    public String toString() {
        return "ElanInterfaceAddWorker [key=" + key + ", elanInterface=" + elanInterface + ", elanInstance="
                + elanInstance + ", interfaceInfo=" + interfaceInfo + "]";
    }


    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        dataChangeListener.addElanInterface(elanInterface, interfaceInfo, elanInstance);
        return futures;
    }
    
    

}
