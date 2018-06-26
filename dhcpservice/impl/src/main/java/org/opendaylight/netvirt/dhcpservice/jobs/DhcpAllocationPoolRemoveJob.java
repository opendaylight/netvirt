/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice.jobs;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.netvirt.dhcpservice.DhcpServiceUtils;

public class DhcpAllocationPoolRemoveJob implements Callable<List<ListenableFuture<Void>>> {

    private final ManagedNewTransactionRunner txRunner;
    private final String interfaceName;

    public DhcpAllocationPoolRemoveJob(ManagedNewTransactionRunner txRunner, String interfaceName) {
        this.txRunner = txRunner;
        this.interfaceName = interfaceName;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        return unInstallDhcpEntries();
    }

    private List<ListenableFuture<Void>> unInstallDhcpEntries() {
        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
            tx -> DhcpServiceUtils.unbindDhcpService(interfaceName, tx)));
    }
}
