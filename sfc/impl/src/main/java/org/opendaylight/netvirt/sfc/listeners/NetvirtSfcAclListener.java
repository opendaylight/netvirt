/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcDataProcessorBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for AccessList.
 */
public class NetvirtSfcAclListener extends AsyncDataTreeChangeListenerBase<Acl, NetvirtSfcAclListener>
        implements AutoCloseable {

    private final DataBroker dataBroker;
    private final NetvirtSfcDataProcessorBase<Acl> dataProcessor;

    @Inject
    public NetvirtSfcAclListener(DataBroker dataBroker, NetvirtSfcDataProcessorBase<Acl> dataProcessor) {
        super(Acl.class, NetvirtSfcAclListener.class);

        this.dataBroker = dataBroker;
        this.dataProcessor = dataProcessor;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected NetvirtSfcAclListener getDataTreeChangeListener() {
        return null;
    }

    @Override
    protected InstanceIdentifier<Acl> getWildCardPath() {
        return null;
    }

    @Override
    protected void add(InstanceIdentifier<Acl> key, Acl acl) {
        dataProcessor.add(key, acl);
    }

    @Override
    protected void remove(InstanceIdentifier<Acl> key, Acl acl) {
        dataProcessor.remove(key, acl);
    }

    @Override
    protected void update(InstanceIdentifier<Acl> key, Acl aclBefore, Acl aclAfter) {
        dataProcessor.update(key, aclBefore, aclAfter);
    }
}
