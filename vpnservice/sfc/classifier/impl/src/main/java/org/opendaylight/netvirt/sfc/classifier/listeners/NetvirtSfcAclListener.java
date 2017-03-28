/*
 * Copyright (c) 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.sfc.classifier.service.ClassifierService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for AccessList.
 */
@Singleton
public class NetvirtSfcAclListener
    extends AsyncDataTreeChangeListenerBase<Acl, NetvirtSfcAclListener>
    implements AutoCloseable {

    private DataBroker dataBroker;
    private ClassifierService classifierService;

    @Inject
    public NetvirtSfcAclListener(final DataBroker dataBroker, final ClassifierService classifierService) {
        super(Acl.class, NetvirtSfcAclListener.class);
        this.dataBroker = dataBroker;
        this.classifierService = classifierService;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Acl> getWildCardPath() {
        return InstanceIdentifier
            .create(AccessLists.class)
            .child(Acl.class);
    }

    @Override
    protected NetvirtSfcAclListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<Acl> key, Acl acl) {
        classifierService.updateAll();
    }

    @Override
    protected void remove(InstanceIdentifier<Acl> key, Acl acl) {
        classifierService.updateAll();
    }

    @Override
    protected void update(InstanceIdentifier<Acl> key, Acl aclBefore, Acl aclAfter) {
        classifierService.updateAll();
    }
}
