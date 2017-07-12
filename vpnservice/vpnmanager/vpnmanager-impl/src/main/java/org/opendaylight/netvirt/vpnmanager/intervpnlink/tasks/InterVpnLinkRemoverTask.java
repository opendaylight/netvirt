/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink.tasks;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterVpnLinkRemoverTask implements Callable<List<ListenableFuture<Void>>> {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkRemoverTask.class);

    private final InstanceIdentifier<InterVpnLink> interVpnLinkIid;
    private final String interVpnLinkName;
    private final DataBroker dataBroker;

    public InterVpnLinkRemoverTask(DataBroker dataBroker, InstanceIdentifier<InterVpnLink> interVpnLinkPath) {
        this.interVpnLinkIid = interVpnLinkPath;
        this.interVpnLinkName = interVpnLinkPath.firstKeyOf(InterVpnLink.class).getName();
        this.dataBroker = dataBroker;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        LOG.debug("Removing InterVpnLink {} from storage", interVpnLinkName);
        List<ListenableFuture<Void>> result = new ArrayList<>();
        WriteTransaction removeTx = dataBroker.newWriteOnlyTransaction();
        removeTx.delete(LogicalDatastoreType.CONFIGURATION, this.interVpnLinkIid);
        CheckedFuture<Void, TransactionCommitFailedException> removalFuture = removeTx.submit();
        result.add(removalFuture);
        return result;
    }

}
