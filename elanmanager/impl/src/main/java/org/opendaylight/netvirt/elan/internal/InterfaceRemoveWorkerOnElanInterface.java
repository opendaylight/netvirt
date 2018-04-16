/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;

public class InterfaceRemoveWorkerOnElanInterface implements Callable<List<ListenableFuture<Void>>> {

    private final String interfaceName;
    private final ElanInstance elanInfo;
    private final InterfaceInfo interfaceInfo;
    private final ElanInterfaceManager dataChangeListener;
    private final boolean isLastElanInterface;

    public InterfaceRemoveWorkerOnElanInterface(String interfaceName, ElanInstance elanInfo,
            InterfaceInfo interfaceInfo, ElanInterfaceManager dataChangeListener, boolean isLastElanInterface) {
        this.interfaceName = interfaceName;
        this.elanInfo = elanInfo;
        this.interfaceInfo = interfaceInfo;
        this.dataChangeListener = dataChangeListener;
        this.isLastElanInterface = isLastElanInterface;
    }

    @Override
    public String toString() {
        return "InterfaceRemoveWorkerOnElanInterface [key=" + interfaceName + ", elanInfo=" + elanInfo
                + ", interfaceInfo=" + interfaceInfo + ", isLastElanInterface=" + isLastElanInterface + "]";
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<ListenableFuture<Void>> call() {
        return dataChangeListener.removeEntriesForElanInterface(elanInfo, interfaceInfo, interfaceName,
                isLastElanInterface);
    }

}
