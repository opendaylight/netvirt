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
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.dhcpservice.DhcpAllocationPoolManager;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpAllocationPoolAddJob implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpAllocationPoolAddJob.class);
    private final DhcpAllocationPoolManager dhcpAllocationPoolManager;
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

    public DhcpAllocationPoolAddJob(DhcpAllocationPoolManager dhcpAllocationPoolManager,
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
        installDhcpEntries(interfaceName, dpnId, futures);
        return futures;
    }

    private void installDhcpEntries(String interfaceName, BigInteger dpId, List<ListenableFuture<Void>> futures) {
        WriteTransaction bindServiceTx = dataBroker.newWriteOnlyTransaction();
        DhcpServiceUtils.bindDhcpService(interfaceName, NwConstants.DHCP_TABLE, bindServiceTx);
        futures.add(bindServiceTx.submit());
    }

}