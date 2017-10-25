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
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;

public class DhcpAllocationPoolAddJob implements Callable<List<ListenableFuture<Void>>> {

    private final DataBroker dataBroker;
    private final String interfaceName;

    public DhcpAllocationPoolAddJob(DataBroker dataBroker, String interfaceName) {
        this.dataBroker = dataBroker;
        this.interfaceName = interfaceName;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        return installDhcpEntries();
    }

    private List<ListenableFuture<Void>> installDhcpEntries() {
        WriteTransaction bindServiceTx = dataBroker.newWriteOnlyTransaction();
        DhcpServiceUtils.bindDhcpService(interfaceName, NwConstants.DHCP_TABLE, bindServiceTx);
        return Collections.singletonList(bindServiceTx.submit());
    }

}