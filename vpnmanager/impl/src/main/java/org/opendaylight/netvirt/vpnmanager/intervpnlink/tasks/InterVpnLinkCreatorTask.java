/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;
import static org.opendaylight.mdsal.binding.api.WriteTransaction.CREATE_MISSING_PARENTS;

public class InterVpnLinkCreatorTask implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkCreatorTask.class);

    private final ManagedNewTransactionRunner txRunner;
    private final InterVpnLink interVpnLinkToPersist;

    public InterVpnLinkCreatorTask(DataBroker dataBroker, InterVpnLink interVpnLink) {
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.interVpnLinkToPersist = interVpnLink;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        LOG.debug(
            "Persisting InterVpnLink {} with 1stEndpoint=[ vpn={}, ipAddr={} ] and 2ndEndpoint=[ vpn={}, ipAddr={} ]",
            interVpnLinkToPersist.getName(), interVpnLinkToPersist.getFirstEndpoint().getVpnUuid(),
            interVpnLinkToPersist.getFirstEndpoint().getIpAddress(),
            interVpnLinkToPersist.getSecondEndpoint().getVpnUuid(),
            interVpnLinkToPersist.getSecondEndpoint().getIpAddress());

        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx ->
            tx.merge(InterVpnLinkUtil.getInterVpnLinkPath(interVpnLinkToPersist.getName()),
                    interVpnLinkToPersist, CREATE_MISSING_PARENTS)));
    }

}
