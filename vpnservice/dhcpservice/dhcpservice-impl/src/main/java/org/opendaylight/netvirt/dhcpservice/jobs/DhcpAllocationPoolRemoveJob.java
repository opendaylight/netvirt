/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.jobs;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
 import org.opendaylight.netvirt.dhcpservice.DhcpAllocationPoolManager;
import org.opendaylight.netvirt.dhcpservice.DhcpExternalTunnelManager;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpAllocationPoolRemoveJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpAllocationPoolRemoveJob.class);
    private final DhcpAllocationPoolManager dhcpAllocationPoolManager;
    DhcpExternalTunnelManager dhcpExternalTunnelManager;
    DataBroker dataBroker;
    String interfaceName;
    BigInteger dpnId;

    private static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            LOG.debug("Success in Datastore write operation");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore write operation", error);
        }
    };

    public DhcpAllocationPoolRemoveJob(DhcpAllocationPoolManager dhcpAllocationPoolManager,
            DataBroker dataBroker, String interfaceName, BigInteger dpnId) {
        super();
        this.dhcpAllocationPoolManager = dhcpAllocationPoolManager;
        this.dataBroker = dataBroker;
        this.interfaceName = interfaceName;
        this.dpnId = dpnId;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        unInstallDhcpEntries(interfaceName, dpnId, futures);
        return futures;
    }

    private void unInstallDhcpEntries(String interfaceName, BigInteger dpId, List<ListenableFuture<Void>> futures) {
        WriteTransaction unbindServiceTx = dataBroker.newWriteOnlyTransaction();
        DhcpServiceUtils.unbindDhcpService(interfaceName, unbindServiceTx);
        futures.add(unbindServiceTx.submit());
    }

}