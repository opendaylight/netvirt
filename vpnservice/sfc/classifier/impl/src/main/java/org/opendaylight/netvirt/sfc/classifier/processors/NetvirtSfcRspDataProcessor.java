/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.processors;

import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * It may be that an ACL is configured with an RSP name before the RSP is actually created,
 * in which case the ACL creation needs to be buffered until the referenced RSP is created.
 * When the referenced RSP is created, need to unbuffer the ACL and proceed with the rest
 * of the creation.
 */
public class NetvirtSfcRspDataProcessor extends NetvirtSfcDataProcessorBase<RenderedServicePath> {

    // TODO upon init, read the RSPs from the data store and act on them
    //      as if they were just created. This is for ODL restarts
    public NetvirtSfcRspDataProcessor() {
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public void remove(InstanceIdentifier<RenderedServicePath> identifier, RenderedServicePath del) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void update(InstanceIdentifier<RenderedServicePath> identifier, RenderedServicePath original,
            RenderedServicePath update) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void add(InstanceIdentifier<RenderedServicePath> identifier, RenderedServicePath add) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

}
