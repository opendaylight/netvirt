/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import java.util.Optional;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcGeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcOpenFlow13Provider;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 * It may be that an ACL is configured with an RSP name before the RSP is actually created,
 * in which case the ACL creation needs to be buffered until the referenced RSP is created.
 * When the referenced RSP is created, need to unbuffer the ACL and proceed with the rest
 * of the creation.
 */
public class NetvirtSfcRspDataProcessor extends NetvirtSfcDataProcessorBase<RenderedServicePath> {

    private NetvirtSfcOpenFlow13Provider openFlow13Provider;
    private NetvirtSfcGeniusProvider geniusProvider;

    // TODO upon init, read the RSPs from the data store and act on them
    //      as if they were just created. This is for ODL restarts

    public NetvirtSfcRspDataProcessor() {
        openFlow13Provider = null;
        geniusProvider = null;
    }

    public NetvirtSfcOpenFlow13Provider getOpenFlow13Provider() {
        return openFlow13Provider;
    }

    public void setOpenFlow13Provider(NetvirtSfcOpenFlow13Provider openFlow13Provider) {
        this.openFlow13Provider = openFlow13Provider;
    }

    public Optional<NetvirtSfcGeniusProvider> getGeniusProvider() {
        return Optional.ofNullable(geniusProvider);
    }

    public void setGeniusProvider(NetvirtSfcGeniusProvider geniusProvider) {
        this.geniusProvider = geniusProvider;
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
