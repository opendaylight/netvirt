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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 */
public class NetvirtSfcAclDataProcessor extends NetvirtSfcDataProcessorBase<Acl> {

    private NetvirtSfcOpenFlow13Provider openFlow13Provider;
    private NetvirtSfcGeniusProvider geniusProvider;

    // TODO upon init, read the ACLs from the data store and act on them
    //      as if they were just created. This is for ODL restarts

    public NetvirtSfcAclDataProcessor() {
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
    public void remove(InstanceIdentifier<Acl> identifier, Acl del) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void update(InstanceIdentifier<Acl> identifier, Acl original, Acl update) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }

    @Override
    public void add(InstanceIdentifier<Acl> identifier, Acl add) {
        // TODO make appropriate calls into the openFlow13Provider and geniusProvider
    }
}
