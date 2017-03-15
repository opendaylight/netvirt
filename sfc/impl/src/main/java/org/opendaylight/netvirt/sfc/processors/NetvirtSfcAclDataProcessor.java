/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.processors;

import org.opendaylight.netvirt.sfc.providers.NetvirtSfcClassifierGeniusProvider;
import org.opendaylight.netvirt.sfc.providers.NetvirtSfcClassifierOpenFlow13Provider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 */
public class NetvirtSfcAclDataProcessor extends NetvirtSfcClassifierDataProcessorBase<Acl> {

    private NetvirtSfcClassifierOpenFlow13Provider openFlow13Provider;
    private NetvirtSfcClassifierGeniusProvider geniusProvider;

    public NetvirtSfcClassifierOpenFlow13Provider getOpenFlow13Provider() {
        return openFlow13Provider;
    }

    public void setOpenFlow13Provider(NetvirtSfcClassifierOpenFlow13Provider openFlow13Provider) {
        this.openFlow13Provider = openFlow13Provider;
    }

    public NetvirtSfcClassifierGeniusProvider getGeniusProvider() {
        return geniusProvider;
    }

    public void setGeniusProvider(NetvirtSfcClassifierGeniusProvider geniusProvider) {
        this.geniusProvider = geniusProvider;
    }

    @Override
    public void remove(InstanceIdentifier<Acl> identifier, Acl del) {
        // TODO Auto-generated method stub

    }

    @Override
    public void update(InstanceIdentifier<Acl> identifier, Acl original, Acl update) {
        // TODO Auto-generated method stub

    }

    @Override
    public void add(InstanceIdentifier<Acl> identifier, Acl add) {
        // TODO Auto-generated method stub

    }
}
