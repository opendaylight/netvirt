/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.jobs;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;

public class DhcpAllocationPoolRemoveJob implements Callable<List<ListenableFuture<Void>>> {

    private final DataBroker dataBroker;
    private final String interfaceName;

    public DhcpAllocationPoolRemoveJob(DataBroker dataBroker, String interfaceName) {
        this.dataBroker = dataBroker;
        this.interfaceName = interfaceName;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        return unInstallDhcpEntries();
    }

    private List<ListenableFuture<Void>> unInstallDhcpEntries() {
        WriteTransaction unbindServiceTx = dataBroker.newWriteOnlyTransaction();
        DhcpServiceUtils.unbindDhcpService(interfaceName, unbindServiceTx);
        return Collections.singletonList(unbindServiceTx.submit());
    }
}
