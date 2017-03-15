/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import com.google.common.util.concurrent.CheckedFuture;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SfcProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SfcProvider.class);
    private final DataBroker dataBroker;

    @Inject
    public SfcProvider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public Optional<RenderedServicePath> getRenderedServicePath(String rspName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        InstanceIdentifier<RenderedServicePath> rspIid = InstanceIdentifier
                .builder(RenderedServicePaths.class).child(RenderedServicePath.class).build();

        ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction();
        CheckedFuture<com.google.common.base.Optional<RenderedServicePath>, ReadFailedException> submitFuture =
                readTx.read(LogicalDatastoreType.OPERATIONAL, rspIid);

        try {
            com.google.common.base.Optional<RenderedServicePath> optionalRet = submitFuture.checkedGet();
            if (optionalRet.isPresent()) {
                rsp = Optional.of(optionalRet.get());
            }
        } catch (ReadFailedException e) {
            LOG.warn("Error reading RSP: {}", e.getMessage());
        }

        return rsp;
    }

    public Optional<RenderedServicePath> getRenderedServicePathFromSfc(String sfcName) {
        Optional<RenderedServicePath> rsp = Optional.empty();

        return rsp;
    }
}
