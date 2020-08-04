/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks;

import static org.opendaylight.mdsal.binding.util.Datastore.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterVpnLinkRemoverTask implements Callable<List<? extends ListenableFuture<?>>> {
    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkRemoverTask.class);

    private final InstanceIdentifier<InterVpnLink> interVpnLinkIid;
    private final String interVpnLinkName;
    private final ManagedNewTransactionRunner txRunner;

    public InterVpnLinkRemoverTask(DataBroker dataBroker, InstanceIdentifier<InterVpnLink> interVpnLinkPath) {
        this.interVpnLinkIid = interVpnLinkPath;
        this.interVpnLinkName = interVpnLinkPath.firstKeyOf(InterVpnLink.class).getName();
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @Override
    public List<? extends ListenableFuture<?>> call() {
        LOG.debug("Removing InterVpnLink {} from storage", interVpnLinkName);
        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx ->
            tx.delete(this.interVpnLinkIid)));
    }
}
