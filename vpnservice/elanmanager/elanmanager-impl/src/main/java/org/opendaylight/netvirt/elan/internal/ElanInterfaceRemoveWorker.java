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

import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;

import com.google.common.util.concurrent.ListenableFuture;

public class ElanInterfaceRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
    private String key;
    private ElanInstance elanInfo;
    private String interfaceName;
    private InterfaceInfo interfaceInfo;
    private ElanInterfaceManager dataChangeListener;

    public ElanInterfaceRemoveWorker(String key, ElanInstance elanInfo, String interfaceName,
            InterfaceInfo interfaceInfo, ElanInterfaceManager dataChangeListener) {
        super();
        this.key = key;
        this.elanInfo = elanInfo;
        this.interfaceName = interfaceName;
        this.interfaceInfo = interfaceInfo;
        this.dataChangeListener = dataChangeListener;
    }

    @Override
    public String toString() {
        return "ElanInterfaceRemoveWorker [key=" + key + ", elanInfo=" + elanInfo +
                ", interfaceName=" + interfaceName
                + ", interfaceInfo=" + interfaceInfo + "]";
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        dataChangeListener.removeElanInterface(elanInfo, interfaceName, interfaceInfo);
        return futures;
    }

}
